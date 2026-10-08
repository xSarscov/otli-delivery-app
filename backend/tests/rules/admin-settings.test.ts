import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { DELIVERY_FEE_CENTS, serverTime, useCatalogEnv } from "./catalog-support";
import { orderDoc } from "./order-support";

const { as, signedOut, admin } = useCatalogEnv();

/** The document the Admin fee editor writes: the amount, when, and by whom. */
const feeDoc = (cents: unknown, overrides: Record<string, unknown> = {}) => ({
  deliveryFeeCents: cents,
  updatedAt: serverTime(),
  updatedBy: "admin-1",
  ...overrides,
});

const settings = (uid: string) => as(uid).collection("settings").doc("app");
const stored = async () => (await admin((db) => db.collection("settings").doc("app").get())).data();

describe("settings/app writes", () => {
  it("lets an active admin set the fee, replacing the document or patching it", async () => {
    await assertSucceeds(settings("admin-1").set(feeDoc(4000)));
    expect(await stored()).toMatchObject({ deliveryFeeCents: 4000, updatedBy: "admin-1" });
    await assertSucceeds(settings("admin-1").update(feeDoc(2500)));
    expect(await stored()).toMatchObject({ deliveryFeeCents: 2500 });
  });

  it("creates the document when none exists yet", async () => {
    await admin((db) => db.collection("settings").doc("app").delete());
    await assertSucceeds(settings("admin-1").set(feeDoc(3500)));
    expect(await stored()).toMatchObject({ deliveryFeeCents: 3500 });
  });

  it("denies a customer, a merchant, a courier and a signed-out visitor", async () => {
    for (const uid of ["customer-1", "merchant-a", "courier-1"]) {
      await assertFails(settings(uid).set(feeDoc(1, { updatedBy: uid })));
      await assertFails(settings(uid).update(feeDoc(1, { updatedBy: uid })));
    }
    await assertFails(signedOut().collection("settings").doc("app").set(feeDoc(4000)));
    expect(await stored()).toMatchObject({ deliveryFeeCents: DELIVERY_FEE_CENTS });
  });

  it("denies an admin account that is not active", async () => {
    for (const status of ["suspended", "pending"]) {
      await admin((db) =>
        db.collection("users").doc(`admin-${status}`).set({ role: "admin", status, displayName: "x", email: "x@otli.test", phone: "1", createdAt: new Date() }),
      );
      await assertFails(settings(`admin-${status}`).set(feeDoc(4000, { updatedBy: `admin-${status}` })));
    }
  });

  it("denies an amount that is not a positive integer of centavos", async () => {
    for (const cents of [0, -1, 30.5, "3000", null, true]) {
      await assertFails(settings("admin-1").set(feeDoc(cents)));
    }
    await assertFails(settings("admin-1").set({ updatedAt: serverTime(), updatedBy: "admin-1" }));
    await assertSucceeds(settings("admin-1").set(feeDoc(1)));
  });

  it("requires the server time and the admin's own uid as the audit stamp", async () => {
    await assertFails(settings("admin-1").set(feeDoc(4000, { updatedAt: new Date() })));
    await assertFails(settings("admin-1").set({ deliveryFeeCents: 4000, updatedBy: "admin-1" }));
    await assertFails(settings("admin-1").set(feeDoc(4000, { updatedBy: "someone-else" })));
    await assertFails(settings("admin-1").set({ deliveryFeeCents: 4000, updatedAt: serverTime() }));
    expect(await stored()).toMatchObject({ deliveryFeeCents: DELIVERY_FEE_CENTS });
  });

  it("denies extra fields", async () => {
    await assertFails(settings("admin-1").set(feeDoc(4000, { currency: "USD" })));
  });

  it("denies deleting the settings, even for the admin", async () => {
    await assertFails(settings("admin-1").delete());
    expect(await stored()).toMatchObject({ deliveryFeeCents: DELIVERY_FEE_CENTS });
  });
});

describe("settings/app reads", () => {
  it("lets every signed-in role read the fee and denies a signed-out visitor", async () => {
    for (const uid of ["customer-1", "merchant-a", "courier-1", "admin-1"]) {
      const snapshot = await assertSucceeds(settings(uid).get());
      expect(snapshot.data()?.deliveryFeeCents).toBe(DELIVERY_FEE_CENTS);
    }
    await assertFails(signedOut().collection("settings").doc("app").get());
  });
});

describe("a fee change and the orders around it", () => {
  const place = (id: string, fee: number) =>
    as("customer-1").collection("orders").doc(id).set(orderDoc({ deliveryFeeCents: fee, totalCents: 18_000 + fee }));

  it("applies the new fee to later orders only and leaves placed orders untouched", async () => {
    await assertSucceeds(place("before", DELIVERY_FEE_CENTS));
    await assertSucceeds(settings("admin-1").set(feeDoc(4500)));

    await assertFails(place("stale", DELIVERY_FEE_CENTS));
    await assertSucceeds(place("after", 4500));

    const before = await admin((db) => db.collection("orders").doc("before").get());
    expect(before.data()).toMatchObject({ deliveryFeeCents: DELIVERY_FEE_CENTS, totalCents: 18_000 + DELIVERY_FEE_CENTS });
    const after = await admin((db) => db.collection("orders").doc("after").get());
    expect(after.data()).toMatchObject({ deliveryFeeCents: 4500, totalCents: 22_500 });
  });
});
