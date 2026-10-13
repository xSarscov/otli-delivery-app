// Decides where the seed script may write and with which password (ADR-15).
// Pure on purpose: every safeguard is unit-tested in tests/seed-target.test.ts, no emulator needed.

export const EMULATOR_PROJECT_ID = "demo-otli";
export const PRODUCTION_PROJECT_ID = "otli-39efc";
export const EMULATOR_PASSWORD = "otli-demo-123";

const MIN_PRODUCTION_PASSWORD_LENGTH = 8;
const EMULATOR_HOST_VARIABLES = ["FIRESTORE_EMULATOR_HOST", "FIREBASE_AUTH_EMULATOR_HOST"] as const;

export interface SeedTarget {
  kind: "emulator" | "production";
  projectId: string;
  password: string;
}

/**
 * @param credentialProjectId `project_id` read from the file GOOGLE_APPLICATION_CREDENTIALS points at
 *   (undefined when the variable is not set); lets us refuse credentials of another project.
 */
export function resolveSeedTarget(argv: string[], env: NodeJS.ProcessEnv, credentialProjectId: string | undefined): SeedTarget {
  const targetFlag = argv.find((arg) => arg.startsWith("--target="));
  const target = targetFlag ? targetFlag.slice("--target=".length) : "emulator";
  const confirmed = argv.includes("--confirm");

  if (target === "emulator") {
    if (confirmed) throw new Error("--confirm only applies to --target=production.");
    const missing = EMULATOR_HOST_VARIABLES.filter((name) => !env[name]);
    if (missing.length > 0) {
      throw new Error(
        `Refusing to seed: ${missing.join(", ")} not set. The default target is the local emulators ` +
          "(example: FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099).",
      );
    }
    return { kind: "emulator", projectId: EMULATOR_PROJECT_ID, password: EMULATOR_PASSWORD };
  }

  if (target !== "production") throw new Error(`Unknown seed target '${target}' (use 'emulator' or 'production').`);
  if (!confirmed) throw new Error(`Refusing to seed ${PRODUCTION_PROJECT_ID}: --confirm is required with --target=production.`);

  const emulatorHosts = EMULATOR_HOST_VARIABLES.filter((name) => env[name]);
  if (emulatorHosts.length > 0) {
    throw new Error(`Refusing to seed production: ${emulatorHosts.join(", ")} is set, so the Admin SDK would write to the emulator.`);
  }
  if (!env.GOOGLE_APPLICATION_CREDENTIALS || credentialProjectId === undefined) {
    throw new Error("Refusing to seed production: GOOGLE_APPLICATION_CREDENTIALS must point at a service-account key file.");
  }
  if (credentialProjectId !== PRODUCTION_PROJECT_ID) {
    throw new Error(`Refusing to seed production: the credentials belong to '${credentialProjectId}', expected '${PRODUCTION_PROJECT_ID}'.`);
  }
  const password = env.OTLI_SEED_PASSWORD;
  if (!password || password === EMULATOR_PASSWORD || password.length < MIN_PRODUCTION_PASSWORD_LENGTH) {
    throw new Error(
      `Refusing to seed production: OTLI_SEED_PASSWORD must be set, at least ${MIN_PRODUCTION_PASSWORD_LENGTH} characters, ` +
        "and different from the public demo password.",
    );
  }
  return { kind: "production", projectId: PRODUCTION_PROJECT_ID, password };
}
