import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { useCatalogEnv } from "./catalog-support";
import { seedCourier, seedReadyOrder } from "./dispatch-support";
import { orderDoc } from "./order-support";

const { as, signedOut, admin } = useCatalogEnv();

/** Two orders of customer-1 at merchant-a and one of customer-2 at merchant-b, bypassing the rules. */
async function seedOrders() {
  await admin(async (db) => {
    const base = { createdAt: new Date(), updatedAt: new Date() };
    await db.collection("orders").doc("o1").set(orderDoc(base));
    await db.collection("orders").doc("o2").set(orderDoc({ ...base, status: "accepted" }));
    await db.collection("orders").doc("o3").set(orderDoc({ ...base, customerId: "customer-2", merchantId: "merchant-b" }));
  });
}

const read = (uid: string, id: string) => as(uid).collection("orders").doc(id).get();

describe("orders/{id} read", () => {
  it("lets the customer read their own orders, by id and by query", async () => {
    await seedOrders();
    await assertSucceeds(read("customer-1", "o1"));
    const mine = await assertSucceeds(as("customer-1").collection("orders").where("customerId", "==", "customer-1").get());
    expect(mine.docs.map((doc) => doc.id).sort()).toEqual(["o1", "o2"]);
  });

  it("lets the merchant read the orders addressed to them, by id and by query", async () => {
    await seedOrders();
    await assertSucceeds(read("merchant-a", "o2"));
    const incoming = await assertSucceeds(as("merchant-a").collection("orders").where("merchantId", "==", "merchant-a").get());
    expect(incoming.docs.map((doc) => doc.id).sort()).toEqual(["o1", "o2"]);
  });

  it("lets an active admin read any order", async () => {
    await seedOrders();
    await assertSucceeds(read("admin-1", "o1"));
    await assertSucceeds(read("admin-1", "o3"));
  });

  it("denies another customer and another merchant", async () => {
    await seedOrders();
    await assertFails(read("customer-2", "o1"));
    await assertFails(read("merchant-b", "o1"));
    await assertFails(as("customer-2").collection("orders").where("customerId", "==", "customer-1").get());
  });

  it("denies browsing all orders without filtering on yourself", async () => {
    await seedOrders();
    await assertFails(as("customer-1").collection("orders").get());
    await assertFails(as("merchant-a").collection("orders").get());
  });

  it("denies signed-out users", async () => {
    await seedOrders();
    await assertFails(signedOut().collection("orders").doc("o1").get());
  });
});

/**
 * Couriers read the open pool (ready orders, for any active courier) and the orders assigned to them.
 * Rules cannot hide fields, so a pool reader also sees the customer name, phone and dropoff before the
 * claim: an accepted risk of the open pool (ADR-7), limited to ready orders.
 */
describe("orders/{id} read by couriers", () => {
  const poolQuery = (uid: string) => as(uid).collection("orders").where("status", "==", "ready").orderBy("readyAt").get();

  async function seedPool() {
    await seedCourier(admin, "courier-1");
    await seedCourier(admin, "courier-2");
    await seedCourier(admin, "courier-pending", {}, "pending");
    await seedCourier(admin, "courier-suspended", {}, "suspended");
    await seedReadyOrder(admin, "ready-1", { readyAt: new Date(1_000) });
    await seedReadyOrder(admin, "ready-2", { readyAt: new Date(2_000) });
    await seedReadyOrder(admin, "preparing", { status: "preparing" });
    await seedReadyOrder(admin, "mine", { status: "claimed", courierId: "courier-1" });
    await seedReadyOrder(admin, "theirs", { status: "picked_up", courierId: "courier-2" });
    await seedReadyOrder(admin, "mine-done", { status: "delivered", courierId: "courier-1" });
  }

  it("lets an active courier read the pool, oldest first, by id and by query", async () => {
    await seedPool();
    await assertSucceeds(read("courier-1", "ready-1"));
    const pool = await assertSucceeds(poolQuery("courier-2"));
    expect(pool.docs.map((doc) => doc.id)).toEqual(["ready-1", "ready-2"]);
  });

  it("lets a courier read the orders assigned to them, including finished ones", async () => {
    await seedPool();
    await assertSucceeds(read("courier-1", "mine"));
    await assertSucceeds(read("courier-1", "mine-done"));
    const mine = await assertSucceeds(as("courier-1").collection("orders").where("courierId", "==", "courier-1").get());
    expect(mine.docs.map((doc) => doc.id).sort()).toEqual(["mine", "mine-done"]);
  });

  it("denies a courier orders that are not in the pool and not theirs", async () => {
    await seedPool();
    await assertFails(read("courier-1", "preparing"));
    await assertFails(read("courier-1", "theirs"));
    await assertFails(read("courier-2", "mine"));
    await assertFails(as("courier-1").collection("orders").where("courierId", "==", "courier-2").get());
    await assertFails(as("courier-1").collection("orders").get());
  });

  it("denies the pool to couriers whose account is pending or suspended", async () => {
    await seedPool();
    await assertFails(read("courier-pending", "ready-1"));
    await assertFails(poolQuery("courier-suspended"));
  });

  it("denies the pool to an account that is not a courier or admin", async () => {
    await seedPool();
    await assertFails(read("customer-2", "ready-1"));
    await assertFails(read("merchant-b", "ready-1"));
    await assertFails(signedOut().collection("orders").doc("ready-1").get());
  });
});
