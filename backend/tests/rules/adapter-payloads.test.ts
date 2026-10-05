import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { useCatalogEnv, serverTime } from "./catalog-support";
import { seedFreeCourier, seedReadyOrder } from "./dispatch-support";
import { liveFix } from "./live-support";

/**
 * The exact documents the Android adapter writes (`DispatchDocuments`), in the exact shape of its
 * transactions (`FirestoreDispatchRepository`): the claim reads the order, the courier and the
 * `users` document before writing, the pickup is a single update, the delivery is a read-less
 * transaction and going online is a single update. The rules must accept each of them.
 */
const { as, admin } = useCatalogEnv();

const UID = "courier-1";

const claimOrderUpdate = () => ({ status: "claimed", courierId: UID, claimedAt: serverTime(), updatedAt: serverTime() });
const claimCourierUpdate = (orderId: string) => ({ activeOrderId: orderId, updatedAt: serverTime() });
const pickUpUpdate = () => ({ status: "picked_up", pickedUpAt: serverTime(), updatedAt: serverTime() });
const deliverOrderUpdate = () => ({ status: "delivered", deliveredAt: serverTime(), updatedAt: serverTime() });
const deliverCourierUpdate = () => ({ activeOrderId: null, updatedAt: serverTime() });
const onlineUpdate = (online: boolean) => ({ isOnline: online, updatedAt: serverTime() });

const order = async (id: string) => (await admin((db) => db.collection("orders").doc(id).get())).data();
const courier = async () => (await admin((db) => db.collection("couriers").doc(UID).get())).data();

function claimLikeTheAdapter(orderId: string) {
  const db = as(UID);
  const orderRef = db.collection("orders").doc(orderId);
  const courierRef = db.collection("couriers").doc(UID);
  return db.runTransaction(async (tx) => {
    await tx.get(orderRef);
    await tx.get(courierRef);
    await tx.get(db.collection("users").doc(UID));
    tx.update(orderRef, claimOrderUpdate());
    tx.update(courierRef, claimCourierUpdate(orderId));
  });
}

function deliverLikeTheAdapter(orderId: string) {
  const db = as(UID);
  return db.runTransaction(async (tx) => {
    tx.update(db.collection("orders").doc(orderId), deliverOrderUpdate());
    tx.update(db.collection("couriers").doc(UID), deliverCourierUpdate());
  });
}

describe("the writes of the Android dispatch adapter", () => {
  it("go online, claim, pick up, deliver and go offline, one after the other", async () => {
    await seedReadyOrder(admin, "o1");
    await seedCourier(admin);
    await assertSucceeds(as(UID).collection("couriers").doc(UID).update(onlineUpdate(true)));
    await assertSucceeds(claimLikeTheAdapter("o1"));
    expect(await order("o1")).toMatchObject({ status: "claimed", courierId: UID });
    expect(await courier()).toMatchObject({ isOnline: true, activeOrderId: "o1" });

    await assertSucceeds(as(UID).collection("orders").doc("o1").update(pickUpUpdate()));
    await assertSucceeds(deliverLikeTheAdapter("o1"));
    expect(await order("o1")).toMatchObject({ status: "delivered" });
    expect(await courier()).toMatchObject({ isOnline: true, activeOrderId: null });

    await assertSucceeds(as(UID).collection("couriers").doc(UID).update(onlineUpdate(false)));
  });

  it("cannot go offline while holding an order, and cannot deliver before the pickup", async () => {
    await seedReadyOrder(admin, "o1");
    await seedFreeCourier(admin, UID);
    await assertSucceeds(claimLikeTheAdapter("o1"));
    await assertFails(as(UID).collection("couriers").doc(UID).update(onlineUpdate(false)));
    await assertFails(deliverLikeTheAdapter("o1"));
    expect(await order("o1")).toMatchObject({ status: "claimed" });
    expect(await courier()).toMatchObject({ activeOrderId: "o1" });
  });
});

describe("the live location write of the Android tracking adapter", () => {
  const publish = (orderId: string, lat: number) =>
    as(UID).collection("liveLocations").doc(orderId).set(liveFix(UID, { lat }));

  it("publishes while claimed, again after the floor, and stops at the delivery", async () => {
    await seedReadyOrder(admin, "o1");
    await seedFreeCourier(admin, UID);
    await assertFails(publish("o1", 12.2));
    await assertSucceeds(claimLikeTheAdapter("o1"));
    await assertSucceeds(publish("o1", 12.2));
    await assertFails(publish("o1", 12.3));
    await admin((db) => db.collection("liveLocations").doc("o1").update({ updatedAt: new Date(Date.now() - 6000) }));
    await assertSucceeds(as(UID).collection("orders").doc("o1").update(pickUpUpdate()));
    await assertSucceeds(publish("o1", 12.4));
    await admin((db) => db.collection("liveLocations").doc("o1").update({ updatedAt: new Date(Date.now() - 6000) }));
    await assertSucceeds(deliverLikeTheAdapter("o1"));
    await assertFails(publish("o1", 12.5));
    expect((await admin((db) => db.collection("liveLocations").doc("o1").get())).data()?.lat).toBe(12.4);
  });
});

