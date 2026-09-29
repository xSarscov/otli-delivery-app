// Runs a test command inside `firebase emulators:exec` with FIXED arguments only.
// Extra CLI arguments are treated as vitest file filters (letters, digits, dash, dot,
// underscore, slash) so `npm --prefix backend test -- users` works; anything else is rejected.
import { spawnSync } from "node:child_process";

const [mode, ...extra] = process.argv.slice(2);
const safeFilter = /^[\w./-]+$/;

let script;
if (mode === "vitest") {
  if (!extra.every((arg) => safeFilter.test(arg))) {
    console.error("Only simple file filters are accepted after `--`.");
    process.exit(2);
  }
  script = ["vitest", "run", ...extra].join(" ");
} else if (mode === "android") {
  const gradlew = process.platform === "win32" ? "gradlew" : "./gradlew";
  script = `cd .. && ${gradlew} connectedDebugAndroidTest`;
} else {
  console.error("Usage: run-with-emulators.mjs <vitest|android> [filters]");
  process.exit(2);
}

const result = spawnSync(
  "npx",
  ["firebase", "emulators:exec", "--only", "firestore,auth", "--project", "demo-otli", `"${script}"`],
  { stdio: "inherit", shell: true },
);
process.exit(result.status ?? 1);
