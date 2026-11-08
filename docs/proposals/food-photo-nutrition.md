# Photo Meal Logging And Nutrition Estimation

> **Status:** Proposed. Nothing here is built.
> **Current behavior source:** [Features](../../Features.md), [Food entry flow](../features/feature-map.md).
> **Constraint that shapes every option:** [Architecture, offline rules](../engineering/architecture.md).

Point a camera at a plate, get a meal logged: recognized foods, estimated portion, nutrients filled in, everything editable before it is saved. This note covers what can be built inside the app's existing rules, what cannot, and the one decision the project owner has to make first.

## Why This Is A Decision, Not Just A Feature

OpenVitals is offline-only by construction. The manifest removes `INTERNET`, `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` from the merged app ([`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml)), phone-to-phone sync uses Bluetooth Classic specifically to keep that true, and the README, the privacy policy and both store listings promise it. The app also ships no ML runtime today: there is no TFLite, ML Kit or ONNX dependency anywhere in `gradle/libs.versions.toml`.

Food recognition is the one health feature that people expect to be a cloud call, and the manifest is built to make that call fail. So the shape of the feature is decided by one question:

**Does a photo of a meal leave the phone?**

- If yes, the `INTERNET` invariant has to change, and with it the privacy policy, the Play data-safety declaration, the F-Droid listing text and `README.md`. That is an explicit product decision, not an implementation detail, and [AGENTS.md](../../AGENTS.md) says not to break an invariant without one.
- If no, recognition runs on the phone, which means a bundled model and a bundled nutrient table, both of which have licensing and APK-size consequences.

Everything below assumes the offline answer, because it is the only one that fits the app as it stands.

## What The App Already Has

The save half of this feature exists and does not need to be rebuilt:

- **Capture:** CameraX 1.6.2 is already a dependency, with `CAMERA` declared and `android.hardware.camera.any` marked `required="false"`. [`MedicalCardScanner.kt`](../../app/src/main/kotlin/tech/mmarca/openvitals/features/imports/medical/MedicalCardScanner.kt) is the precedent for asking for the permission only when a button is tapped, and for a photo being pickable instead when the camera is refused or absent.
- **Food catalog:** [`FoodEntryScreen.kt`](../../app/src/main/kotlin/tech/mmarca/openvitals/features/manualentry/food/FoodEntryScreen.kt) and [`FoodEntryInput.kt`](../../app/src/main/kotlin/tech/mmarca/openvitals/features/manualentry/food/FoodEntryInput.kt) already hold named foods with an amount in grams and a nutrient map, scaled per portion, with validation (`1 g` to `10 000 g`, no empty nutrient set).
- **Nutrients:** `NutritionNutrient` in [`NutritionModels.kt`](../../domain/src/main/kotlin/tech/mmarca/openvitals/domain/model/NutritionModels.kt) covers the energy, macro and micronutrient fields Health Connect accepts, which is what "add everything" means in practice.
- **Write path:** [`NutritionRepository.kt`](../../app/src/main/kotlin/tech/mmarca/openvitals/data/repository/NutritionRepository.kt) has `writeNutritionEntry`, `updateNutritionEntry` and `deleteNutritionEntry`, plus `hasNutritionWritePermission()`; the manual-entry permission requester and `InlineGrantPrompt` already handle a missing write grant.

So the new work is capture UI, recognition, portion estimation, and a review screen. Not plumbing.

## User Flow

1. **Start.** A *Photo a meal* action on the food entry screen (and an optional manual-entry widget). The camera permission is requested on the tap, not on screen entry.
2. **Capture.** CameraX `ImageCapture` into a file in app-private storage, or the system photo picker. Nothing is uploaded; nothing is written to shared media storage.
3. **Analyze.** On-device only. The result is a list of candidate foods with a confidence and an estimated mass, never a finished record.
4. **Review.** One row per recognized item: name, editable grams, and every nutrient the table knows for it, each row editable. A portion slider steps in sensible amounts (10 g, then 5 g above 100 g). Items can be deleted, renamed, or added by hand from the catalog. The screen says plainly that a photo cannot weigh food and that the estimate is a starting point.
5. **Save.** Each confirmed item becomes one Health Connect nutrition record through the existing write path, named after the food, at the chosen time, matching how a catalog portion is logged today. Unknown nutrients are left absent rather than written as zero.
6. **Afterwards.** The photo is deleted once the save completes unless the user keeps it; a kept photo stays app-private and is reachable from the entry.

## Recognition, Offline

The viable offline stack is a quantized image classifier plus a local per-100 g nutrient table:

- **Model:** a small MobileNet-class classifier converted to TFLite, using the `org.tensorflow.lite` (or LiteRT) interpreter. Bundled, not downloaded, because a download needs the network permission the app does not have.
- **Labels:** the model's own class list, mapped to entries in a bundled nutrient table. Food-101's 101 classes are the usual starting point, and their licence (research use) is the first thing to check before shipping anything.
- **Table:** per-100 g values for energy, protein, carbohydrate, fat, fibre, sugar, saturated fat and sodium at minimum, from a source whose licence allows redistribution in a GPL-compatible app. A public-domain or permissively licensed composition table is required; most national food-composition databases are not redistributable as-is.
- **Portion:** no classifier weighs food. Estimate mass from the plate area the food occupies relative to a reference (a default 100 g serving, adjusted by the user), or skip estimation entirely and open the row at a neutral default the user corrects. Report a range, not a single confident number.

What this stack cannot do, and the review screen should admit:

- Only foods in the model's class list are recognized. Long-tail and regional dishes are not.
- Mixed plates (stew, salad, sandwich) are recognized as one label at best.
- Preparation changes the numbers more than the classifier knows: fried versus boiled, sugared versus plain.
- Nothing about the photo can confirm the nutrients; the table and the portion decide them.

## Architecture Shape

Following [the feature playbook](../engineering/feature-playbook.md):

- **`:domain`** — pure code, testable without Android: `domain/meal/` holding the label-to-food mapping, per-100 g scaling, portion range arithmetic, and the "which nutrients are absent" model. No Android imports, no inference.
- **`:app`** — `features/mealphoto/` for the capture screen, review screen, `MealPhotoViewModel`, and the `FoodPhotoAnalyzer` interface with its TFLite implementation and the bundled asset. The ViewModel owns the analyzer result, the editable rows and the save calls; composables hold no coroutines and no model handle.
- **No new Health Connect reader.** Recognition produces a `NutritionWriteRequest`; the existing repository writes it. No second write path, no second permission gate.
- **No Room change in phase 1.** A pending draft lives in the ViewModel and the photo in a private file. Keeping a library of past photos would need a table and therefore `MIGRATION_18_19` and a version bump; do that only when it is actually wanted.
- **No new foreground service.** Analysis is a bounded operation on one photo and belongs in the screen's scope, not the single foreground slot.

## Strings And Translations

Roughly 25 to 30 new strings (permission rationale, capture, analyzing, low confidence, the portion caveat, "not weighed", delete and keep photo, errors), each going to `values/strings.xml` and to all sixteen `values-*/strings.xml`, checked by [`verify-translations.py`](../../scripts/verify-translations.py). The caveat wording is the part that must not be machine-paraphrased: it carries the "this is an estimate, not a measurement" claim.

## Privacy

- The photo never leaves the phone, is never written to shared storage, and is never attached to a report, crash draft or log line. Reports stay structure, not data: a count of items and a reason, never a food name or a time.
- If the cloud route is ever chosen, it needs its own consent screen, a policy date bump, a Play data-safety declaration and a visible per-photo indicator. None of that exists today, and the manifest would have to stop removing `INTERNET`.

## Tests Worth Having

- Domain: scaling per-100 g to a portion, portion range bounds, absent-versus-zero nutrients, a label with no table entry.
- Analysis: every model label resolves to a table row or is explicitly unmapped; no duplicate labels.
- Save: one item writes one record through the existing repository; a refused write surfaces `ScreenError.PermissionDenied` and not a parsed exception message.
- Privacy: the redactor and report paths hold no food name and no photo path.

## Open Decisions

1. **Cloud or on-device.** On-device keeps every existing promise. Cloud recognizes anything and costs the privacy model; choose it deliberately or not at all.
2. **Model and table licences.** Both must permit redistribution in this app; this is the likeliest place the feature dies.
3. **One record per item, or one per meal.** Per item matches today's per-portion logging and keeps per-food history; per meal is simpler to read in Health Connect.
4. **Photos kept or deleted.** Deletion after save is the safer default; keeping them needs storage, a table and a delete flow.
5. **Scope of the nutrient table.** "Add everything" is bounded by what the table carries; absent fields must stay absent.

## Suggested Phases

- **Phase 1 — capture and review, no model.** Photo capture, the review screen, editable rows, and saving through the existing write path, with the user choosing foods from their own catalog. Ships offline, adds no dependency, and is useful on its own.
- **Phase 2 — on-device recognition.** TFLite dependency (lockfiles regenerated), bundled model and table, portion estimate, confidence display.
- **Phase 3 — docs.** Promote the built behavior into [Features](../../Features.md), the [feature map](../features/feature-map.md) and a feature page, then retire this note.
