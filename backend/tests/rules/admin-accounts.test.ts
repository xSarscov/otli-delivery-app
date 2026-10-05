import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { serverTime, useCatalogEnv } from "./catalog-support";
import { seedCourier, seedReadyOrder } from "./dispatch-support";

const { as, admin } = useCatalogEnv();

/**
 * The status change the Admin screen writes: `users/{uid}` is the source of truth, and a merchant's
 * `merchants/{uid}` mirror follows in the same batch; a courier has no mirror (ADR-6).
 */
function setStatus(uid: string, status: string, mirrored: boolean, asUid = "admin-1") {
  const db = as(asUid);
  const batch = db.batch();
  batch.update(db.collection("users").doc(uid), { status });
  if (mirrored) batch.update(db.collection("merchants").doc(uid), { status, updatedAt: serverTime() });
  return batch.commit();
}

const status = async (collection: string, uid: string) => (await admin((db) => db.collection(collection).doc(uid).get())).data()?.status;
const newCategory = (uid: string) => as(uid).collection("merchants").doc(uid).collection("categories").doc("new").set({ name: "Bebidas", sortOrder: 2 });
const goOnline = (uid: string) => as(uid).collection("couriers").doc(uid).update({ isOnline: true, updatedAt: serverTime() });

describe("approving a merchant", () => {
  it("is blocked until the admin approves, then opens the catalog actions", async () => {
    await assertFails(newCategory("merchant-pending"));
    await assertSucceeds(setStatus("merchant-pending", "active", true));
    expect(await status("users", "merchant-pending")).toBe("active");
    expect(await status("merchants", "merchant-pending")).toBe("active");
    await assertSucceeds(newCategory("merchant-pending"));
  });

  it("writes the account and its storefront mirror together or not at all", async () => {
    const db = as("admin-1");
    const batch = db.batch();
    batch.update(db.collection("users").doc("merchant-pending"), { status: "active" });
    batch.update(db.collection("merchants").doc("merchant-pending"), { status: "banned", updatedAt: serverTime() });
    await assertFails(batch.commit());
    expect(await status("users", "merchant-pending")).toBe("pending");
    expect(await status("merchants", "merchant-pending")).toBe("pending");
  });

  it("cannot be done by the merchant itself or by another role", async () => {
    await assertFails(setStatus("merchant-pending", "active", true, "merchant-pending"));
    for (const uid of ["customer-1", "merchant-a", "courier-1"]) {
      await assertFails(setStatus("merchant-pending", "active", true, uid));
    }
    expect(await status("users", "merchant-pending")).toBe("pending");
    expect(await status("merchants", "merchant-pending")).toBe("pending");
  });

  it("cannot be done to either document alone by anyone but the admin", async () => {
    for (const uid of ["customer-1", "merchant-a", "courier-1", "merchant-pending"]) {
      await assertFails(as(uid).collection("users").doc("merchant-pending").update({ status: "active" }));
      await assertFails(as(uid).collection("merchants").doc("merchant-pending").update({ status: "active", updatedAt: serverTime() }));
      await assertFails(as(uid).collection("users").doc("courier-1").update({ status: "suspended" }));
    }
    expect(await status("users", "merchant-pending")).toBe("pending");
    expect(await status("merchants", "merchant-pending")).toBe("pending");
    expect(await status("users", "courier-1")).toBe("active");
  });

  it("cannot be done by an admin account that is not active", async () => {
    await admin((db) =>
      db.collection("users").doc("admin-suspended").set({ role: "admin", status: "suspended", displayName: "x", email: "x@otli.test", phone: "1", createdAt: new Date() }),
    );
    await assertFails(setStatus("merchant-pending", "active", true, "admin-suspended"));
  });
});

describe("approving a courier", () => {
  it("opens going online only once approved", async () => {
    await seedCourier(admin, "courier-new", {}, "pending");
    await assertFails(goOnline("courier-new"));
    await assertSucceeds(setStatus("courier-new", "active", false));
    await assertSucceeds(goOnline("courier-new"));
  });
});

describe("suspending", () => {
  it("takes the catalog actions away from a merchant at once", async () => {
    await assertSucceeds(newCategory("merchant-a"));
    await assertSucceeds(setStatus("merchant-a", "suspended", true));
    expect(await status("merchants", "merchant-a")).toBe("suspended");
    await assertFails(newCategory("merchant-a"));
    await assertFails(as("merchant-a").collection("merchants").doc("merchant-a").update({ isOpen: false, updatedAt: serverTime() }));
  });

  it("takes going online and claiming away from a courier at once", async () => {
    await seedCourier(admin, "courier-2", { isOnline: true });
    await seedReadyOrder(admin, "o1");
    await assertSucceeds(setStatus("courier-2", "suspended", false));
    await assertFails(goOnline("courier-2"));
    const db = as("courier-2");
    const batch = db.batch();
    batch.update(db.collection("orders").doc("o1"), { status: "claimed", courierId: "courier-2", claimedAt: serverTime(), updatedAt: serverTime() });
    batch.update(db.collection("couriers").doc("courier-2"), { activeOrderId: "o1", updatedAt: serverTime() });
    await assertFails(batch.commit());
  });

  it("lets the admin reactivate a suspended account, which regains its actions", async () => {
    await assertSucceeds(setStatus("merchant-suspended", "active", true));
    await assertSucceeds(newCategory("merchant-suspended"));
  });
});

describe("reading the accounts to manage", () => {
  it("lets the admin list every merchant and courier account but nobody else", async () => {
    const roles = ["merchant", "courier"];
    const listing = (uid: string) => as(uid).collection("users").where("role", "in", roles).get();
    const snapshot = await assertSucceeds(listing("admin-1"));
    expect(snapshot.docs.map((d) => d.id)).toEqual(expect.arrayContaining(["merchant-a", "merchant-pending", "courier-1"]));
    expect(snapshot.docs.every((d) => roles.includes(d.data().role))).toBe(true);
    for (const uid of ["customer-1", "merchant-a", "courier-1"]) await assertFails(listing(uid));
  });
});
