import { serverTime } from "./catalog-support";
import type { useCatalogEnv } from "./catalog-support";

type Env = ReturnType<typeof useCatalogEnv>;

/**
 * The `liveLocations/{orderId}` payload exactly as the Android adapter writes it
 * (`LiveLocationDocuments.publishPayload`): the signed-in courier's id, the fix and a server timestamp.
 * Tests override one field at a time.
 */
export function liveFix(courierId = "courier-1", overrides: Record<string, unknown> = {}) {
  return { courierId, lat: 12.27, lng: -86.57, accuracyM: 8, updatedAt: serverTime(), ...overrides };
}

/**
 * Seeds a stored `liveLocations/{orderId}` published [ageSeconds] ago, bypassing the rules. The rules
 * compare `request.time` with the stored `updatedAt`, so the age decides the 5-second floor.
 */
export const seedLiveFix = (admin: Env["admin"], orderId: string, ageSeconds: number, courierId = "courier-1") =>
  admin((db) =>
    db
      .collection("liveLocations")
      .doc(orderId)
      .set(liveFix(courierId, { updatedAt: new Date(Date.now() - ageSeconds * 1000) })),
  );
