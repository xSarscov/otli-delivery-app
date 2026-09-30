import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { bytesOf, KB, productDoc, serverTime, useCatalogEnv } from "./catalog-support";

const { as, signedOut, admin } = useCatalogEnv();

/** The merchant document of [owner] as seen by the signed-in user [uid]. */
const catalogOf = (uid: string, owner: string) => as(uid).collection("merchants").doc(owner);
const signedOutCatalog = () => signedOut().collection("merchants").doc("merchant-a");

describe("categories", () => {
  const categories = (uid: string, owner = uid) => catalogOf(uid, owner).collection("categories");

  it("lets the active owner create, edit and delete categories", async () => {
    await assertSucceeds(categories("merchant-a").doc("cat-2").set({ name: "Bebidas", sortOrder: 2 }));
    await assertSucceeds(categories("merchant-a").doc("cat-2").update({ name: "Refrescos" }));
    await assertSucceeds(categories("merchant-a").doc("cat-2").delete());
  });

  it("denies a blank name, a non-integer sortOrder and unknown fields", async () => {
    await assertFails(categories("merchant-a").doc("bad-1").set({ name: "", sortOrder: 1 }));
    await assertFails(categories("merchant-a").doc("bad-2").set({ name: "Ok", sortOrder: "first" }));
    await assertFails(categories("merchant-a").doc("bad-3").set({ name: "Ok", sortOrder: 1, hidden: true }));
  });

  it("denies another merchant creating, editing or removing a category", async () => {
    const victim = categories("merchant-b", "merchant-a");
    await assertFails(victim.doc("evil").set({ name: "Evil", sortOrder: 1 }));
    await assertFails(victim.doc("cat-1").update({ name: "Hijacked" }));
    await assertFails(victim.doc("cat-1").delete());
  });

  it("denies pending and suspended owners and every non-merchant", async () => {
    const attempt = { name: "X", sortOrder: 1 };
    await assertFails(categories("merchant-pending").doc("c").set(attempt));
    await assertFails(categories("merchant-suspended").doc("c").set(attempt));
    await assertFails(categories("customer-1", "merchant-a").doc("c").set(attempt));
    await assertFails(categories("admin-1", "merchant-a").doc("c").set(attempt));
    await assertFails(signedOutCatalog().collection("categories").doc("c").set(attempt));
  });

  it("lets any signed-in user read categories but not signed-out users", async () => {
    await assertSucceeds(categories("customer-1", "merchant-a").get());
    await assertSucceeds(categories("merchant-b", "merchant-a").doc("cat-1").get());
    await assertFails(signedOutCatalog().collection("categories").get());
  });
});

