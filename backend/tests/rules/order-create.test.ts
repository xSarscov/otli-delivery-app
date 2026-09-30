import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { DELIVERY_FEE_CENTS, useCatalogEnv } from "./catalog-support";
import { itemsOf, ORDER_ITEM, orderDoc } from "./order-support";

const { as, signedOut, admin } = useCatalogEnv();

const place = (uid: string, id: string, overrides: Record<string, unknown> = {}) =>
  as(uid).collection("orders").doc(id).set(orderDoc(overrides));

describe("orders/{id} create", () => {
  it("lets an active customer place an order with the snapshotted fee and matching totals", async () => {
    await assertSucceeds(place("customer-1", "o1"));
    const stored = await admin((db) => db.collection("orders").doc("o1").get());
    expect(stored.data()).toMatchObject({
      customerId: "customer-1",
      merchantId: "merchant-a",
      subtotalCents: 18_000,
      deliveryFeeCents: 3000,
      totalCents: 21_000,
      status: "placed",
      courierId: null,
    });
  });

  it("accepts the lowest and highest allowed subtotals as long as the total adds up", async () => {
    await assertSucceeds(place("customer-1", "min", { subtotalCents: 1, totalCents: 1 + DELIVERY_FEE_CENTS }));
    await assertSucceeds(place("customer-1", "max", { subtotalCents: 99_999_999, totalCents: 99_999_999 + DELIVERY_FEE_CENTS }));
  });

  describe("fee snapshot and totals", () => {
    it("denies a fee that differs from settings/app", async () => {
      await assertFails(place("customer-1", "cheap", { deliveryFeeCents: 2000, totalCents: 20_000 }));
      await assertFails(place("customer-1", "free", { deliveryFeeCents: 0, totalCents: 18_000 }));
      await assertFails(place("customer-1", "dear", { deliveryFeeCents: 4000, totalCents: 22_000 }));
    });

    it("denies a total that is not subtotal plus fee", async () => {
      await assertFails(place("customer-1", "over", { totalCents: 21_001 }));
      await assertFails(place("customer-1", "under", { totalCents: 20_999 }));
      await assertFails(place("customer-1", "no-fee", { totalCents: 18_000 }));
    });

    it("denies a subtotal that is zero, negative or not an integer", async () => {
      await assertFails(place("customer-1", "zero", { subtotalCents: 0, totalCents: 3000 }));
      await assertFails(place("customer-1", "negative", { subtotalCents: -500, totalCents: 2500 }));
      await assertFails(place("customer-1", "float", { subtotalCents: 18_000.5, totalCents: 21_000.5 }));
      await assertFails(place("customer-1", "text", { subtotalCents: "18000", totalCents: 21_000 }));
    });

    it("follows a later fee change for new orders only", async () => {
      await assertSucceeds(place("customer-1", "before"));

      await admin((db) => db.collection("settings").doc("app").update({ deliveryFeeCents: 4500 }));

      await assertFails(place("customer-1", "stale-fee"));
      await assertSucceeds(place("customer-1", "after", { deliveryFeeCents: 4500, totalCents: 22_500 }));
      const before = await admin((db) => db.collection("orders").doc("before").get());
      expect(before.data()?.deliveryFeeCents).toBe(3000);
      expect(before.data()?.totalCents).toBe(21_000);
    });

    it("denies placing while settings/app does not exist", async () => {
      await admin((db) => db.collection("settings").doc("app").delete());
      await assertFails(place("customer-1", "no-settings"));
    });
  });

  describe("store availability", () => {
    it("denies an order to a closed store", async () => {
      await assertFails(place("customer-1", "closed", { merchantId: "merchant-b" }));
    });

    it("denies an order to an open store whose account is not active", async () => {
      await admin((db) => db.collection("merchants").doc("merchant-a").update({ status: "suspended" }));
      await assertFails(place("customer-1", "suspended-store"));
      await admin((db) => db.collection("merchants").doc("merchant-a").update({ status: "pending" }));
      await assertFails(place("customer-1", "pending-store"));
    });

    it("denies an order to a merchant that does not exist", async () => {
      await assertFails(place("customer-1", "ghost", { merchantId: "nobody" }));
    });

    it("allows the order again once the store reopens", async () => {
      await assertFails(place("customer-1", "closed", { merchantId: "merchant-b" }));
      await admin((db) => db.collection("merchants").doc("merchant-b").update({ isOpen: true }));
      await assertSucceeds(place("customer-1", "reopened", { merchantId: "merchant-b" }));
    });
  });

  describe("items and payment", () => {
    it("allows between 1 and 30 line items", async () => {
      await assertSucceeds(place("customer-1", "one", { items: [ORDER_ITEM] }));
      await assertSucceeds(place("customer-1", "thirty", { items: itemsOf(30) }));
    });

    it("denies no items, more than 30, or a non-list", async () => {
      await assertFails(place("customer-1", "none", { items: [] }));
      await assertFails(place("customer-1", "too-many", { items: itemsOf(31) }));
      await assertFails(place("customer-1", "not-a-list", { items: ORDER_ITEM }));
    });

    it("denies any payment method other than cash", async () => {
      await assertFails(place("customer-1", "card", { paymentMethod: "card" }));
      await assertFails(place("customer-1", "blank", { paymentMethod: "" }));
    });
  });

  describe("ownership and initial state", () => {
    it("denies placing an order in another customer's name", async () => {
      await assertFails(place("customer-2", "spoof-a", { customerId: "customer-1" }));
      await assertFails(place("customer-1", "spoof-b", { customerId: "customer-2" }));
    });

    it("denies every actor that is not an active customer", async () => {
      await assertFails(place("merchant-a", "by-merchant", { customerId: "merchant-a" }));
      await assertFails(place("admin-1", "by-admin", { customerId: "admin-1" }));
      await assertFails(place("customer-pending", "by-pending", { customerId: "customer-pending" }));
      await assertFails(place("customer-suspended", "by-suspended", { customerId: "customer-suspended" }));
      await assertFails(signedOut().collection("orders").doc("anon").set(orderDoc()));
    });

    it("denies an order that does not start as placed and unassigned", async () => {
      await assertFails(place("customer-1", "accepted", { status: "accepted" }));
      await assertFails(place("customer-1", "delivered", { status: "delivered" }));
      await assertFails(place("customer-1", "assigned", { courierId: "courier-1" }));
    });

    it("requires server timestamps for createdAt and updatedAt", async () => {
      await assertFails(place("customer-1", "client-created", { createdAt: new Date() }));
      await assertFails(place("customer-1", "client-updated", { updatedAt: new Date() }));
    });
  });

  describe("shape", () => {
    it("denies unknown fields and missing required fields", async () => {
      await assertFails(place("customer-1", "extra", { rejectReason: "x" }));
      await assertFails(place("customer-1", "extra-2", { tip: 500 }));
      const { dropoff: _dropoff, ...withoutDropoff } = orderDoc();
      await assertFails(as("customer-1").collection("orders").doc("no-dropoff").set(withoutDropoff));
      const { courierId: _courierId, ...withoutCourier } = orderDoc();
      await assertFails(as("customer-1").collection("orders").doc("no-courier").set(withoutCourier));
    });

    it("denies a drop-off pin that is malformed or out of range", async () => {
      await assertFails(place("customer-1", "lat", { dropoff: { lat: 95, lng: -86.5, reference: "x" } }));
      await assertFails(place("customer-1", "lng", { dropoff: { lat: 12.2, lng: -190, reference: "x" } }));
      await assertFails(place("customer-1", "no-ref", { dropoff: { lat: 12.2, lng: -86.5 } }));
      await assertFails(place("customer-1", "string", { dropoff: "12.2,-86.5" }));
    });

    it("denies blank customer and merchant snapshots", async () => {
      await assertFails(place("customer-1", "blank-name", { customerName: "  " }));
      await assertFails(place("customer-1", "blank-phone", { customerPhone: "" }));
      await assertFails(place("customer-1", "blank-store", { merchantName: "" }));
    });
  });
});

describe("settings/app", () => {
  it("is readable by any signed-in user and not by signed-out users", async () => {
    const read = (db: ReturnType<typeof as>) => db.collection("settings").doc("app").get();
    await assertSucceeds(read(as("customer-1")));
    await assertSucceeds(read(as("merchant-a")));
    await assertFails(read(signedOut() as ReturnType<typeof as>));
  });

  it("cannot be written by the client yet, not even by an admin (the fee editor arrives in Slice 6)", async () => {
    await assertFails(as("admin-1").collection("settings").doc("app").update({ deliveryFeeCents: 1 }));
    await assertFails(as("customer-1").collection("settings").doc("app").update({ deliveryFeeCents: 0 }));
  });
});
