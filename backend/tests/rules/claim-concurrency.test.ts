import { assertFails } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { useCatalogEnv } from "./catalog-support";
import { claimOrder, claimSlot, seedCourier, seedReadyOrder } from "./dispatch-support";

/**
 * The mandatory proof of the race-safe claim (ADR-7): real concurrent transactions against the
 * emulator, repeated ITERATIONS times each to surface flakiness. Every iteration races fresh
 * documents, so nothing is shared between iterations but the couriers' accounts.
 */
const ITERATIONS = 20;
const LONG_TEST_MS = 240_000;

const { as, admin } = useCatalogEnv();

const racer = (index: number) => `racer-${index}`;
const racers = (count: number) => Array.from({ length: count }, (_, i) => racer(i + 1));

/**
 * The claim as the app runs it: read the order and the courier, apply the pre-conditions
 * (`ClaimPolicy`), then write the pair in one transaction.
 */
function claimWithReads(uid: string, orderId: string) {
  const db = as(uid);
  const orderRef = db.collection("orders").doc(orderId);
  const courierRef = db.collection("couriers").doc(uid);
  return db.runTransaction(async (tx) => {
    const [order, courier] = await Promise.all([tx.get(orderRef), tx.get(courierRef)]);
    if (order.data()?.status !== "ready" || order.data()?.courierId != null) throw new Error("the order is not claimable");
    if (courier.data()?.activeOrderId != null) throw new Error("the courier is busy");
    tx.update(orderRef, claimOrder(uid));
    tx.update(courierRef, claimSlot(orderId));
  });
}

/** A client that skips every check and just writes the pair: only the rules can refuse it. */
function claimBlind(uid: string, orderId: string) {
  const db = as(uid);
  return db.runTransaction(async (tx) => {
    tx.update(db.collection("orders").doc(orderId), claimOrder(uid));
    tx.update(db.collection("couriers").doc(uid), claimSlot(orderId));
  });
}

const CLAIMERS = [
  ["a client that reads and checks first", claimWithReads],
  ["a client that writes blindly", claimBlind],
] as const;

type Claimer = (typeof CLAIMERS)[number][1];

/** Seeds the accounts of [uids] once: active couriers, with their documents reset by [prepare]. */
async function registerRacers(uids: string[]) {
  for (const uid of uids) await seedCourier(admin, uid, { isOnline: true });
}

/** Online, free couriers and fresh ready orders for one iteration, in a single write. */
const prepare = (uids: string[], orderIds: string[]) =>
  admin(async (db) => {
    const batch = db.batch();
    for (const uid of uids) batch.set(db.collection("couriers").doc(uid), { isOnline: true, activeOrderId: null, updatedAt: new Date() });
    await batch.commit();
    for (const id of orderIds) await seedReadyOrder(admin, id);
  });

const orderData = async (id: string) => (await admin((db) => db.collection("orders").doc(id).get())).data();
const slotOf = async (uid: string) => (await admin((db) => db.collection("couriers").doc(uid).get())).data()?.activeOrderId;

/** Runs [body] [ITERATIONS] times, checks it ran that many times and reports the timing. */
async function looped(name: string, body: (iteration: number) => Promise<void>) {
  const started = Date.now();
  let executed = 0;
  for (let i = 1; i <= ITERATIONS; i++) {
    await body(i);
    executed++;
  }
  expect(executed).toBe(ITERATIONS);
  const elapsed = Date.now() - started;
  console.info(`[claim-concurrency] ${name}: ${ITERATIONS} iterations in ${elapsed} ms (${Math.round(elapsed / ITERATIONS)} ms each)`);
}

/** Races [uids] to claim the same order and checks exactly one won, with the pair of documents agreeing. */
async function raceForOneOrder(claimer: Claimer, uids: string[], orderId: string) {
  const results = await Promise.allSettled(uids.map((uid) => claimer(uid, orderId)));
  const winners = uids.filter((_, index) => results[index].status === "fulfilled");
  expect(winners).toHaveLength(1);
  expect(await orderData(orderId)).toMatchObject({ status: "claimed", courierId: winners[0] });
  for (const uid of uids) expect(await slotOf(uid)).toBe(uid === winners[0] ? orderId : null);
}

