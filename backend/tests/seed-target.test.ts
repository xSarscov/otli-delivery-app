import { describe, expect, it } from "vitest";
import { PRODUCTION_PROJECT_ID, resolveSeedTarget } from "../scripts/seed-target";

const EMULATOR_ENV = { FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080", FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099" };
const PRODUCTION_ENV = { GOOGLE_APPLICATION_CREDENTIALS: "C:/keys/otli-service-account.json", OTLI_SEED_PASSWORD: "a-long-demo-password" };
const PRODUCTION_ARGS = ["--target=production", "--confirm"];

describe("resolveSeedTarget: emulator (default)", () => {
  it("targets the demo project with the public demo password when both emulator hosts are set", () => {
    expect(resolveSeedTarget([], EMULATOR_ENV, undefined)).toEqual({
      kind: "emulator",
      projectId: "demo-otli",
      password: "otli-demo-123",
    });
  });

  it("refuses to run without the emulator hosts, so it can never fall through to a real project", () => {
    expect(() => resolveSeedTarget([], {}, undefined)).toThrow(/FIRESTORE_EMULATOR_HOST, FIREBASE_AUTH_EMULATOR_HOST/);
    expect(() => resolveSeedTarget([], { FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080" }, undefined)).toThrow(/FIREBASE_AUTH_EMULATOR_HOST/);
  });

  it("refuses the production-only flags without --target=production", () => {
    expect(() => resolveSeedTarget(["--confirm"], EMULATOR_ENV, undefined)).toThrow(/--confirm/);
  });

  it("rejects an unknown target", () => {
    expect(() => resolveSeedTarget(["--target=staging"], EMULATOR_ENV, undefined)).toThrow(/Unknown seed target/);
  });
});

describe("resolveSeedTarget: production", () => {
  it("targets the live project with the password from OTLI_SEED_PASSWORD", () => {
    expect(resolveSeedTarget(PRODUCTION_ARGS, PRODUCTION_ENV, PRODUCTION_PROJECT_ID)).toEqual({
      kind: "production",
      projectId: PRODUCTION_PROJECT_ID,
      password: "a-long-demo-password",
    });
  });

  it("requires --confirm", () => {
    expect(() => resolveSeedTarget(["--target=production"], PRODUCTION_ENV, PRODUCTION_PROJECT_ID)).toThrow(/--confirm/);
  });

  it("requires explicit service-account credentials", () => {
    const { OTLI_SEED_PASSWORD } = PRODUCTION_ENV;
    expect(() => resolveSeedTarget(PRODUCTION_ARGS, { OTLI_SEED_PASSWORD }, undefined)).toThrow(/GOOGLE_APPLICATION_CREDENTIALS/);
  });

  it("requires a seed password and never falls back to the public demo one", () => {
    const { GOOGLE_APPLICATION_CREDENTIALS } = PRODUCTION_ENV;
    expect(() => resolveSeedTarget(PRODUCTION_ARGS, { GOOGLE_APPLICATION_CREDENTIALS }, PRODUCTION_PROJECT_ID)).toThrow(/OTLI_SEED_PASSWORD/);
  });

  it("rejects a password that is the public demo one or too short", () => {
    for (const OTLI_SEED_PASSWORD of ["otli-demo-123", "short"]) {
      expect(() => resolveSeedTarget(PRODUCTION_ARGS, { ...PRODUCTION_ENV, OTLI_SEED_PASSWORD }, PRODUCTION_PROJECT_ID)).toThrow(/OTLI_SEED_PASSWORD/);
    }
  });

  it("refuses when an emulator host is set, because the Admin SDK would then write to the emulator", () => {
    expect(() => resolveSeedTarget(PRODUCTION_ARGS, { ...PRODUCTION_ENV, ...EMULATOR_ENV }, PRODUCTION_PROJECT_ID)).toThrow(/emulator/i);
  });

  it("refuses credentials that belong to another project", () => {
    expect(() => resolveSeedTarget(PRODUCTION_ARGS, PRODUCTION_ENV, "some-other-project")).toThrow(/some-other-project/);
  });
});
