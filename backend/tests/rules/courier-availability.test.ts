import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { serverTime, useCatalogEnv } from "./catalog-support";
import { courierDoc, seedCourier } from "./dispatch-support";

const { as, signedOut, admin } = useCatalogEnv();

const courierRef = (uid: string, asUid = uid) => as(asUid).collection("couriers").doc(uid);
const stored = async (uid: string) => (await admin((db) => db.collection("couriers").doc(uid).get())).data();
const goOnline = (online: boolean, extra: Record<string, unknown> = {}) => ({ isOnline: online, updatedAt: serverTime(), ...extra });

describe("couriers/{uid} availability", () => {
  it("lets an active courier go online and offline", async () => {
    await seedCourier(admin, "courier-1");
    await assertSucceeds(courierRef("courier-1").update(goOnline(true)));
    expect((await stored("courier-1"))?.isOnline).toBe(true);
    await assertSucceeds(courierRef("courier-1").update(goOnline(false)));
    expect((await stored("courier-1"))?.isOnline).toBe(false);
  });

  it("stamps updatedAt with the server time", async () => {
    await seedCourier(admin, "courier-1");
    await assertFails(courierRef("courier-1").update({ isOnline: true, updatedAt: new Date() }));
    await assertFails(courierRef("courier-1").update({ isOnline: true }));
  });

  it("takes only a boolean for isOnline", async () => {
    await seedCourier(admin, "courier-1");
    await assertFails(courierRef("courier-1").update(goOnline("yes" as unknown as boolean)));
    await assertFails(courierRef("courier-1").update(goOnline(1 as unknown as boolean)));
  });

  it("cannot go offline while serving an order, but may keep or set itself online", async () => {
    await seedCourier(admin, "courier-1", { isOnline: true, activeOrderId: "order-1" });
    await assertFails(courierRef("courier-1").update(goOnline(false)));
    await assertSucceeds(courierRef("courier-1").update(goOnline(true)));
    expect((await stored("courier-1"))?.activeOrderId).toBe("order-1");
  });

  it("goes offline once the active order is gone", async () => {
    await seedCourier(admin, "courier-1", { isOnline: true, activeOrderId: null });
    await assertSucceeds(courierRef("courier-1").update(goOnline(false)));
  });

  it("never writes the active order slot through the availability toggle", async () => {
    await seedCourier(admin, "courier-1", { isOnline: true });
    await assertFails(courierRef("courier-1").update(goOnline(true, { activeOrderId: "order-1" })));
    await seedCourier(admin, "courier-2", { isOnline: true, activeOrderId: "order-2" });
    await assertFails(courierRef("courier-2").update(goOnline(true, { activeOrderId: null })));
    expect((await stored("courier-2"))?.activeOrderId).toBe("order-2");
  });

  it("cannot add other fields", async () => {
    await seedCourier(admin, "courier-1");
    await assertFails(courierRef("courier-1").update(goOnline(true, { rating: 5 })));
  });

  it("denies a courier toggling another courier", async () => {
    await seedCourier(admin, "courier-1");
    await seedCourier(admin, "courier-2");
    await assertFails(courierRef("courier-1", "courier-2").update(goOnline(true)));
    expect((await stored("courier-1"))?.isOnline).toBe(false);
  });

  it("denies a courier whose account is pending or suspended", async () => {
    await seedCourier(admin, "courier-pending", {}, "pending");
    await seedCourier(admin, "courier-suspended", {}, "suspended");
    await assertFails(courierRef("courier-pending").update(goOnline(true)));
    await assertFails(courierRef("courier-suspended").update(goOnline(true)));
  });

  it("denies an account that has a courier document but is not a courier", async () => {
    await admin((db) => db.collection("couriers").doc("customer-1").set(courierDoc()));
    await admin((db) => db.collection("couriers").doc("admin-1").set(courierDoc()));
    await assertFails(courierRef("customer-1").update(goOnline(true)));
    await assertFails(courierRef("admin-1").update(goOnline(true)));
  });

  it("denies a signed-out user", async () => {
    await seedCourier(admin, "courier-1");
    await assertFails(signedOut().collection("couriers").doc("courier-1").update(goOnline(true)));
  });
});

describe("couriers/{uid} create", () => {
  const newCourier = (overrides: Record<string, unknown> = {}) => ({ isOnline: false, activeOrderId: null, updatedAt: serverTime(), ...overrides });

  const registerAs = (uid: string, role: string) =>
    admin((db) =>
      db.collection("users").doc(uid).set({ role, status: "pending", displayName: uid, email: "x@otli.test", phone: "1", createdAt: new Date() }),
    );

  it("lets a courier create their own offline, free document", async () => {
    await registerAs("new-courier", "courier");
    await assertSucceeds(courierRef("new-courier").set(newCourier()));
    expect(await stored("new-courier")).toMatchObject({ isOnline: false, activeOrderId: null });
  });

  it("lets a registering courier create it together with users/{uid} in one batch", async () => {
    const db = as("batch-courier");
    const batch = db.batch();
    batch.set(db.collection("users").doc("batch-courier"), {
      role: "courier",
      status: "pending",
      displayName: "B",
      email: "b@otli.test",
      phone: "1",
      createdAt: serverTime(),
    });
    batch.set(db.collection("couriers").doc("batch-courier"), newCourier());
    await assertSucceeds(batch.commit());
  });

  it("denies creating it online, with an order, with extra or missing fields or with a client clock", async () => {
    for (const [index, overrides] of [{ isOnline: true }, { activeOrderId: "order-1" }, { rating: 5 }, { updatedAt: new Date() }].entries()) {
      await registerAs(`bad-${index}`, "courier");
      await assertFails(courierRef(`bad-${index}`).set(newCourier(overrides)));
    }
    await registerAs("no-flag", "courier");
    const { isOnline: _online, ...withoutOnline } = newCourier();
    await assertFails(courierRef("no-flag").set(withoutOnline));
    await registerAs("no-slot", "courier");
    const { activeOrderId: _slot, ...withoutSlot } = newCourier();
    await assertFails(courierRef("no-slot").set(withoutSlot));
  });

  it("denies creating it for another uid or for an account that is not a courier", async () => {
    await registerAs("new-courier", "courier");
    await assertFails(courierRef("new-courier", "courier-1").set(newCourier()));
    await registerAs("new-merchant", "merchant");
    await assertFails(courierRef("new-merchant").set(newCourier()));
    await assertFails(courierRef("customer-1").set(newCourier()));
  });
});

describe("couriers/{uid} read and delete", () => {
  it("lets the courier and the admin read the document, nobody else", async () => {
    await seedCourier(admin, "courier-1", { isOnline: true });
    await seedCourier(admin, "courier-2");
    await assertSucceeds(courierRef("courier-1").get());
    await assertSucceeds(courierRef("courier-1", "admin-1").get());
    await assertFails(courierRef("courier-1", "courier-2").get());
    await assertFails(courierRef("courier-1", "customer-1").get());
    await assertFails(courierRef("courier-1", "merchant-a").get());
    await assertFails(signedOut().collection("couriers").doc("courier-1").get());
  });

  it("denies deleting it, even for the courier and the admin", async () => {
    await seedCourier(admin, "courier-1");
    await assertFails(courierRef("courier-1").delete());
    await assertFails(courierRef("courier-1", "admin-1").delete());
    expect(await stored("courier-1")).toBeDefined();
  });
});
