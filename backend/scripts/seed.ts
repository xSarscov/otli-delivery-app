// Seeds the local Firebase emulators with fixed-UID test accounts (ADR-15).
// Safety: this script only ever talks to the emulators. It refuses to run unless both
// emulator host variables are set, so it can never touch a production project.
//
//   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 npm --prefix backend run seed
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

const PROJECT_ID = "demo-otli";
const EMULATOR_PASSWORD = "otli-demo-123";

function requireEmulatorEnv(env: NodeJS.ProcessEnv): void {
  const missing = ["FIRESTORE_EMULATOR_HOST", "FIREBASE_AUTH_EMULATOR_HOST"].filter((name) => !env[name]);
  if (missing.length > 0) {
    throw new Error(
      `Refusing to seed: ${missing.join(", ")} not set. This script only targets the local emulators ` +
        "(example: FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099).",
    );
  }
}

interface SeedUser {
  uid: string;
  email: string;
  role: "admin" | "merchant";
  status: "active" | "pending";
  displayName: string;
  phone: string;
}

const SEED_USERS: SeedUser[] = [
  { uid: "seed-admin", email: "admin@otli.test", role: "admin", status: "active", displayName: "Otli Admin", phone: "8888-0000" },
  { uid: "seed-merchant-1", email: "merchant1@otli.test", role: "merchant", status: "active", displayName: "Doña Marta", phone: "8888-0101" },
  { uid: "seed-merchant-2", email: "merchant2@otli.test", role: "merchant", status: "active", displayName: "Don Chepe", phone: "8888-0102" },
  { uid: "seed-merchant-pending", email: "merchant-pending@otli.test", role: "merchant", status: "pending", displayName: "Nuevo Comercio", phone: "8888-0103" },
];

interface SeedProduct {
  id: string;
  categoryId: string;
  name: string;
  description: string;
  priceCents: number;
  isAvailable: boolean;
}

interface SeedMerchant {
  uid: string;
  name: string;
  description: string;
  phone: string;
  status: "active" | "pending";
  isOpen: boolean;
  location: { lat: number; lng: number; reference: string };
  categories: { id: string; name: string; sortOrder: number }[];
  products: SeedProduct[];
}

// Prices are NIO centavos (C$ 1.00 = 100). Photos are intentionally absent (photoVersion 0).
const SEED_MERCHANTS: SeedMerchant[] = [
  {
    uid: "seed-merchant-1",
    name: "Comedor Doña Marta",
    description: "Comida típica nicaragüense",
    phone: "8888-0101",
    status: "active",
    isOpen: true,
    location: { lat: 12.2667, lng: -86.5667, reference: "Frente al parque central de Nagarote" },
    categories: [
      { id: "cat-platos", name: "Platos fuertes", sortOrder: 1 },
      { id: "cat-bebidas", name: "Bebidas", sortOrder: 2 },
    ],
    products: [
      { id: "prod-gallo-pinto", categoryId: "cat-platos", name: "Gallo pinto con queso", description: "Con cuajada y plátano", priceCents: 9000, isAvailable: true },
      { id: "prod-nacatamal", categoryId: "cat-platos", name: "Nacatamal", description: "Cerdo, arroz y papa", priceCents: 12000, isAvailable: true },
      { id: "prod-vigoron", categoryId: "cat-platos", name: "Vigorón", description: "Yuca, chicharrón y repollo", priceCents: 8000, isAvailable: false },
      { id: "prod-fresco-cacao", categoryId: "cat-bebidas", name: "Fresco de cacao", description: "Vaso grande", priceCents: 2500, isAvailable: true },
      { id: "prod-fresco-jamaica", categoryId: "cat-bebidas", name: "Fresco de jamaica", description: "Vaso grande", priceCents: 2000, isAvailable: true },
    ],
  },
  {
    uid: "seed-merchant-2",
    name: "Pizzería Don Chepe",
    description: "Pizzas y bebidas frías",
    phone: "8888-0102",
    status: "active",
    isOpen: false,
    location: { lat: 12.2681, lng: -86.5642, reference: "Una cuadra al norte de la iglesia" },
    categories: [
      { id: "cat-pizzas", name: "Pizzas", sortOrder: 1 },
      { id: "cat-gaseosas", name: "Gaseosas", sortOrder: 2 },
    ],
    products: [
      { id: "prod-pizza-queso", categoryId: "cat-pizzas", name: "Pizza de queso", description: "Mediana, 8 porciones", priceCents: 22000, isAvailable: true },
      { id: "prod-pizza-mixta", categoryId: "cat-pizzas", name: "Pizza mixta", description: "Jamón, chorizo y queso", priceCents: 26000, isAvailable: true },
      { id: "prod-pizza-hawaiana", categoryId: "cat-pizzas", name: "Pizza hawaiana", description: "Jamón y piña", priceCents: 25000, isAvailable: true },
      { id: "prod-coca-cola", categoryId: "cat-gaseosas", name: "Coca-Cola 600 ml", description: "Bien fría", priceCents: 3000, isAvailable: true },
      { id: "prod-fanta", categoryId: "cat-gaseosas", name: "Fanta 600 ml", description: "Bien fría", priceCents: 3000, isAvailable: true },
    ],
  },
  {
    // Pending on purpose: the Admin approval demo (Slice 6) flips this one to active.
    uid: "seed-merchant-pending",
    name: "Nuevo Comercio",
    description: "Esperando aprobación",
    phone: "8888-0103",
    status: "pending",
    isOpen: false,
    location: { lat: 12.2650, lng: -86.5690, reference: "Barrio San Antonio" },
    categories: [],
    products: [],
  },
];

