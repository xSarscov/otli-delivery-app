import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import firebase from "firebase/compat/app";
import "firebase/compat/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

let env: RulesTestEnvironment;

const serverTime = () => firebase.firestore.FieldValue.serverTimestamp();

/** A valid self-registration payload; individual tests override fields to break one rule at a time. */
function registration(role: string, status: string, overrides: Record<string, unknown> = {}) {
  return {
    role,
    status,
    displayName: "Test User",
    email: "user@otli.test",
    phone: "8888-0000",
    createdAt: serverTime(),
    ...overrides,
  };
}

async function seedUsers() {
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc("admin-1").set({ role: "admin", status: "active", displayName: "Admin", email: "admin@otli.test", phone: "", createdAt: new Date() });
    await db.collection("users").doc("customer-1").set({ role: "customer", status: "active", displayName: "Cust", email: "c@otli.test", phone: "1", createdAt: new Date() });
    await db.collection("users").doc("merchant-1").set({ role: "merchant", status: "pending", displayName: "Merch", email: "m@otli.test", phone: "2", createdAt: new Date() });
    await db.collection("users").doc("courier-1").set({ role: "courier", status: "active", displayName: "Cour", email: "k@otli.test", phone: "3", createdAt: new Date() });
  });
}

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
  await seedUsers();
});

const as = (uid: string) => env.authenticatedContext(uid, { email: "user@otli.test" }).firestore();

describe("users/{uid} self-registration", () => {
  it("lets a customer register as active", async () => {
    await assertSucceeds(as("new-customer").collection("users").doc("new-customer").set(registration("customer", "active")));
  });

  it("lets a merchant register as pending", async () => {
    await assertSucceeds(as("new-merchant").collection("users").doc("new-merchant").set(registration("merchant", "pending")));
  });

  it("lets a courier register as pending", async () => {
    await assertSucceeds(as("new-courier").collection("users").doc("new-courier").set(registration("courier", "pending")));
  });

  it("denies a customer registering as pending or suspended", async () => {
    await assertFails(as("u1").collection("users").doc("u1").set(registration("customer", "pending")));
    await assertFails(as("u2").collection("users").doc("u2").set(registration("customer", "suspended")));
  });

  it("denies a merchant or courier self-activating at registration", async () => {
    await assertFails(as("u3").collection("users").doc("u3").set(registration("merchant", "active")));
    await assertFails(as("u4").collection("users").doc("u4").set(registration("courier", "active")));
  });

  it("denies self-registering as admin with any status", async () => {
    await assertFails(as("u5").collection("users").doc("u5").set(registration("admin", "active")));
    await assertFails(as("u6").collection("users").doc("u6").set(registration("admin", "pending")));
  });

  it("denies unknown roles", async () => {
    await assertFails(as("u7").collection("users").doc("u7").set(registration("superuser", "active")));
  });

  it("denies creating a document for another uid", async () => {
    await assertFails(as("u8").collection("users").doc("someone-else").set(registration("customer", "active")));
  });

  it("denies signed-out registration", async () => {
    await assertFails(env.unauthenticatedContext().firestore().collection("users").doc("u9").set(registration("customer", "active")));
  });

  it("denies unexpected extra fields and a client-supplied createdAt", async () => {
    await assertFails(as("u10").collection("users").doc("u10").set(registration("customer", "active", { isAdmin: true })));
    await assertFails(as("u11").collection("users").doc("u11").set(registration("customer", "active", { createdAt: new Date(0) })));
  });

  it("denies re-creating an existing user document as a self-update path", async () => {
    await assertFails(as("merchant-1").collection("users").doc("merchant-1").set(registration("merchant", "active")));
  });
});

describe("users/{uid} self-update", () => {
  it("lets a user update their own profile fields", async () => {
    await assertSucceeds(as("customer-1").collection("users").doc("customer-1").update({ displayName: "New Name", phone: "9999" }));
  });

  it("denies a user changing their own role or status", async () => {
    await assertFails(as("customer-1").collection("users").doc("customer-1").update({ role: "admin" }));
    await assertFails(as("merchant-1").collection("users").doc("merchant-1").update({ status: "active" }));
  });

  it("denies updating another user's profile fields", async () => {
    await assertFails(as("customer-1").collection("users").doc("courier-1").update({ displayName: "Hacked" }));
  });

  it("denies deleting a user document", async () => {
    await assertFails(as("customer-1").collection("users").doc("customer-1").delete());
  });
});

describe("users/{uid} admin status changes", () => {
  it("lets an admin approve and suspend accounts", async () => {
    await assertSucceeds(as("admin-1").collection("users").doc("merchant-1").update({ status: "active" }));
    await assertSucceeds(as("admin-1").collection("users").doc("courier-1").update({ status: "suspended" }));
  });

  it("denies an admin changing anything but status", async () => {
    await assertFails(as("admin-1").collection("users").doc("customer-1").update({ role: "admin" }));
    await assertFails(as("admin-1").collection("users").doc("customer-1").update({ displayName: "Renamed" }));
  });

  it("denies an admin setting an unknown status value", async () => {
    await assertFails(as("admin-1").collection("users").doc("merchant-1").update({ status: "banned" }));
  });

  it("denies non-admins changing anyone's status", async () => {
    await assertFails(as("courier-1").collection("users").doc("merchant-1").update({ status: "active" }));
    await assertFails(as("customer-1").collection("users").doc("customer-1").update({ status: "suspended" }));
  });
});

describe("users/{uid} reads", () => {
  it("lets a user read their own document", async () => {
    await assertSucceeds(as("customer-1").collection("users").doc("customer-1").get());
  });

  it("denies reading another user's document", async () => {
    await assertFails(as("customer-1").collection("users").doc("merchant-1").get());
  });

  it("lets an admin read any user document and list pending accounts", async () => {
    await assertSucceeds(as("admin-1").collection("users").doc("merchant-1").get());
    await assertSucceeds(as("admin-1").collection("users").where("status", "==", "pending").orderBy("createdAt").get());
  });

  it("denies signed-out reads and a non-admin listing pending accounts", async () => {
    await assertFails(env.unauthenticatedContext().firestore().collection("users").doc("customer-1").get());
    await assertFails(as("customer-1").collection("users").where("status", "==", "pending").get());
  });
});
