import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { useCatalogEnv } from "./catalog-support";
import { seedCourier, seedReadyOrder, seedServing } from "./dispatch-support";
import { liveFix, seedLiveFix } from "./live-support";

const { as, signedOut, admin } = useCatalogEnv();

const live = (asUid: string, orderId = "o1") => as(asUid).collection("liveLocations").doc(orderId);
const stored = async (orderId = "o1") => (await admin((db) => db.collection("liveLocations").doc(orderId).get())).data();

describe("liveLocations/{orderId} contract with the Android adapter", () => {
  // backend/contracts/live-location.json is also read by the Kotlin LiveLocationDocumentsTest.
  const contract: { fields: string[] } = JSON.parse(readFileSync("contracts/live-location.json", "utf8"));

  it("accepts exactly the fields of the shared fixture", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    expect(Object.keys(liveFix()).sort()).toEqual([...contract.fields].sort());
    await assertSucceeds(live("courier-1").set(liveFix()));
    await assertFails(live("courier-1", "o1").update({ ...liveFix(), extra: 1 }));
  });
});

describe("liveLocations/{orderId} writes", () => {
  it("lets the assigned courier publish while the order is claimed", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await assertSucceeds(live("courier-1").set(liveFix()));
    expect(await stored()).toMatchObject({ courierId: "courier-1", lat: 12.27, lng: -86.57, accuracyM: 8 });
    expect((await stored())?.updatedAt).toBeDefined();
  });

  it("lets the assigned courier publish while the order is picked up", async () => {
    await seedServing(admin, "courier-1", "o1", "picked_up");
    await assertSucceeds(live("courier-1").set(liveFix()));
    expect(await stored()).toMatchObject({ courierId: "courier-1" });
  });

  it("refuses a position before the order is claimed", async () => {
    await seedCourier(admin, "courier-1", { isOnline: true });
    await seedReadyOrder(admin, "o1");
    await assertFails(live("courier-1").set(liveFix()));
    expect(await stored()).toBeUndefined();
  });

  it("refuses every state outside claimed and picked_up", async () => {
    await seedCourier(admin, "courier-1", { isOnline: true });
    for (const status of ["placed", "accepted", "preparing", "ready", "delivered", "rejected", "cancelled"]) {
      await seedReadyOrder(admin, `o-${status}`, { status, courierId: "courier-1" });
      await assertFails(live("courier-1", `o-${status}`).set(liveFix()));
    }
  });

  it("stops accepting positions once the order is delivered, even when the floor has passed", async () => {
    await seedServing(admin, "courier-1", "o1", "picked_up");
    await seedLiveFix(admin, "o1", 30);
    await admin((db) => db.collection("orders").doc("o1").update({ status: "delivered" }));
    await assertFails(live("courier-1").set(liveFix()));
  });

  it("refuses a courier the order is not assigned to", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await seedCourier(admin, "courier-2", { isOnline: true });
    await assertFails(live("courier-2").set(liveFix("courier-2")));
    expect(await stored()).toBeUndefined();
  });

  it("refuses everyone who is not a courier", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await assertFails(live("customer-1").set(liveFix("customer-1")));
    await assertFails(live("merchant-a").set(liveFix("merchant-a")));
    await assertFails(live("admin-1").set(liveFix("admin-1")));
    await assertFails(signedOut().collection("liveLocations").doc("o1").set(liveFix()));
  });

  it("refuses a suspended courier", async () => {
    await seedCourier(admin, "courier-1", { isOnline: true, activeOrderId: "o1" }, "suspended");
    await seedReadyOrder(admin, "o1", { status: "claimed", courierId: "courier-1" });
    await assertFails(live("courier-1").set(liveFix()));
  });

  it("refuses a position for an order that does not exist", async () => {
    await seedCourier(admin, "courier-1", { isOnline: true });
    await assertFails(live("courier-1", "ghost").set(liveFix()));
  });

  it("refuses a courierId that is not the writer", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await assertFails(live("courier-1").set(liveFix("courier-2")));
  });

  it("takes exactly courierId, lat, lng, accuracyM and updatedAt", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await assertFails(live("courier-1").set(liveFix("courier-1", { speed: 3 })));
    await assertFails(live("courier-1").set(liveFix("courier-1", { orderId: "o1" })));
    for (const field of ["courierId", "lat", "lng", "accuracyM", "updatedAt"]) {
      const without = Object.fromEntries(Object.entries(liveFix()).filter(([key]) => key !== field));
      expect(Object.keys(without)).toHaveLength(4);
      await assertFails(live("courier-1").set(without));
    }
  });

  it("takes only numeric coordinates on the globe and a non-negative accuracy", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await assertFails(live("courier-1").set(liveFix("courier-1", { lat: "12.27" })));
    await assertFails(live("courier-1").set(liveFix("courier-1", { lng: null })));
    await assertFails(live("courier-1").set(liveFix("courier-1", { accuracyM: "8" })));
    await assertFails(live("courier-1").set(liveFix("courier-1", { lat: 90.5 })));
    await assertFails(live("courier-1").set(liveFix("courier-1", { lat: -90.5 })));
    await assertFails(live("courier-1").set(liveFix("courier-1", { lng: 180.5 })));
    await assertFails(live("courier-1").set(liveFix("courier-1", { lng: -180.5 })));
    await assertFails(live("courier-1").set(liveFix("courier-1", { accuracyM: -1 })));
    // The edges of the valid range are fine.
    await assertSucceeds(live("courier-1").set(liveFix("courier-1", { lat: 90, lng: -180, accuracyM: 0 })));
  });

  it("stamps updatedAt with the server time", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await assertFails(live("courier-1").set(liveFix("courier-1", { updatedAt: new Date() })));
    await assertFails(live("courier-1").set(liveFix("courier-1", { updatedAt: new Date(0) })));
  });

  it("never lets anyone delete the document", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await seedLiveFix(admin, "o1", 30);
    await assertFails(live("courier-1").delete());
    await assertFails(live("admin-1").delete());
    expect(await stored()).toBeDefined();
  });
});

