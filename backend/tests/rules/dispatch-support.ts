import type { useCatalogEnv } from "./catalog-support";
import { serverTime } from "./catalog-support";
import { orderDoc } from "./order-support";

type Env = ReturnType<typeof useCatalogEnv>;
type Db = ReturnType<Env["as"]>;

/** A `couriers/{uid}` document as stored: offline and free unless a test says otherwise. */
export function courierDoc(overrides: Record<string, unknown> = {}) {
  return { isOnline: false, activeOrderId: null, updatedAt: new Date(), ...overrides };
}

/**
 * Seeds `users/{uid}` (an account of the courier role in [status]) and `couriers/{uid}` with
 * [courier] on top of [courierDoc], bypassing the rules.
 */
export async function seedCourier(admin: Env["admin"], uid: string, courier: Record<string, unknown> = {}, status = "active") {
  await admin(async (db) => {
    await db.collection("users").doc(uid).set({ role: "courier", status, displayName: uid, email: `${uid}@otli.test`, phone: "1", createdAt: new Date() });
    await db.collection("couriers").doc(uid).set(courierDoc(courier));
  });
}

/** An online courier with no order, ready to claim. */
export const seedFreeCourier = (admin: Env["admin"], uid: string) => seedCourier(admin, uid, { isOnline: true });

/** Seeds an order of customer-1 at merchant-a that the merchant marked ready, bypassing the rules. */
export const seedReadyOrder = (admin: Env["admin"], id: string, overrides: Record<string, unknown> = {}) =>
  admin((db) =>
    db.collection("orders").doc(id).set(orderDoc({ status: "ready", createdAt: new Date(), readyAt: new Date(), updatedAt: new Date(), ...overrides })),
  );

/** The order half of a claim by [uid]. */
export const claimOrder = (uid: string, overrides: Record<string, unknown> = {}) => ({
  status: "claimed",
  courierId: uid,
  claimedAt: serverTime(),
  updatedAt: serverTime(),
  ...overrides,
});

/** The courier half of a claim: the slot now holds [orderId]. */
export const claimSlot = (orderId: string | null, overrides: Record<string, unknown> = {}) => ({
  activeOrderId: orderId,
  updatedAt: serverTime(),
  ...overrides,
});

/** Seeds courier [uid] serving order [orderId] in [status] (claimed or picked_up): the order names the courier and the slot holds the order. */
export async function seedServing(admin: Env["admin"], uid: string, orderId: string, status: "claimed" | "picked_up" = "picked_up") {
  await seedCourier(admin, uid, { isOnline: true, activeOrderId: orderId });
  await seedReadyOrder(admin, orderId, { status, courierId: uid });
}

/** The order half of picking the order up. */
export const pickUpOrder = (overrides: Record<string, unknown> = {}) => ({
  status: "picked_up",
  pickedUpAt: serverTime(),
  updatedAt: serverTime(),
  ...overrides,
});

/** The order half of a delivery. */
export const deliverOrder = (overrides: Record<string, unknown> = {}) => ({
  status: "delivered",
  deliveredAt: serverTime(),
  updatedAt: serverTime(),
  ...overrides,
});

/** Delivers [orderId] the way the app does: the order and the freed courier slot in one atomic write. */
export function deliver(db: Db, uid: string, orderId: string, order: Record<string, unknown> = {}, slot: Record<string, unknown> = {}) {
  const batch = db.batch();
  batch.update(db.collection("orders").doc(orderId), deliverOrder(order));
  batch.update(db.collection("couriers").doc(uid), claimSlot(null, slot));
  return batch.commit();
}

/** Claims [orderId] the way the app does, both documents in one atomic write. */
export function claim(db: Db, uid: string, orderId: string, order: Record<string, unknown> = {}, slot: Record<string, unknown> = {}) {
  const batch = db.batch();
  batch.update(db.collection("orders").doc(orderId), claimOrder(uid, order));
  batch.update(db.collection("couriers").doc(uid), claimSlot(orderId, slot));
  return batch.commit();
}
