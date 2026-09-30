import { readFileSync } from "node:fs";
import { assertFails, initializeTestEnvironment, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { afterAll, beforeAll, describe, it } from "vitest";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-otli",
    firestore: { rules: readFileSync("firestore.rules", "utf8") },
  });
});

afterAll(async () => {
  await env.cleanup();
});

// Proves the rules runner works end to end; replaced by real matrices from slice 1.
describe("scaffold rules", () => {
  it("denies a signed-out read", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(db.collection("users").doc("anyone").get());
  });
});
