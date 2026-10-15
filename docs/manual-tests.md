# Manual tests log

Single log of the device checks run for `otli-device-evidence`; each device session appends rows at its end. `reference` is an Engram observation id, the path of a small text artifact, or `-`.

| date | row IDs | what was tested | result | where | who | reference |
|------|---------|-----------------|--------|-------|-----|-----------|
| 2026-10-14 | O2 | `FirestorePhotoSourceTest` (4 cases) run alone on the phone against fresh seeded local emulators | PASS 4/4 | Xiaomi 2510DRA23L (Android 16), local emulators through `adb reverse` | agent via adb | Engram #79 |
| 2026-10-14 | O2 | Temporary mutants of `FirestorePhotoSource.fetch`, one at a time, class rerun after each, file restored from git each time: M1 `return null`, M2 reads the profile photo id, M3 hard-coded `seed-merchant-1` | PASS (each mutant seen failing): M1 failed 3 cases (`aCustomerReadsAMerchantsProductPhoto`, `theProfilePhotoIsReadUnderTheFixedProfileId`, `aSignedOutReaderIsRefused`); M2 failed `aCustomerReadsAMerchantsProductPhoto` and `aPhotoThatDoesNotExistIsNull`; M3 failed `theProfilePhotoIsReadUnderTheFixedProfileId`; no mutant left in the tree | Xiaomi 2510DRA23L (Android 16), local emulators through `adb reverse` | agent via adb | Engram #79 |
| 2026-10-14 | O2, O1 | Full instrumented suite `npm --prefix backend run test:android` (own emulators, manual ones stopped first) | PASS 65/65 in 8 classes: Admin 13, Auth 11, Catalog 9, Merchant 5, PhotoSource 4, Dispatch 11, Order 8, Location 4; 0 failures, 0 skipped | Xiaomi 2510DRA23L (Android 16), local emulators | agent via adb | Engram #79 |
