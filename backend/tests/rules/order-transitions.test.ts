import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { serverTime, useCatalogEnv } from "./catalog-support";
import { orderDoc } from "./order-support";

const { as, admin } = useCatalogEnv();

type Triple = { from: string; to: string; actor: string };

/** The contract shared with the Kotlin `OrderTransitions` table (ADR-14). */
const contract: Triple[] = JSON.parse(readFileSync("contracts/order-transitions.json", "utf8")).allowed;

const STATUSES = ["placed", "accepted", "preparing", "ready", "claimed", "picked_up", "delivered", "rejected", "cancelled"];
const ACTORS = ["customer", "merchant", "courier", "admin"];

/**
 * Actors whose transitions the rules implement so far. The admin release arrives in Slice 6: until
 * then every attempt by an admin must be denied, and this set grows with each slice.
 */
const IMPLEMENTED_ACTORS = new Set(["customer", "merchant", "courier"]);

/** Courier steps of the contract whose rules a later slice work unit adds: still denied until then. */
const NOT_YET = new Set(["claimed>picked_up>courier", "picked_up>delivered>courier"]);

const UID: Record<string, string> = { customer: "customer-1", merchant: "merchant-a", courier: "courier-1", admin: "admin-1" };
const TIMESTAMP_FIELD: Record<string, string> = {
  accepted: "acceptedAt",
  preparing: "preparingAt",
  ready: "readyAt",
  claimed: "claimedAt",
  picked_up: "pickedUpAt",
  delivered: "deliveredAt",
  rejected: "rejectedAt",
  cancelled: "cancelledAt",
};

/**
 * Seeds an order of customer-1 at merchant-a in [status], bypassing the rules, and the online courier-1
 * in the state that goes with it: serving the order while it is claimed or picked up, free otherwise.
 */
const seed = (id: string, status: string, overrides: Record<string, unknown> = {}) =>
  admin(async (db) => {
    const assigned = ["claimed", "picked_up", "delivered"].includes(status);
    await db.collection("orders").doc(id).set(
      orderDoc({ status, courierId: assigned ? "courier-1" : null, createdAt: new Date(), updatedAt: new Date(), ...overrides }),
    );
    const serving = ["claimed", "picked_up"].includes(status);
    await db.collection("couriers").doc("courier-1").set({ isOnline: true, activeOrderId: serving ? id : null, updatedAt: new Date() });
  });

/** The most complete update an actor could send to move to [to]: denial of a combination is then about the transition itself. */
function updateTo(to: string, actor: string, overrides: Record<string, unknown> = {}): Record<string, unknown> {
  const extra: Record<string, unknown> = {};
  if (to === "rejected") extra.rejectReason = "Out of stock";
  if (to === "cancelled") extra.cancelledBy = actor === "admin" ? "admin" : "customer";
  if (to === "claimed") extra.courierId = UID[actor];
  const stamp = TIMESTAMP_FIELD[to];
  return { status: to, ...(stamp ? { [stamp]: serverTime() } : {}), ...extra, updatedAt: serverTime(), ...overrides };
}

const orderOf = (uid: string, id: string) => as(uid).collection("orders").doc(id);

/** The contract triples the rules implement so far. */
const implemented = contract.filter((t) => IMPLEMENTED_ACTORS.has(t.actor) && !NOT_YET.has(`${t.from}>${t.to}>${t.actor}`));

/** Sends [payload] as [actor]. A courier's claim carries the paired write on couriers/{uid}, as the app's transaction does. */
function send(actor: string, id: string, to: string, payload: Record<string, unknown>) {
  const db = as(UID[actor]);
  const ref = db.collection("orders").doc(id);
  if (actor !== "courier" || to !== "claimed") return ref.update(payload);
  const batch = db.batch();
  batch.update(ref, payload);
  batch.update(db.collection("couriers").doc(UID.courier), { activeOrderId: id, updatedAt: serverTime() });
  return batch.commit();
}

