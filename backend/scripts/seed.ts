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
  role: "admin";
  status: "active";
  displayName: string;
  phone: string;
}

const SEED_USERS: SeedUser[] = [
  { uid: "seed-admin", email: "admin@otli.test", role: "admin", status: "active", displayName: "Otli Admin", phone: "8888-0000" },
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

async function main(): Promise<void> {
  requireEmulatorEnv(process.env);
  initializeApp({ projectId: PROJECT_ID });
  for (const user of SEED_USERS) {
    const outcome = await upsertUser(user);
    console.log(`${user.uid}: auth user ${outcome}, users/${user.uid} document upserted`);
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
