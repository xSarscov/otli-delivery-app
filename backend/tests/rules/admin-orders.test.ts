import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { serverTime, useCatalogEnv } from "./catalog-support";
import { claim, seedCourier, seedFreeCourier, seedReadyOrder, seedServing } from "./dispatch-support";

const { as, admin } = useCatalogEnv();

type Db = ReturnType<typeof as>;

/** The order half of the release the Admin screen writes: back to ready, no courier. */
const releaseOrder = (overrides: Record<string, unknown> = {}) => ({ status: "ready", courierId: null, updatedAt: serverTime(), ...overrides });
/** The courier half: the slot is freed. */
const releaseSlot = (overrides: Record<string, unknown> = {}) => ({ activeOrderId: null, updatedAt: serverTime(), ...overrides });

/** Releases [orderId] of [courierUid] the way the app does: both documents in one atomic write. */
function release(db: Db, orderId: string, courierUid: string, order: Record<string, unknown> = {}, slot: Record<string, unknown> = {}) {
  const batch = db.batch();
  batch.update(db.collection("orders").doc(orderId), releaseOrder(order));
  batch.update(db.collection("couriers").doc(courierUid), releaseSlot(slot));
  return batch.commit();
}

/** The update the Admin screen writes to cancel with [reason]. */
const cancelOrder = (reason: unknown = "Store never answered", overrides: Record<string, unknown> = {}) => ({
  status: "cancelled",
  cancelledBy: "admin",
  cancelReason: reason,
  cancelledAt: serverTime(),
  updatedAt: serverTime(),
  ...overrides,
});

const order = async (id: string) => (await admin((db) => db.collection("orders").doc(id).get())).data();
const courier = async (uid: string) => (await admin((db) => db.collection("couriers").doc(uid).get())).data();
const orderRef = (uid: string, id: string) => as(uid).collection("orders").doc(id);

describe("releasing a claimed order", () => {
  it("puts the order back in the pool and frees the courier in one write", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await assertSucceeds(release(as("admin-1"), "o1", "courier-1"));
    expect(await order("o1")).toMatchObject({ status: "ready", courierId: null });
    expect(await courier("courier-1")).toMatchObject({ isOnline: true, activeOrderId: null });
  });

  it("lets another courier claim the released order", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await seedFreeCourier(admin, "courier-2");
    await assertSucceeds(release(as("admin-1"), "o1", "courier-1"));
    await assertSucceeds(claim(as("courier-2"), "courier-2", "o1"));
    expect(await order("o1")).toMatchObject({ status: "claimed", courierId: "courier-2" });
  });

  it("works when the courier was suspended meanwhile, and leaves their online flag alone", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await admin((db) => db.collection("users").doc("courier-1").update({ status: "suspended" }));
    await assertSucceeds(release(as("admin-1"), "o1", "courier-1"));
    expect(await courier("courier-1")).toMatchObject({ isOnline: true, activeOrderId: null });
  });

  it("is denied once the courier picked the order up", async () => {
    await seedServing(admin, "courier-1", "o1", "picked_up");
    await assertFails(release(as("admin-1"), "o1", "courier-1"));
    expect(await order("o1")).toMatchObject({ status: "picked_up", courierId: "courier-1" });
    expect(await courier("courier-1")).toMatchObject({ activeOrderId: "o1" });
  });

  it("is denied for the order alone or the courier slot alone", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await assertFails(orderRef("admin-1", "o1").update(releaseOrder()));
    await assertFails(as("admin-1").collection("couriers").doc("courier-1").update(releaseSlot()));
    expect(await order("o1")).toMatchObject({ status: "claimed", courierId: "courier-1" });
    expect(await courier("courier-1")).toMatchObject({ activeOrderId: "o1" });
  });

  it("is denied to everyone but an active admin", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await seedFreeCourier(admin, "courier-2");
    for (const uid of ["customer-1", "merchant-a", "courier-1", "courier-2"]) {
      await assertFails(release(as(uid), "o1", "courier-1"));
    }
    await admin((db) =>
      db.collection("users").doc("admin-off").set({ role: "admin", status: "suspended", displayName: "x", email: "x@otli.test", phone: "1", createdAt: new Date() }),
    );
    await assertFails(release(as("admin-off"), "o1", "courier-1"));
    expect(await order("o1")).toMatchObject({ status: "claimed", courierId: "courier-1" });
  });

  it("must clear the courier and touch nothing else", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    const admin1 = as("admin-1");
    await assertFails(release(admin1, "o1", "courier-1", { courierId: "courier-1" }));
    await assertFails(release(admin1, "o1", "courier-1", { courierId: "courier-2" }));
    await assertFails(release(admin1, "o1", "courier-1", { totalCents: 1 }));
    await assertFails(release(admin1, "o1", "courier-1", { readyAt: serverTime() }));
    await assertFails(release(admin1, "o1", "courier-1", { updatedAt: new Date() }));
    await assertFails(release(admin1, "o1", "courier-1", {}, { isOnline: false }));
    await assertFails(release(admin1, "o1", "courier-1", {}, { updatedAt: new Date() }));
    await assertSucceeds(release(admin1, "o1", "courier-1"));
  });

  it("is denied when the courier's slot does not hold this order", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await admin((db) => db.collection("couriers").doc("courier-1").update({ activeOrderId: "other" }));
    await assertFails(release(as("admin-1"), "o1", "courier-1"));
    await seedCourier(admin, "courier-2", { isOnline: true, activeOrderId: "o1" });
    await assertFails(release(as("admin-1"), "o1", "courier-2"));
  });

  it("is denied while the slot holds another order, even when that one is released in the same write", async () => {
    await seedServing(admin, "courier-1", "o2", "claimed");
    await seedReadyOrder(admin, "o1", { status: "claimed", courierId: "courier-1" });
    const db = as("admin-1");
    const batch = db.batch();
    batch.update(db.collection("orders").doc("o1"), releaseOrder());
    batch.update(db.collection("orders").doc("o2"), releaseOrder());
    batch.update(db.collection("couriers").doc("courier-1"), releaseSlot());
    await assertFails(batch.commit());
    expect(await order("o1")).toMatchObject({ status: "claimed" });
    expect(await order("o2")).toMatchObject({ status: "claimed" });
  });
});