describe.each(CLAIMERS)("couriers racing for the same ready order, %s", (_name, claimer) => {
  it.each([2, 10])("lets exactly one of %i couriers win", async (count) => {
    const uids = racers(count);
    await registerRacers(uids);
    await looped(`${count} couriers, one order`, async (i) => {
      const orderId = `race-${i}`;
      await prepare(uids, [orderId]);
      await raceForOneOrder(claimer, uids, orderId);
    });
  }, LONG_TEST_MS);
});

describe.each(CLAIMERS)("one courier racing for two ready orders, %s", (_name, claimer) => {
  it("lets only one of the two claims succeed", async () => {
    const [uid] = racers(1);
    await registerRacers([uid]);
    await looped("one courier, two orders", async (i) => {
      const [first, second] = [`left-${i}`, `right-${i}`];
      await prepare([uid], [first, second]);
      const results = await Promise.allSettled([claimer(uid, first), claimer(uid, second)]);
      expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
      const won = results[0].status === "fulfilled" ? first : second;
      const lost = won === first ? second : first;
      expect(await orderData(won)).toMatchObject({ status: "claimed", courierId: uid });
      expect(await orderData(lost)).toMatchObject({ status: "ready", courierId: null });
      expect(await slotOf(uid)).toBe(won);
    });
  }, LONG_TEST_MS);
});

describe("writes that skip the transaction or send half a claim, under concurrent load", () => {
  it("are all denied while legitimate couriers race and exactly one wins", async () => {
    const uids = racers(8);
    const [legitA, legitB, orderOnly, slotOnly, overwrite, slotOfClaimed, secondSlot] = uids;
    await registerRacers(uids);
    await looped("direct and half writes under load", async (i) => {
      const contested = `contested-${i}`;
      const taken = `taken-${i}`;
      await prepare(uids, [contested]);
      await seedReadyOrder(admin, taken, { status: "claimed", courierId: racer(8) });
      await admin((db) => db.collection("couriers").doc(racer(8)).update({ activeOrderId: taken }));

      const orderRef = (uid: string, id: string) => as(uid).collection("orders").doc(id);
      const courierRef = (uid: string) => as(uid).collection("couriers").doc(uid);
      const attacks = [
        orderRef(orderOnly, contested).update(claimOrder(orderOnly)),
        courierRef(slotOnly).update(claimSlot(contested)),
        orderRef(overwrite, taken).update(claimOrder(overwrite)),
        courierRef(slotOfClaimed).update(claimSlot(taken)),
        courierRef(secondSlot).update(claimSlot(contested)),
      ];
      const legit = [claimWithReads(legitA, contested), claimBlind(legitB, contested)];

      const [attackResults, legitResults] = await Promise.all([Promise.allSettled(attacks), Promise.allSettled(legit)]);
      expect(attackResults.map((r) => r.status)).toEqual(attacks.map(() => "rejected"));
      expect(legitResults.filter((r) => r.status === "fulfilled")).toHaveLength(1);
      const winner = legitResults[0].status === "fulfilled" ? legitA : legitB;
      expect(await orderData(contested)).toMatchObject({ status: "claimed", courierId: winner });
      expect(await orderData(taken)).toMatchObject({ status: "claimed", courierId: racer(8) });
      for (const uid of [orderOnly, slotOnly, overwrite, slotOfClaimed, secondSlot]) expect(await slotOf(uid)).toBeNull();
      expect(await slotOf(winner)).toBe(contested);
    });
  }, LONG_TEST_MS);

  it("does not let a denied half-claim leave the order claimed", async () => {
    await registerRacers([racer(1)]);
    await prepare([racer(1)], ["alone"]);
    await assertFails(as(racer(1)).collection("orders").doc("alone").update(claimOrder(racer(1))));
    expect(await orderData("alone")).toMatchObject({ status: "ready", courierId: null });
  });
});
