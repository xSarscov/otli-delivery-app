// Runs a test command inside `firebase emulators:exec` with FIXED arguments only.
// Extra CLI arguments are treated as vitest file filters (letters, digits, dash, dot,
// underscore, slash) so `npm --prefix backend test -- users` works; anything else is rejected.
import { spawn, spawnSync } from "node:child_process";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const EMULATOR_PORTS = [8080, 9099]; // Firestore, Auth (fixed; see backend/firebase.json)
const safeSerial = /^[\w.:-]+$/;

/**
 * Picks the target device (ANDROID_SERIAL, or the only attached one) and, for a physical
 * device, tunnels the emulator ports with `adb reverse` so the app can use 127.0.0.1.
 * All adb arguments are fixed apart from the serial, which must match a strict pattern.
 */
function prepareDevice() {
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
  const adb = sdk ? join(sdk, "platform-tools", process.platform === "win32" ? "adb.exe" : "adb") : "adb";
  const listing = spawnSync(adb, ["devices"], { encoding: "utf8" });
  if (listing.error || listing.status !== 0) return { gradleArgs: "", cleanup: () => {} };
  const serials = listing.stdout
    .split(/\r?\n/)
    .slice(1)
    .map((line) => line.trim().split(/\s+/))
    .filter(([serial, state]) => serial && state === "device")
    .map(([serial]) => serial);
  const serial = process.env.ANDROID_SERIAL ?? (serials.length === 1 ? serials[0] : undefined);
  if (!serial || !safeSerial.test(serial) || !serials.includes(serial)) {
    return { gradleArgs: "", cleanup: () => {} };
  }
  process.env.ANDROID_SERIAL = serial;
  if (serial.startsWith("emulator-")) return { gradleArgs: "", cleanup: () => {} };
  for (const port of EMULATOR_PORTS) {
    spawnSync(adb, ["-s", serial, "reverse", `tcp:${port}`, `tcp:${port}`], { stdio: "inherit" });
  }
  console.log(`Physical device ${serial}: adb reverse set for ports ${EMULATOR_PORTS.join(", ")}.`);
  return {
    gradleArgs: " -Potli.emulatorHost=127.0.0.1",
    cleanup: () => {
      for (const port of EMULATOR_PORTS) {
        spawnSync(adb, ["-s", serial, "reverse", "--remove", `tcp:${port}`], { stdio: "inherit" });
      }
      console.log(`Physical device ${serial}: adb reverse removed.`);
    },
  };
}

const [mode, ...extra] = process.argv.slice(2);
const safeFilter = /^[\w./-]+$/;

let script;
let cleanup = () => {};
if (mode === "vitest") {
  if (!extra.every((arg) => safeFilter.test(arg))) {
    console.error("Only simple file filters are accepted after `--`.");
    process.exit(2);
  }
  script = ["vitest", "run", ...extra].join(" ");
} else if (mode === "android") {
  // emulators:exec does not run the script from this directory, so use the absolute repo root.
  const root = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..").replaceAll("\\", "/");
  if (/\s/.test(root)) {
    console.error("The repository path must not contain spaces for test:android.");
    process.exit(2);
  }
  const windows = process.platform === "win32";
  const gradlew = windows ? `${root}/gradlew.bat` : "./gradlew";
  const device = prepareDevice();
  cleanup = device.cleanup;
  // Seed first: adapter tests sign in as the seeded merchants (fresh emulators start empty).
  const seed = `npm --prefix ${root}/backend run seed`;
  script = `cd ${windows ? "/d " : ""}${root} && ${seed} && ${gradlew} connectedDebugAndroidTest${device.gradleArgs}`;
} else {
  console.error("Usage: run-with-emulators.mjs <vitest|android> [filters]");
  process.exit(2);
}

// AGP reports BUILD SUCCESSFUL even when the APK could not be installed on the device, so
// the android mode also scans the output for those failures instead of trusting the exit code.
const installFailure = /AndroidTestRunner failed|Failed to install APK/;
let output = "";
const child = spawn(
  "npx",
  ["firebase", "emulators:exec", "--only", "firestore,auth", "--project", "demo-otli", `"${script}"`],
  { stdio: ["inherit", "pipe", "pipe"], shell: true },
);
for (const [stream, sink] of [[child.stdout, process.stdout], [child.stderr, process.stderr]]) {
  stream.on("data", (chunk) => {
    sink.write(chunk);
    if (mode === "android") output = (output + chunk).slice(-20000);
  });
}
child.on("close", (status) => {
  cleanup();
  if (mode === "android" && status === 0 && installFailure.test(output)) {
    console.error("Instrumented tests did not run: the APK could not be installed on the device.");
    process.exit(1);
  }
  process.exit(status ?? 1);
});