async function seedCourier(adminFn: typeof admin) {
  await adminFn((db) => db.collection("couriers").doc(UID).set({ isOnline: false, activeOrderId: null, updatedAt: new Date() }));
}

/**
 * The exact documents the Android Admin adapter writes (`AdminDocuments`), in the exact shape of its
 * calls (`FirestoreAdminRepository`): the fee is a whole-document set, a status change is one batch of
 * the account and, for a merchant, its mirror, the release is a transaction that reads the order first,
 * the cancellation is one update and the three observers are the queries below.
 */
describe("the writes and queries of the Android admin adapter", () => {
  const ADMIN = "admin-1";
  const feeData = (cents: number) => ({ deliveryFeeCents: cents, updatedAt: serverTime(), updatedBy: ADMIN });
  const userStatusUpdate = (status: string) => ({ status });
  const merchantMirrorUpdate = (status: string) => ({ status, updatedAt: serverTime() });
  const releaseOrderUpdate = () => ({ status: "ready", courierId: null, updatedAt: serverTime() });
  const releaseCourierUpdate = () => ({ activeOrderId: null, updatedAt: serverTime() });
  const cancelUpdate = (reason: string) => ({
    status: "cancelled",
    cancelledBy: "admin",
    cancelReason: reason,
    cancelledAt: serverTime(),
    updatedAt: serverTime(),
  });

  function setStatusLikeTheAdapter(uid: string, status: string, merchant: boolean) {
    const db = as(ADMIN);
    const batch = db.batch();
    batch.update(db.collection("users").doc(uid), userStatusUpdate(status));
    if (merchant) batch.update(db.collection("merchants").doc(uid), merchantMirrorUpdate(status));
    return batch.commit();
  }

  function releaseLikeTheAdapter(orderId: string, courierId: string) {
    const db = as(ADMIN);
    const orderRef = db.collection("orders").doc(orderId);
    return db.runTransaction(async (tx) => {
      await tx.get(orderRef);
      tx.update(orderRef, releaseOrderUpdate());
      tx.update(db.collection("couriers").doc(courierId), releaseCourierUpdate());
    });
  }

  it("sets the fee with a whole-document set, and the next order snapshots it", async () => {
    await assertSucceeds(as(ADMIN).collection("settings").doc("app").set(feeData(4500)));
    expect((await admin((db) => db.collection("settings").doc("app").get())).data()).toMatchObject({ deliveryFeeCents: 4500, updatedBy: ADMIN });
  });

  it("approves a pending merchant with its mirror, suspends a courier alone and reactivates them", async () => {
    await assertSucceeds(setStatusLikeTheAdapter("merchant-pending", "active", true));
    expect((await admin((db) => db.collection("merchants").doc("merchant-pending").get())).data()?.status).toBe("active");
    await assertSucceeds(setStatusLikeTheAdapter("merchant-pending", "suspended", true));
    await assertSucceeds(setStatusLikeTheAdapter("courier-1", "suspended", false));
    await assertSucceeds(setStatusLikeTheAdapter("courier-1", "active", false));
    expect((await admin((db) => db.collection("users").doc("courier-1").get())).data()?.status).toBe("active");
  });

  it("releases a claimed order and then cancels it", async () => {
    await seedReadyOrder(admin, "o1");
    await seedFreeCourier(admin, UID);
    await assertSucceeds(claimLikeTheAdapter("o1"));
    await assertSucceeds(releaseLikeTheAdapter("o1", UID));
    expect(await order("o1")).toMatchObject({ status: "ready", courierId: null });
    expect(await courier()).toMatchObject({ activeOrderId: null });
    await assertSucceeds(as(ADMIN).collection("orders").doc("o1").update(cancelUpdate("Courier unreachable")));
    expect(await order("o1")).toMatchObject({ status: "cancelled", cancelledBy: "admin", cancelReason: "Courier unreachable" });
  });

  it("reads the actionable orders, the latest orders and the merchant and courier accounts", async () => {
    await seedReadyOrder(admin, "o1");
    const orders = as(ADMIN).collection("orders");
    const actionable = await assertSucceeds(orders.where("status", "in", ["placed", "accepted", "preparing", "ready", "claimed"]).get());
    expect(actionable.docs.map((d) => d.id)).toEqual(["o1"]);
    await assertSucceeds(orders.orderBy("createdAt", "desc").limit(50).get());
    const accounts = await assertSucceeds(as(ADMIN).collection("users").where("role", "in", ["merchant", "courier"]).get());
    expect(accounts.docs.length).toBeGreaterThan(0);
  });

  it("cannot be used by anyone else", async () => {
    await seedReadyOrder(admin, "o1");
    await assertFails(as("merchant-a").collection("settings").doc("app").set({ ...feeData(1), updatedBy: "merchant-a" }));
    await assertFails(as("customer-1").collection("orders").doc("o1").update(cancelUpdate("No")));
    await assertFails(as("customer-1").collection("users").where("role", "in", ["merchant", "courier"]).get());
    await assertFails(as("customer-1").collection("orders").orderBy("createdAt", "desc").limit(50).get());
  });
});
