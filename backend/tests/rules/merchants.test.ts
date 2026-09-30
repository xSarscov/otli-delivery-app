import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { describe, expect, it } from "vitest";
import { merchantDoc, NAGAROTE_LOCATION, serverTime, useCatalogEnv } from "./catalog-support";

const { as, signedOut, admin } = useCatalogEnv();

/** Creates a users document for a uid that has no merchants document yet. */
async function registerUser(uid: string, role: string) {
  await admin((db) =>
    db.collection("users").doc(uid).set({ role, status: "pending", displayName: uid, email: "x@otli.test", phone: "1", createdAt: new Date() }),
  );
}

describe("merchants/{uid} create", () => {
  it("lets a merchant create their own pending, closed profile", async () => {
    await registerUser("new-merchant", "merchant");
    await assertSucceeds(as("new-merchant").collection("merchants").doc("new-merchant").set(merchantDoc()));
  });

  it("lets a merchant create the profile together with users/{uid} in one batch", async () => {
    const db = as("batch-merchant");
    const batch = db.batch();
    batch.set(db.collection("users").doc("batch-merchant"), {
      role: "merchant",
      status: "pending",
      displayName: "B",
      email: "b@otli.test",
      phone: "1",
      createdAt: serverTime(),
    });
    batch.set(db.collection("merchants").doc("batch-merchant"), merchantDoc());
    await assertSucceeds(batch.commit());
  });

  it("accepts a location with a reference text", async () => {
    await registerUser("new-merchant", "merchant");
    const location = { lat: 12.2, lng: -86.5, reference: "Frente al parque" };
    await assertSucceeds(as("new-merchant").collection("merchants").doc("new-merchant").set(merchantDoc({ location })));
  });

  it("denies creation without a location pin", async () => {
    await registerUser("no-pin", "merchant");
    const { location: _location, ...withoutLocation } = merchantDoc();
    await assertFails(as("no-pin").collection("merchants").doc("no-pin").set(withoutLocation));
  });

  it("denies a malformed or out-of-range location", async () => {
    await registerUser("bad-pin", "merchant");
    const doc = as("bad-pin").collection("merchants").doc("bad-pin");
    await assertFails(doc.set(merchantDoc({ location: { lat: 95, lng: -86.5, reference: "" } })));
    await assertFails(doc.set(merchantDoc({ location: { lat: 12.2, lng: -86.5 } })));
    await assertFails(doc.set(merchantDoc({ location: "12.2,-86.5" })));
  });

  it("denies creation without a contact phone or with a blank one", async () => {
    await registerUser("no-phone", "merchant");
    const doc = as("no-phone").collection("merchants").doc("no-phone");
    const { phone: _phone, ...withoutPhone } = merchantDoc();
    await assertFails(doc.set(withoutPhone));
    await assertFails(doc.set(merchantDoc({ phone: "   " })));
  });

  it("denies self-activating or opening at creation", async () => {
    await registerUser("u1", "merchant");
    await assertFails(as("u1").collection("merchants").doc("u1").set(merchantDoc({ status: "active" })));
    await assertFails(as("u1").collection("merchants").doc("u1").set(merchantDoc({ isOpen: true })));
  });

  it("denies creating a merchant profile for another uid", async () => {
    await registerUser("u2", "merchant");
    await assertFails(as("u2").collection("merchants").doc("someone-else").set(merchantDoc()));
  });

  it("denies a customer or courier creating a merchant profile", async () => {
    await registerUser("cust", "customer");
    await registerUser("cour", "courier");
    await assertFails(as("cust").collection("merchants").doc("cust").set(merchantDoc()));
    await assertFails(as("cour").collection("merchants").doc("cour").set(merchantDoc()));
  });

  it("denies signed-out creation", async () => {
    await assertFails(signedOut().collection("merchants").doc("ghost").set(merchantDoc()));
  });

  it("denies unexpected fields, a client createdAt and a blank name", async () => {
    await registerUser("u3", "merchant");
    await assertFails(as("u3").collection("merchants").doc("u3").set(merchantDoc({ verified: true })));
    await assertFails(as("u3").collection("merchants").doc("u3").set(merchantDoc({ createdAt: new Date(0) })));
    await assertFails(as("u3").collection("merchants").doc("u3").set(merchantDoc({ name: "" })));
  });
});