describe("liveLocations/{orderId} 5-second floor", () => {
  it("accepts the first position of an order whenever it arrives", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await assertSucceeds(live("courier-1").set(liveFix()));
  });

  it("refuses a second position less than 5 seconds after the last one", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await seedLiveFix(admin, "o1", 4);
    await assertFails(live("courier-1").set(liveFix("courier-1", { lat: 12.3 })));
    expect((await stored())?.lat).toBe(12.27);
  });

  it("refuses back to back writes through the real server clock", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await assertSucceeds(live("courier-1").set(liveFix()));
    await assertFails(live("courier-1").set(liveFix("courier-1", { lat: 12.3 })));
  });

  it("accepts a position more than 5 seconds after the last one", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await seedLiveFix(admin, "o1", 6);
    await assertSucceeds(live("courier-1").set(liveFix("courier-1", { lat: 12.3 })));
    expect((await stored())?.lat).toBe(12.3);
  });

  it("applies the floor to updates as well as to overwrites", async () => {
    await seedServing(admin, "courier-1", "o1", "claimed");
    await seedLiveFix(admin, "o1", 1);
    await assertFails(live("courier-1").update(liveFix("courier-1", { lat: 12.31 })));
    await seedLiveFix(admin, "o1", 20);
    await assertSucceeds(live("courier-1").update(liveFix("courier-1", { lat: 12.31 })));
  });

  it("starts a new floor for the courier that takes over a released order", async () => {
    await seedServing(admin, "courier-2", "o1", "claimed");
    await seedLiveFix(admin, "o1", 60, "courier-1");
    await assertSucceeds(live("courier-2").set(liveFix("courier-2")));
    expect(await stored()).toMatchObject({ courierId: "courier-2" });
  });
});

describe("liveLocations/{orderId} reads", () => {
  const seedPublishing = async () => {
    await seedServing(admin, "courier-1", "o1", "picked_up");
    await seedLiveFix(admin, "o1", 3);
  };

  it("lets the order's own customer read the courier position", async () => {
    await seedPublishing();
    const snapshot = await assertSucceeds(live("customer-1").get());
    expect(snapshot.data()).toMatchObject({ courierId: "courier-1", lat: 12.27, lng: -86.57 });
  });

  it("lets the assigned courier read their own position", async () => {
    await seedPublishing();
    const snapshot = await assertSucceeds(live("courier-1").get());
    expect(snapshot.data()).toMatchObject({ courierId: "courier-1" });
  });

  it("lets Admin read it for oversight", async () => {
    await seedPublishing();
    const snapshot = await assertSucceeds(live("admin-1").get());
    expect(snapshot.data()).toMatchObject({ courierId: "courier-1" });
  });

  it("hides it from a different customer", async () => {
    await seedPublishing();
    await assertFails(live("customer-2").get());
  });

  it("hides it from the merchant that prepared the order", async () => {
    await seedPublishing();
    await assertFails(live("merchant-a").get());
  });

  it("hides it from a courier the order is not assigned to", async () => {
    await seedPublishing();
    await seedCourier(admin, "courier-2", { isOnline: true });
    await assertFails(live("courier-2").get());
  });

  it("hides it from signed-out clients", async () => {
    await seedPublishing();
    await assertFails(signedOut().collection("liveLocations").doc("o1").get());
  });

  it("hides it from a suspended admin account", async () => {
    await seedPublishing();
    await admin((db) => db.collection("users").doc("admin-1").update({ status: "suspended" }));
    await assertFails(live("admin-1").get());
  });

  it("denies reads of a position whose order does not exist", async () => {
    await admin((db) => db.collection("liveLocations").doc("ghost").set(liveFix()));
    await assertFails(live("customer-1", "ghost").get());
  });

  it("stops the previous courier from reading once the order was released and reassigned", async () => {
    await seedPublishing();
    await admin((db) => db.collection("orders").doc("o1").update({ courierId: "courier-2" }));
    await assertFails(live("courier-1").get());
    await assertSucceeds(live("customer-1").get());
  });

  it("never lets a client list the collection", async () => {
    await seedPublishing();
    await assertFails(as("admin-1").collection("liveLocations").get());
    await assertFails(as("customer-1").collection("liveLocations").get());
  });
});
