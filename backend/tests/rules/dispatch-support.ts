import type { useCatalogEnv } from "./catalog-support";

type Env = ReturnType<typeof useCatalogEnv>;

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