describe("the transitions contract", () => {
  it("lists the nine allowed triples of the spec", () => {
    expect(contract).toHaveLength(9);
  });

  it("allows every implemented triple and denies every other (from, to, actor) combination", async () => {
    const allowed = new Set(implemented.map((t) => `${t.from}>${t.to}>${t.actor}`));
    expect(allowed.size).toBeGreaterThan(0);

    const wrong: string[] = [];
    let attempts = 0;
    for (const from of STATUSES) {
      for (const to of STATUSES) {
        for (const actor of ACTORS) {
          const key = `${from}>${to}>${actor}`;
          const id = `o-${key}`;
          await seed(id, from);
          let ok: boolean;
          try {
            await assertSucceeds(send(actor, id, to, updateTo(to, actor)));
            ok = true;
          } catch {
            ok = false;
          }
          attempts++;
          if (ok !== allowed.has(key)) wrong.push(`${key}: ${ok ? "allowed" : "denied"}`);
        }
      }
    }
    expect(attempts).toBe(STATUSES.length * STATUSES.length * ACTORS.length);
    expect(wrong).toEqual([]);
  }, 180_000);
});

describe("every implemented transition touches only its own fields", () => {
  it.each(implemented.map((t) => [`${t.from} to ${t.to} by ${t.actor}`, t] as const))("%s", async (_name, t) => {
    const attempt = async (id: string, overrides: Record<string, unknown>, omit: string[] = []) => {
      await seed(id, t.from);
      const payload: Record<string, unknown> = { ...updateTo(t.to, t.actor), ...overrides };
      for (const key of omit) delete payload[key];
      return send(t.actor, id, t.to, payload);
    };
    const stamp = TIMESTAMP_FIELD[t.to];

    await assertSucceeds(attempt("ok", {}));
    await assertFails(attempt("extra-total", { totalCents: 1 }));
    await assertFails(attempt("extra-courier", { courierId: "courier-2" }));
    await assertFails(attempt("extra-customer", { customerName: "Someone else" }));
    await assertFails(attempt("no-stamp", {}, [stamp]));
    await assertFails(attempt("client-stamp", { [stamp]: new Date() }));
    await assertFails(attempt("client-updated", { updatedAt: new Date() }));
    await assertFails(attempt("no-updated", {}, ["updatedAt"]));
  });
});

describe("merchant steps (accept, prepare, ready)", () => {
  it("lets the owning merchant walk an order through the kitchen", async () => {
    await seed("o1", "placed");
    await assertSucceeds(orderOf("merchant-a", "o1").update(updateTo("accepted", "merchant")));
    await assertSucceeds(orderOf("merchant-a", "o1").update(updateTo("preparing", "merchant")));
    await assertSucceeds(orderOf("merchant-a", "o1").update(updateTo("ready", "merchant")));
    const stored = await admin((db) => db.collection("orders").doc("o1").get());
    expect(stored.data()?.status).toBe("ready");
    expect(stored.data()?.readyAt).toBeDefined();
  });

  it("denies skipping a step", async () => {
    await seed("o1", "placed");
    await assertFails(orderOf("merchant-a", "o1").update(updateTo("ready", "merchant")));
    await assertFails(orderOf("merchant-a", "o1").update(updateTo("preparing", "merchant")));
  });

  it("denies a merchant that does not own the order", async () => {
    await seed("o1", "placed");
    await assertFails(orderOf("merchant-b", "o1").update(updateTo("accepted", "merchant")));
  });

  it("denies a user whose uid is on the order but whose account is not a merchant", async () => {
    await seed("o1", "placed", { merchantId: "courier-1" });
    await assertFails(orderOf("courier-1", "o1").update(updateTo("accepted", "merchant")));
    await assertFails(orderOf("courier-1", "o1").update(updateTo("rejected", "merchant")));
  });

  it("denies a merchant that is no longer active", async () => {
    await seed("o1", "placed");
    await admin((db) => db.collection("users").doc("merchant-a").update({ status: "suspended" }));
    await assertFails(orderOf("merchant-a", "o1").update(updateTo("accepted", "merchant")));
  });

  it("denies an update that keeps the status and so moves nothing", async () => {
    await seed("o1", "accepted");
    await assertFails(orderOf("merchant-a", "o1").update({ updatedAt: serverTime() }));
  });
});

