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