describe("products", () => {
  const products = (uid: string, owner = uid) => catalogOf(uid, owner).collection("products");

  it("lets the active owner create a product with a NIO price", async () => {
    await assertSucceeds(products("merchant-a").doc("prod-2").set(productDoc({ priceCents: 12_550 })));
    const stored = await admin((db) => db.collection("merchants").doc("merchant-a").collection("products").doc("prod-2").get());
    expect(stored.data()?.priceCents).toBe(12_550);
  });

  it("lets the owner edit, toggle availability and delete", async () => {
    await assertSucceeds(products("merchant-a").doc("prod-1").update({ name: "Nuevo", updatedAt: serverTime() }));
    await assertSucceeds(products("merchant-a").doc("prod-1").update({ isAvailable: false, updatedAt: serverTime() }));
    await assertSucceeds(products("merchant-a").doc("prod-1").delete());
  });

  it("denies zero, negative, fractional and non-numeric prices", async () => {
    const badPrices: unknown[] = [0, -100, 10.5, "90"];
    for (const [index, priceCents] of badPrices.entries()) {
      await assertFails(products("merchant-a").doc(`bad-${index}`).set(productDoc({ priceCents })));
    }
    await assertFails(products("merchant-a").doc("prod-1").update({ priceCents: 0, updatedAt: serverTime() }));
  });

  it("denies blank names, bad flags, negative photoVersion and unknown fields", async () => {
    await assertFails(products("merchant-a").doc("b1").set(productDoc({ name: " " })));
    await assertFails(products("merchant-a").doc("b2").set(productDoc({ isAvailable: "yes" })));
    await assertFails(products("merchant-a").doc("b3").set(productDoc({ photoVersion: -1 })));
    await assertFails(products("merchant-a").doc("b4").set(productDoc({ discount: 5 })));
  });

  it("denies a client-supplied updatedAt", async () => {
    await assertFails(products("merchant-a").doc("b5").set(productDoc({ updatedAt: new Date(0) })));
  });

  it("denies another merchant creating, editing or removing products", async () => {
    const victim = products("merchant-b", "merchant-a");
    await assertFails(victim.doc("evil").set(productDoc()));
    await assertFails(victim.doc("prod-1").update({ isAvailable: false, updatedAt: serverTime() }));
    await assertFails(victim.doc("prod-1").delete());
  });

  it("denies suspended owners, customers, admins and signed-out users writing", async () => {
    await assertFails(products("merchant-suspended").doc("p").set(productDoc()));
    await assertFails(products("customer-1", "merchant-a").doc("p").set(productDoc()));
    await assertFails(products("admin-1", "merchant-a").doc("p").set(productDoc()));
    await assertFails(signedOutCatalog().collection("products").doc("p").set(productDoc()));
  });

  it("lets any signed-in user read products, including a closed store's, but not signed-out users", async () => {
    await assertSucceeds(products("customer-1", "merchant-a").get());
    await assertSucceeds(products("customer-1", "merchant-b").doc("prod-1").get());
    await assertFails(signedOutCatalog().collection("products").get());
  });
});

describe("productPhotos", () => {
  const photos = (uid: string, owner = uid) => catalogOf(uid, owner).collection("productPhotos");

  it("lets the active owner store a photo up to 300 KB", async () => {
    await assertSucceeds(photos("merchant-a").doc("prod-2").set({ jpeg: bytesOf(10 * KB), version: 1 }));
    await assertSucceeds(photos("merchant-a").doc("prod-3").set({ jpeg: bytesOf(300 * KB), version: 1 }));
  });

  it("denies a photo over 300 KB", async () => {
    await assertFails(photos("merchant-a").doc("prod-1").set({ jpeg: bytesOf(300 * KB + 1), version: 2 }));
  });

  it("denies non-bytes payloads, a bad version and unknown fields", async () => {
    await assertFails(photos("merchant-a").doc("p1").set({ jpeg: "not-bytes", version: 1 }));
    await assertFails(photos("merchant-a").doc("p2").set({ jpeg: bytesOf(10), version: "1" }));
    await assertFails(photos("merchant-a").doc("p3").set({ jpeg: bytesOf(10), version: 1, caption: "x" }));
  });

  it("lets the owner replace and delete a photo", async () => {
    await assertSucceeds(photos("merchant-a").doc("prod-1").set({ jpeg: bytesOf(20 * KB), version: 2 }));
    await assertSucceeds(photos("merchant-a").doc("prod-1").delete());
  });

  it("denies another merchant, a customer or a suspended owner writing photos", async () => {
    await assertFails(photos("merchant-b", "merchant-a").doc("prod-1").set({ jpeg: bytesOf(10), version: 9 }));
    await assertFails(photos("merchant-b", "merchant-a").doc("prod-1").delete());
    await assertFails(photos("customer-1", "merchant-a").doc("prod-1").set({ jpeg: bytesOf(10), version: 9 }));
    await assertFails(photos("merchant-suspended").doc("p").set({ jpeg: bytesOf(10), version: 1 }));
  });

  it("lets any signed-in user read photos but not signed-out users", async () => {
    await assertSucceeds(photos("customer-1", "merchant-a").doc("prod-1").get());
    await assertFails(signedOutCatalog().collection("productPhotos").doc("prod-1").get());
  });
});
