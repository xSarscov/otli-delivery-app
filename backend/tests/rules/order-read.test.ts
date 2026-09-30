import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { useCatalogEnv } from "./catalog-support";
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

  it("denies a courier for now (pool and assigned-order reads arrive with the dispatch rules) and signed-out users", async () => {
    await seedOrders();
    await assertFails(read("courier-1", "o1"));
    await assertFails(signedOut().collection("orders").doc("o1").get());
  });
});
