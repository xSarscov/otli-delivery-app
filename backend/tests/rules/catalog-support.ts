import { readFileSync } from "node:fs";
import { initializeTestEnvironment, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import firebase from "firebase/compat/app";
import "firebase/compat/firestore";
import { afterAll, beforeAll, beforeEach } from "vitest";

export const serverTime = () => firebase.firestore.FieldValue.serverTimestamp();
export const bytesOf = (size: number) => firebase.firestore.Blob.fromUint8Array(new Uint8Array(size).fill(0xff));

export const KB = 1024;

/** A valid `merchants/{uid}` create payload; tests override one field at a time. */
export function merchantDoc(overrides: Record<string, unknown> = {}) {
  return {
    name: "Comedor Nagarote",
    description: "Comida tipica",
    phone: "8888-1111",
    status: "pending",
    isOpen: false,
    createdAt: serverTime(),
    updatedAt: serverTime(),
    ...overrides,
  };
}

/** A valid product payload; tests override one field at a time. */
export function productDoc(overrides: Record<string, unknown> = {}) {
  return {
    categoryId: "cat-1",
    name: "Gallo pinto",
    description: "Con queso",
    priceCents: 9000,
    isAvailable: true,
    photoVersion: 0,
    updatedAt: serverTime(),
    ...overrides,
  };
}

const user = (role: string, status: string) => ({
  role,
  status,
  displayName: role,
  email: `${role}@otli.test`,
  phone: "1",
  createdAt: new Date(),
});

/**
 * Boots the rules environment once per test file and, before every test, resets Firestore to:
 * users admin-1, customer-1, merchant-a / merchant-b (active), merchant-pending, merchant-suspended;
 * active merchants merchant-a (open) and merchant-b (closed), each with category `cat-1`,
 * product `prod-1` and a photo document; plus pending and suspended merchant profiles.
 */
export function useCatalogEnv() {
  let env: RulesTestEnvironment;

  beforeAll(async () => {
    env = await initializeTestEnvironment({
      projectId: "demo-otli",
      firestore: { rules: readFileSync("firestore.rules", "utf8") },
    });
  });

  afterAll(async () => {
    await env.cleanup();
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc("admin-1").set(user("admin", "active"));
      await db.collection("users").doc("customer-1").set(user("customer", "active"));
      await db.collection("users").doc("merchant-a").set(user("merchant", "active"));
      await db.collection("users").doc("merchant-b").set(user("merchant", "active"));
      await db.collection("users").doc("merchant-pending").set(user("merchant", "pending"));
      await db.collection("users").doc("merchant-suspended").set(user("merchant", "suspended"));
      for (const [uid, open] of [["merchant-a", true], ["merchant-b", false]] as const) {
        const base = db.collection("merchants").doc(uid);
        await base.set(merchantDoc({ status: "active", isOpen: open, createdAt: new Date(), updatedAt: new Date() }));
        await base.collection("categories").doc("cat-1").set({ name: "Platos", sortOrder: 1 });
        await base.collection("products").doc("prod-1").set(productDoc({ updatedAt: new Date() }));
        await base.collection("productPhotos").doc("prod-1").set({ jpeg: bytesOf(10), version: 1 });
      }
      for (const [uid, status] of [["merchant-pending", "pending"], ["merchant-suspended", "suspended"]] as const) {
        await db.collection("merchants").doc(uid).set(merchantDoc({ status, createdAt: new Date(), updatedAt: new Date() }));
      }
    });
  });

  return {
    as: (uid: string) => env.authenticatedContext(uid, { email: "user@otli.test" }).firestore(),
    signedOut: () => env.unauthenticatedContext().firestore(),
    /** Runs [action] with security rules disabled, for arranging or asserting stored state. */
    admin: async <T>(action: (db: ReturnType<ReturnType<RulesTestEnvironment["authenticatedContext"]>["firestore"]>) => Promise<T>) => {
      let result: T | undefined;
      await env.withSecurityRulesDisabled(async (context) => {
        result = await action(context.firestore());
      });
      return result as T;
    },
  };
}