async function upsertUser(user: SeedUser): Promise<"created" | "updated"> {
  const auth = getAuth();
  const db = getFirestore();
  let outcome: "created" | "updated" = "updated";
  try {
    await auth.getUser(user.uid);
    await auth.updateUser(user.uid, { email: user.email, password: EMULATOR_PASSWORD, displayName: user.displayName });
  } catch (error) {
    if ((error as { code?: string }).code !== "auth/user-not-found") throw error;
    await auth.createUser({ uid: user.uid, email: user.email, password: EMULATOR_PASSWORD, displayName: user.displayName });
    outcome = "created";
  }

  const ref = db.collection("users").doc(user.uid);
  const existing = await ref.get();
  const { uid: _uid, ...fields } = user;
  await ref.set(existing.exists ? fields : { ...fields, createdAt: FieldValue.serverTimestamp() }, { merge: true });
  return outcome;
}

/** Upserts `merchants/{uid}` and its catalog under fixed ids; createdAt is only set on first creation. */
async function upsertMerchant(merchant: SeedMerchant): Promise<void> {
  const db = getFirestore();
  const ref = db.collection("merchants").doc(merchant.uid);
  const existing = await ref.get();
  const { uid: _uid, categories, products, ...profile } = merchant;
  await ref.set(
    {
      ...profile,
      photoVersion: 0,
      updatedAt: FieldValue.serverTimestamp(),
      ...(existing.exists ? {} : { createdAt: FieldValue.serverTimestamp() }),
    },
    { merge: true },
  );
  for (const { id, ...category } of categories) {
    await ref.collection("categories").doc(id).set(category);
  }
  for (const { id, priceCents, ...product } of products) {
    await ref.collection("products").doc(id).set({ ...product, priceCents, photoVersion: 0, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  }
}

async function main(): Promise<void> {
  requireEmulatorEnv(process.env);
  initializeApp({ projectId: PROJECT_ID });
  for (const user of SEED_USERS) {
    const outcome = await upsertUser(user);
    console.log(`${user.uid}: auth user ${outcome}, users/${user.uid} document upserted`);
  }
  for (const merchant of SEED_MERCHANTS) {
    await upsertMerchant(merchant);
    console.log(`${merchant.uid}: merchants/${merchant.uid} upserted with ${merchant.categories.length} categories and ${merchant.products.length} products`);
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
