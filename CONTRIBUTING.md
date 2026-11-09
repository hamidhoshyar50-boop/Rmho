# Contributing to Rmho

Rmho is a community guide — accurate data is the whole point.

## Game data

- Data lives in `app/src/main/assets/data.json` (starter bundle) and follows the
  models in `app/src/main/java/ir/rokoi/rmho/data/Models.kt`.
- Every value (elemental weakness, drop, skill effect) should be verifiable
  in-game or from a reputable community source — note the source in your PR.
- Write original descriptions. Do not copy text or art from official sites,
  wikis or other guides; facts are fine, their wording and images are not.

## Code

- Kotlin + Jetpack Compose, Material 3. Match the existing structure
  (data / ui / ui.theme) and keep screens stateless where practical.
- English UI only.
- Run `./gradlew assembleDebug` before opening a PR.

## Bugs & ideas

Open an issue with the app version, your device/Android version, and steps to
reproduce.