describe("cancelling an order no courier holds", () => {
  it.each(["placed", "accepted", "preparing", "ready"])("lets the admin cancel a %s order with a reason, recording who and why", async (status) => {
    await seedReadyOrder(admin, "o1", { status });
    await assertSucceeds(orderRef("admin-1", "o1").update(cancelOrder("Merchant closed for the day")));
    expect(await order("o1")).toMatchObject({ status: "cancelled", cancelledBy: "admin", cancelReason: "Merchant closed for the day" });
    expect((await order("o1"))?.cancelledAt).toBeDefined();
  });

  it.each(["claimed", "picked_up", "delivered", "rejected", "cancelled"])("is denied for a %s order", async (status) => {
    await seedReadyOrder(admin, "o1", { status, courierId: ["claimed", "picked_up", "delivered"].includes(status) ? "courier-1" : null });
    await assertFails(orderRef("admin-1", "o1").update(cancelOrder()));
    expect((await order("o1"))?.status).toBe(status);
  });

  it("needs a reason of 1 to 200 characters of text", async () => {
    for (const reason of ["", "   ", undefined, null, 42, "x".repeat(201)]) {
      await seedReadyOrder(admin, "o1");
      const { cancelReason: _omitted, ...withoutReason } = cancelOrder();
      await assertFails(orderRef("admin-1", "o1").update(reason === undefined ? withoutReason : cancelOrder(reason)));
    }
    await seedReadyOrder(admin, "o1");
    await assertSucceeds(orderRef("admin-1", "o1").update(cancelOrder("x".repeat(200))));
  });

  it("must say the admin cancelled and use the server time", async () => {
    await seedReadyOrder(admin, "o1");
    const { cancelledBy: _by, ...withoutBy } = cancelOrder();
    await assertFails(orderRef("admin-1", "o1").update(withoutBy));
    await assertFails(orderRef("admin-1", "o1").update(cancelOrder("Reason", { cancelledBy: "customer" })));
    await assertFails(orderRef("admin-1", "o1").update(cancelOrder("Reason", { cancelledBy: "merchant" })));
    await assertFails(orderRef("admin-1", "o1").update(cancelOrder("Reason", { cancelledAt: new Date() })));
    await assertFails(orderRef("admin-1", "o1").update(cancelOrder("Reason", { updatedAt: new Date() })));
  });

  it("can only move the order to cancelled, whatever else the cancellation fields say", async () => {
    for (const status of ["placed", "accepted", "ready", "claimed", "delivered", "rejected"]) {
      await seedReadyOrder(admin, "o1", { status: "preparing" });
      await assertFails(orderRef("admin-1", "o1").update(cancelOrder("Reason", { status })));
      expect((await order("o1"))?.status).toBe("preparing");
    }
  });

  it("touches only the cancellation fields", async () => {
    await seedReadyOrder(admin, "o1");
    await assertFails(orderRef("admin-1", "o1").update(cancelOrder("Reason", { totalCents: 1 })));
    await assertFails(orderRef("admin-1", "o1").update(cancelOrder("Reason", { courierId: "courier-1" })));
    await assertFails(orderRef("admin-1", "o1").update(cancelOrder("Reason", { rejectReason: "x" })));
  });

  it("is denied to everyone but an active admin, even when they claim to be the admin", async () => {
    for (const uid of ["customer-1", "merchant-a", "courier-1", "merchant-b"]) {
      await seedReadyOrder(admin, "o1", { status: "placed" });
      await assertFails(orderRef(uid, "o1").update(cancelOrder()));
    }
    await admin((db) =>
      db.collection("users").doc("admin-off").set({ role: "admin", status: "suspended", displayName: "x", email: "x@otli.test", phone: "1", createdAt: new Date() }),
    );
    await assertFails(orderRef("admin-off", "o1").update(cancelOrder()));
    expect((await order("o1"))?.status).toBe("placed");
  });

  it("is possible after the claim was released, and the customer then reads the reason", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await assertFails(orderRef("admin-1", "o1").update(cancelOrder()));
    await assertSucceeds(release(as("admin-1"), "o1", "courier-1"));
    await assertSucceeds(orderRef("admin-1", "o1").update(cancelOrder("Courier unreachable")));

    const seen = await assertSucceeds(orderRef("customer-1", "o1").get());
    expect(seen.data()).toMatchObject({ status: "cancelled", cancelReason: "Courier unreachable", cancelledBy: "admin" });
  });

  it("leaves a cancelled order final", async () => {
    await seedReadyOrder(admin, "o1", { status: "preparing" });
    await assertSucceeds(orderRef("admin-1", "o1").update(cancelOrder()));
    await assertFails(orderRef("admin-1", "o1").update(cancelOrder("Again")));
    await assertFails(orderRef("merchant-a", "o1").update({ status: "ready", readyAt: serverTime(), updatedAt: serverTime() }));
  });
});
