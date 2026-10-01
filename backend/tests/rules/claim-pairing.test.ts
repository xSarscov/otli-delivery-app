import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { useCatalogEnv } from "./catalog-support";
import { claim, claimOrder, claimSlot, seedCourier, seedFreeCourier, seedReadyOrder } from "./dispatch-support";

const { as, admin } = useCatalogEnv();

const order = async (id: string) => (await admin((db) => db.collection("orders").doc(id).get())).data();
const courier = async (uid: string) => (await admin((db) => db.collection("couriers").doc(uid).get())).data();

describe("claiming a ready order", () => {
  it("writes the order and the courier slot together, as a batch", async () => {
    await seedReadyOrder(admin, "o1");
    await seedFreeCourier(admin, "courier-1");
    await assertSucceeds(claim(as("courier-1"), "courier-1", "o1"));
    expect(await order("o1")).toMatchObject({ status: "claimed", courierId: "courier-1" });
    expect((await order("o1"))?.claimedAt).toBeDefined();
    expect(await courier("courier-1")).toMatchObject({ isOnline: true, activeOrderId: "o1" });
  });

  it("works as a real transaction that reads both documents first", async () => {
    await seedReadyOrder(admin, "o1");
    await seedFreeCourier(admin, "courier-1");
    const db = as("courier-1");
    const orderRef = db.collection("orders").doc("o1");
    const courierRef = db.collection("couriers").doc("courier-1");
    await assertSucceeds(
      db.runTransaction(async (tx) => {
        const [o, c] = await Promise.all([tx.get(orderRef), tx.get(courierRef)]);
        expect(o.data()?.status).toBe("ready");
        expect(c.data()?.activeOrderId).toBeNull();
        tx.update(orderRef, claimOrder("courier-1"));
        tx.update(courierRef, claimSlot("o1"));
      }),
    );
    expect(await order("o1")).toMatchObject({ status: "claimed", courierId: "courier-1" });
  });

  it("denies an order that is not ready", async () => {
    await seedFreeCourier(admin, "courier-1");
    for (const status of ["placed", "accepted", "preparing", "rejected", "cancelled"]) {
      await seedReadyOrder(admin, `o-${status}`, { status });
      await assertFails(claim(as("courier-1"), "courier-1", `o-${status}`));
    }
    expect((await courier("courier-1"))?.activeOrderId).toBeNull();
  });

  it("fails cleanly when another courier already holds the order, leaving it untouched", async () => {
    await seedReadyOrder(admin, "o1", { status: "claimed", courierId: "courier-2" });
    await seedFreeCourier(admin, "courier-1");
    await assertFails(claim(as("courier-1"), "courier-1", "o1"));
    expect(await order("o1")).toMatchObject({ status: "claimed", courierId: "courier-2" });
    expect((await courier("courier-1"))?.activeOrderId).toBeNull();
  });

  it("denies a ready order that already names a courier", async () => {
    await seedReadyOrder(admin, "o1", { courierId: "courier-2" });
    await seedFreeCourier(admin, "courier-1");
    await assertFails(claim(as("courier-1"), "courier-1", "o1"));
    expect((await order("o1"))?.courierId).toBe("courier-2");
  });

  it("denies claiming two orders in one write, whichever one the slot names", async () => {
    await seedReadyOrder(admin, "o1");
    await seedReadyOrder(admin, "o2");
    await seedFreeCourier(admin, "courier-1");
    const db = as("courier-1");
    const batch = db.batch();
    batch.update(db.collection("orders").doc("o1"), claimOrder("courier-1"));
    batch.update(db.collection("orders").doc("o2"), claimOrder("courier-1"));
    batch.update(db.collection("couriers").doc("courier-1"), claimSlot("o2"));
    await assertFails(batch.commit());
    expect(await order("o1")).toMatchObject({ status: "ready", courierId: null });
    expect(await order("o2")).toMatchObject({ status: "ready", courierId: null });
  });

  it("denies a courier that already has an active order and leaves the target ready", async () => {
    await seedReadyOrder(admin, "o1");
    await seedCourier(admin, "courier-1", { isOnline: true, activeOrderId: "other-order" });
    await assertFails(claim(as("courier-1"), "courier-1", "o1"));
    expect(await order("o1")).toMatchObject({ status: "ready", courierId: null });
    expect((await courier("courier-1"))?.activeOrderId).toBe("other-order");
  });

  it("denies an offline courier", async () => {
    await seedReadyOrder(admin, "o1");
    await seedCourier(admin, "courier-1", { isOnline: false });
    await assertFails(claim(as("courier-1"), "courier-1", "o1"));
  });

  it("denies a courier whose account is pending or suspended", async () => {
    await seedReadyOrder(admin, "o1");
    await seedCourier(admin, "courier-pending", { isOnline: true }, "pending");
    await seedCourier(admin, "courier-suspended", { isOnline: true }, "suspended");
    await assertFails(claim(as("courier-pending"), "courier-pending", "o1"));
    await assertFails(claim(as("courier-suspended"), "courier-suspended", "o1"));
  });

  it("denies a customer, a merchant and an admin even with a courier document", async () => {
    await seedReadyOrder(admin, "o1");
    for (const uid of ["customer-1", "merchant-a", "admin-1"]) {
      await admin((db) => db.collection("couriers").doc(uid).set({ isOnline: true, activeOrderId: null, updatedAt: new Date() }));
      await assertFails(claim(as(uid), uid, "o1"));
    }
  });

  it("denies assigning the order to another courier or to nobody", async () => {
    await seedReadyOrder(admin, "o1");
    await seedFreeCourier(admin, "courier-1");
    await seedFreeCourier(admin, "courier-2");
    await assertFails(claim(as("courier-1"), "courier-1", "o1", { courierId: "courier-2" }));
    await assertFails(claim(as("courier-1"), "courier-1", "o1", { courierId: null }));
  });

  it("denies a slot that points at another order, or at nothing", async () => {
    await seedReadyOrder(admin, "o1");
    await seedReadyOrder(admin, "o2");
    await seedFreeCourier(admin, "courier-1");
    await assertFails(claim(as("courier-1"), "courier-1", "o1", {}, { activeOrderId: "o2" }));
    await assertFails(claim(as("courier-1"), "courier-1", "o1", {}, { activeOrderId: null }));
  });

  it("denies the order half alone", async () => {
    await seedReadyOrder(admin, "o1");
    await seedFreeCourier(admin, "courier-1");
    await assertFails(as("courier-1").collection("orders").doc("o1").update(claimOrder("courier-1")));
    expect(await order("o1")).toMatchObject({ status: "ready", courierId: null });
  });

  it("denies the courier half alone, even naming a ready order", async () => {
    await seedReadyOrder(admin, "o1");
    await seedFreeCourier(admin, "courier-1");
    await assertFails(as("courier-1").collection("couriers").doc("courier-1").update(claimSlot("o1")));
    expect((await courier("courier-1"))?.activeOrderId).toBeNull();
  });

  it("denies a courier half for an order that was claimed before, even by this same courier", async () => {
    await seedReadyOrder(admin, "o1", { status: "claimed", courierId: "courier-1" });
    await seedFreeCourier(admin, "courier-1");
    await assertFails(as("courier-1").collection("couriers").doc("courier-1").update(claimSlot("o1")));
    expect((await courier("courier-1"))?.activeOrderId).toBeNull();
  });

  it("denies a claim that also changes anything else on either document", async () => {
    await seedReadyOrder(admin, "o1");
    await seedReadyOrder(admin, "o2");
    await seedReadyOrder(admin, "o3");
    await seedFreeCourier(admin, "courier-1");
    await assertFails(claim(as("courier-1"), "courier-1", "o1", { totalCents: 1 }));
    await assertFails(claim(as("courier-1"), "courier-1", "o2", { pickedUpAt: new Date() }));
    await assertFails(claim(as("courier-1"), "courier-1", "o3", {}, { isOnline: false }));
  });

  it("stamps claimedAt and updatedAt with the server time on both documents", async () => {
    await seedReadyOrder(admin, "o1");
    await seedReadyOrder(admin, "o2");
    await seedReadyOrder(admin, "o3");
    await seedFreeCourier(admin, "courier-1");
    await assertFails(claim(as("courier-1"), "courier-1", "o1", { claimedAt: new Date() }));
    await assertFails(claim(as("courier-1"), "courier-1", "o2", { updatedAt: new Date() }));
    await assertFails(claim(as("courier-1"), "courier-1", "o3", {}, { updatedAt: new Date() }));
  });

  it("denies a direct write to an order that is already claimed", async () => {
    await seedReadyOrder(admin, "o1", { status: "claimed", courierId: "courier-2" });
    await seedFreeCourier(admin, "courier-1");
    await assertFails(as("courier-1").collection("orders").doc("o1").update({ courierId: "courier-1", updatedAt: claimSlot(null).updatedAt }));
    await assertFails(as("courier-1").collection("orders").doc("o1").update(claimOrder("courier-1")));
    expect((await order("o1"))?.courierId).toBe("courier-2");
  });

  it("lets only one of two orders be claimed by a courier at a time", async () => {
    await seedReadyOrder(admin, "o1");
    await seedReadyOrder(admin, "o2");
    await seedFreeCourier(admin, "courier-1");
    await assertSucceeds(claim(as("courier-1"), "courier-1", "o1"));
    await assertFails(claim(as("courier-1"), "courier-1", "o2"));
    expect(await order("o2")).toMatchObject({ status: "ready", courierId: null });
    expect((await courier("courier-1"))?.activeOrderId).toBe("o1");
  });
});