describe("merchants/{uid} owner update", () => {
  const merchantA = () => as("merchant-a").collection("merchants").doc("merchant-a");

  it("lets an active merchant edit name, description, phone and location", async () => {
    await assertSucceeds(merchantA().update({ name: "Nuevo nombre", description: "Otra", phone: "7777", updatedAt: serverTime() }));
    await assertSucceeds(merchantA().update({ location: { lat: 12.2, lng: -86.5, reference: "Esquina" }, updatedAt: serverTime() }));
  });

  it("keeps the phone and location required on update", async () => {
    await assertFails(merchantA().update({ phone: "", updatedAt: serverTime() }));
    await assertFails(merchantA().update({ location: { ...NAGAROTE_LOCATION, lat: 200 }, updatedAt: serverTime() }));
  });

  it("lets an active merchant replace the photo version", async () => {
    await assertSucceeds(merchantA().update({ photoVersion: 2, updatedAt: serverTime() }));
  });

  it("lets an active merchant close and reopen the store", async () => {
    await assertSucceeds(merchantA().update({ isOpen: false, updatedAt: serverTime() }));
    await assertSucceeds(merchantA().update({ isOpen: true, updatedAt: serverTime() }));
  });

  it("denies changing status, createdAt or adding unknown fields", async () => {
    await assertFails(merchantA().update({ status: "suspended", updatedAt: serverTime() }));
    await assertFails(merchantA().update({ createdAt: new Date(0), updatedAt: serverTime() }));
    await assertFails(merchantA().update({ verified: true, updatedAt: serverTime() }));
  });

  it("denies a blank name and a client-supplied updatedAt", async () => {
    await assertFails(merchantA().update({ name: "   ", updatedAt: serverTime() }));
    await assertFails(merchantA().update({ name: "Ok", updatedAt: new Date(0) }));
  });

  it("denies a pending or suspended merchant editing their profile or opening the store", async () => {
    await assertFails(as("merchant-pending").collection("merchants").doc("merchant-pending").update({ isOpen: true, updatedAt: serverTime() }));
    await assertFails(as("merchant-suspended").collection("merchants").doc("merchant-suspended").update({ name: "x", updatedAt: serverTime() }));
  });

  it("denies editing another merchant's profile or store state", async () => {
    await assertFails(as("merchant-a").collection("merchants").doc("merchant-b").update({ isOpen: true, updatedAt: serverTime() }));
    await assertFails(as("customer-1").collection("merchants").doc("merchant-a").update({ isOpen: false, updatedAt: serverTime() }));
  });

  it("denies deleting a merchant profile", async () => {
    await assertFails(merchantA().delete());
    await assertFails(as("admin-1").collection("merchants").doc("merchant-a").delete());
  });
});

describe("merchants/{uid} admin status mirror", () => {
  it("lets an admin approve and suspend a merchant", async () => {
    await assertSucceeds(as("admin-1").collection("merchants").doc("merchant-pending").update({ status: "active" }));
    await assertSucceeds(as("admin-1").collection("merchants").doc("merchant-a").update({ status: "suspended", updatedAt: serverTime() }));
  });

  it("denies an admin changing anything but status and updatedAt", async () => {
    await assertFails(as("admin-1").collection("merchants").doc("merchant-a").update({ name: "Renamed" }));
    await assertFails(as("admin-1").collection("merchants").doc("merchant-a").update({ isOpen: false }));
  });

  it("denies an unknown status value", async () => {
    await assertFails(as("admin-1").collection("merchants").doc("merchant-a").update({ status: "banned" }));
  });

  it("denies a non-admin changing status", async () => {
    await assertFails(as("customer-1").collection("merchants").doc("merchant-pending").update({ status: "active" }));
  });
});

describe("merchants/{uid} reads", () => {
  it("lets any signed-in user read a merchant and list active merchants ordered by name", async () => {
    await assertSucceeds(as("customer-1").collection("merchants").doc("merchant-a").get());
    const snapshot = await assertSucceeds(as("customer-1").collection("merchants").where("status", "==", "active").orderBy("name").get());
    expect(snapshot.docs.map((doc) => doc.id).sort()).toEqual(["merchant-a", "merchant-b"]);
  });

  it("lets a merchant read another merchant's public profile", async () => {
    await assertSucceeds(as("merchant-a").collection("merchants").doc("merchant-b").get());
  });

  it("denies signed-out reads", async () => {
    await assertFails(signedOut().collection("merchants").doc("merchant-a").get());
    await assertFails(signedOut().collection("merchants").where("status", "==", "active").get());
  });
});