describe("merchant rejection", () => {
  it("lets the owning merchant reject a placed order with a reason", async () => {
    await seed("o1", "placed");
    await assertSucceeds(orderOf("merchant-a", "o1").update(updateTo("rejected", "merchant", { rejectReason: "We ran out of gas" })));
    const stored = await admin((db) => db.collection("orders").doc("o1").get());
    expect(stored.data()).toMatchObject({ status: "rejected", rejectReason: "We ran out of gas" });
  });

  it("denies rejecting without a reason, with a blank one, or with a non-text one", async () => {
    await seed("o1", "placed");
    const { rejectReason: _reason, ...withoutReason } = updateTo("rejected", "merchant");
    await assertFails(orderOf("merchant-a", "o1").update(withoutReason));
    await assertFails(orderOf("merchant-a", "o1").update(updateTo("rejected", "merchant", { rejectReason: "" })));
    await assertFails(orderOf("merchant-a", "o1").update(updateTo("rejected", "merchant", { rejectReason: "   " })));
    await assertFails(orderOf("merchant-a", "o1").update(updateTo("rejected", "merchant", { rejectReason: 42 })));
  });

  it("limits the reason to 200 characters", async () => {
    await seed("ok", "placed");
    await seed("long", "placed");
    await assertSucceeds(orderOf("merchant-a", "ok").update(updateTo("rejected", "merchant", { rejectReason: "x".repeat(200) })));
    await assertFails(orderOf("merchant-a", "long").update(updateTo("rejected", "merchant", { rejectReason: "x".repeat(201) })));
  });

  it("denies rejecting an order that was already accepted", async () => {
    await seed("o1", "accepted");
    await assertFails(orderOf("merchant-a", "o1").update(updateTo("rejected", "merchant")));
  });

  it("denies another merchant rejecting the order", async () => {
    await seed("o1", "placed");
    await assertFails(orderOf("merchant-b", "o1").update(updateTo("rejected", "merchant")));
  });

  it("leaves a rejected order final", async () => {
    await seed("o1", "rejected", { rejectReason: "No stock" });
    await assertFails(orderOf("merchant-a", "o1").update(updateTo("accepted", "merchant")));
    await assertFails(orderOf("merchant-a", "o1").update({ rejectReason: "Changed my mind", updatedAt: serverTime() }));
  });
});

describe("customer cancellation", () => {
  it("lets the owning customer cancel while the order is placed", async () => {
    await seed("o1", "placed");
    await assertSucceeds(orderOf("customer-1", "o1").update(updateTo("cancelled", "customer")));
    const stored = await admin((db) => db.collection("orders").doc("o1").get());
    expect(stored.data()).toMatchObject({ status: "cancelled", cancelledBy: "customer" });
  });

  it("denies cancelling once the order was accepted or later", async () => {
    for (const status of ["accepted", "preparing", "ready", "claimed", "picked_up"]) {
      await seed(`o-${status}`, status);
      await assertFails(orderOf("customer-1", `o-${status}`).update(updateTo("cancelled", "customer")));
    }
  });

  it("denies a different customer cancelling the order", async () => {
    await seed("o1", "placed");
    await assertFails(orderOf("customer-2", "o1").update(updateTo("cancelled", "customer")));
  });

  it("requires cancelledBy to say customer", async () => {
    await seed("o1", "placed");
    await assertFails(orderOf("customer-1", "o1").update(updateTo("cancelled", "customer", { cancelledBy: "admin" })));
    const { cancelledBy: _by, ...withoutBy } = updateTo("cancelled", "customer");
    await assertFails(orderOf("customer-1", "o1").update(withoutBy));
  });

  it("denies a user whose uid is on the order but whose account is not a customer", async () => {
    await seed("o1", "placed", { customerId: "merchant-a" });
    await assertFails(orderOf("merchant-a", "o1").update(updateTo("cancelled", "customer")));
  });

  it("denies a customer that is no longer active", async () => {
    await seed("o1", "placed");
    await admin((db) => db.collection("users").doc("customer-1").update({ status: "suspended" }));
    await assertFails(orderOf("customer-1", "o1").update(updateTo("cancelled", "customer")));
  });

  it("denies the customer editing their order any other way", async () => {
    await seed("o1", "placed");
    await assertFails(orderOf("customer-1", "o1").update({ dropoff: { lat: 12, lng: -86, reference: "Elsewhere" }, updatedAt: serverTime() }));
    await assertFails(orderOf("customer-1", "o1").update(updateTo("cancelled", "customer", { totalCents: 1 })));
  });

  it("leaves a cancelled order final", async () => {
    await seed("o1", "cancelled", { cancelledBy: "customer" });
    await assertFails(orderOf("merchant-a", "o1").update(updateTo("accepted", "merchant")));
  });
});

describe("deleting orders", () => {
  it("is denied for everyone, including the owners and the admin", async () => {
    await seed("o1", "placed");
    for (const uid of ["customer-1", "merchant-a", "admin-1"]) {
      await assertFails(orderOf(uid, "o1").delete());
    }
  });
});
