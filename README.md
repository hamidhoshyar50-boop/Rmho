<div align="center">

# Rmho

**Rokoi Monster Hunter Outlanders — a fan-made guide & coach app**

[![Build](https://github.com/hamidhoshyar50-boop/Rmho/actions/workflows/build.yml/badge.svg)](https://github.com/hamidhoshyar50-boop/Rmho/actions/workflows/build.yml)
[![Platform](https://img.shields.io/badge/platform-Android-brightgreen)](#)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-7F52FF)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

Adventurers · Monsters · Team comps · Rotations · **Live rotation coach**

</div>

---

## About

**Rmho** is an unofficial, fan-made companion guide for **Monster Hunter Outlanders**
(TiMi Studio Group / Capcom, published by Garena), built for the Rokoi hunter
community. Everything a hunter needs between hunts — who to bring, what a monster
is weak to, which parts to break, and how to play the rotation — in one fast,
offline-first app.

The app's content lives in a versioned JSON data bundle, **separate from the code**:
new Adventurers, monsters, team comps, rotations and images are delivered as
*data updates* — no new APK install required.

## What's in v0.7.1

- **Hotfix — hunt analysis freeze fixed.** v0.7.0's Coach analysis could hang at "Analysing… 99%" (and trigger an Android "isn't responding" dialog) when the end of a hunt contained only button presses the chosen rotation never expects — the step aligner looped forever on that input, on the main thread. The aligner now always terminates: a rotation cycle that matches nothing ends the analysis and counts as not-reached (never penalised), alignment runs off the main thread, and a JVM regression suite covering the exact hang repro runs in CI before every build.
- Data stays v5 — no data change.

## What's in v0.7.0

- **Coach — Phase 2: Analyse a hunt** — import a screen recording of a hunt and the Coach compares your on-screen button inputs against the rotation you followed: matched steps, **skipped steps**, out-of-order signals and rough timing, plus full-cycle counts across the whole hunt.
- **On-device video analysis** — the recording is decoded sequentially on the phone (MediaCodec; nothing is uploaded) and button presses are detected by frame-differencing inside the action-button cluster, calibrated on a real 1280×576 hunt recording. Red screen-flash spikes are reported as **part-break moment candidates** (the moment, never which part).
- **Honest by design** — skill steps match any skill button (skill icons change per weapon), hold length is not measured yet, and the report states plainly what it cannot judge: positioning, i-frames and damage. Steps the recording never reached are not penalised.
- Recording inside the app (MediaProjection) is deliberately deferred — import-only in v1 keeps the feature free of capture consent friction and foreground-service failure modes.
- Data stays v5 — no data change was needed.

## What's in v0.6.0

- **The Fated Adventurer, complete** — your own hunter gets a full profile: bilingual description, rarity (SSR) and max stats (Attack 200 · Defense 490), signature traits (Masterful Adventurer, Daytime Hunter, Pioneer Follow-Up, the affinity passive), the weapon-system notes (switch on the equipment screen; the only Charge Blade user; every Heavy Bowgun ammo), the Insight progression system with the top Insight per weapon, full strengths & weaknesses, best class and best starter weapons.
- **Ultimates by Type** — Fated's Ultimate is decided by his Type (class), never by his weapon, so the profile shows all three forms side by side: Assault — Explosive Shot: Spikes, Disruptor — Explosive Shot (flash), Support — Explosive Shot (healing), with effects, cooldown and energy costs.
- **Coach: Fated × all 8 weapons** — 8 new step-by-step rotations (55 steps) from Game8's main-character (MC) weapon guides, each with English + Persian coach tips. The Coach now holds **21 rotations** (140 steps).
- **Grouped Fated coach UX** — one Fated entry in the Coach list opens the trainer with an in-place weapon selector covering all 8 weapons, keeping the character → weapon → rotation path together; Watch / Follow / Test work exactly as before.
- Data v5: ships in the bundle and as a remote data update.

## What's in v0.5.0

- **Rotation Coach — Phase 1** — the Coach tab is live: the published best rotation for all **13 Adventurers** (85 steps, sourced from Game8), each with a coach tip in English and fluent Persian.
- **Three practice modes** — *Watch* (the rotation plays out step by step), *Follow* (tap the practice pad in order, guided), and *Test* (perform the whole rotation from memory and get an accuracy + timing report: Perfect ≤ 0.7 s, Good ≤ 1.6 s per step).
- **Rotations are remote data** (`rotations` in `data.json`, data v4) — rotation fixes and new guides ship without a new APK.
- The Fated Adventurer has no published per-Adventurer rotation (only per-weapon guides exist), so the Coach honestly covers the 13 named Adventurers.

## What's in v0.4.0

- **New Home** — hero header, live stats, an Adventurer of the Day and a Monster of the Day, and quick links.
- **Side drawer menu** (top hamburger): **Settings** (Persian-descriptions toggle, manual data refresh, image-cache clear), **Update** (with an availability badge), and **About** (sources + disclaimer).
- **Portraits with identity** — Adventurers sit in gold-gradient circles with a gold ring; Monsters in deep-red circles with a red ring.
- **Every monster has art now** — official icons/art for all 34 monsters, served via the same CDN pipeline.
- **Persian, rewritten** — all Persian descriptions and team strategies were rewritten from scratch in fluent, natural Persian (no translationese, no jargon labels).

## What's in v0.3.0

- **Full data set** — all 13 launch Adventurers plus the Fated Adventurer, each with an official portrait, class, weapon, element, full skill kit and Ultimate.
- **34 monsters** — the 21 launch monsters plus all 13 Radiant forms, with top elemental weaknesses, breakable parts and drop tables where databases publish them (Rathalos, Ruger Aidal).
- **A counter team for every monster** — 6 community-sourced squads (element-resonance comps, incl. an all-free starter) plus a derived counter comp per monster, built from its published weakness.
- **Circular portraits** everywhere, served from this repo through the jsDelivr CDN and cached on-device.
- **Data lives in [`data/`](data/)** (`data.json` + `version.json` + `images/`): the app fetches it on launch, so data updates ship without a new APK; APK updates are offered in-app.

Data sources: mhodb, GameWith and Game8. Weakness tables list the top published weakness only — where a database has not published data yet (e.g. Nergigante), the app says so instead of guessing.

## Features

**Available in the foundation build**
- Adventurer database with class filters (Assault / Disruptor / Support),
  weapon & element chips, skills, ultimate, strengths and weaknesses
- Dark Material 3 design system ("hunter" theme: deep forest green, jade, amber)
- Offline-first: bundled starter data works with no connection
- Remote data bundle loading (version-checked) ready for live updates

**Roadmap**
- [x] Full roster: all 13 launch Adventurers with photos
- [x] Monsters: elemental weakness tables, breakable parts, loot & farming sources
- [ ] Items & materials database with search
- [x] Team compositions per monster and play style
- [x] Rotation guides as structured, step-by-step timelines
- [x] Fated Adventurer: complete profile + Coach rotations for all 8 weapons
- [x] **Coach — phase 1:** rotation practice / trainer mode (Watch / Follow / Test)
- [x] **Coach — phase 2:** fight-recording analysis that flags rotation mistakes
      (v1: import a screen recording; on-device button-press detection + step alignment)
- [ ] **Coach — phase 3:** live on-screen overlay guide during hunts
      (display-only manual-sync design; you always press the buttons yourself)
- [x] In-app update check for new APK releases

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Min / target SDK | 26 / 34 (Android 8.0+) |
| Data | Versioned JSON bundle (kotlinx.serialization), Coil for images |
| Build | Gradle 8.7 · GitHub Actions (cloud build, APK artifact per run) |

## Data updates

The app reads a `DataBundle` (`dataVersion`, adventurers, monsters, team comps,
rotations). On launch it can check a remote `version.json`; when a newer bundle
exists it downloads the JSON + images and caches them on device. Content can grow
daily without touching the Play-Store-style release cycle — see
`app/src/main/java/ir/rokoi/rmho/data/` for the model and repository.

## Building

Every push to `main` builds a debug APK on GitHub Actions — grab it from the
run's **Artifacts** (`rmho-debug-apk`).

Local build (Android Studio or CLI):

```bash
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

## Project structure

```
app/src/main/java/ir/rokoi/rmho/
├── MainActivity.kt          # entry point
├── RmhoApp.kt               # Application
├── data/                    # models + repository (remote-updatable bundle)
├── ui/                      # screens: home, adventurers, monsters, teams, coach
│   └── theme/               # Material 3 theme (colors, type)
└── assets/data.json         # bundled starter dataset
```

## Contributing

Built for the community — data corrections (weaknesses, drops, rotations) are
the most valuable contributions. Open an issue or a pull request; keep game data
factual and sourced (see [CONTRIBUTING.md](CONTRIBUTING.md)).

## Disclaimer

Unofficial fan project. **Monster Hunter** and **Monster Hunter Outlanders** are
trademarks of Capcom Co., Ltd. This project is not affiliated with, endorsed by,
or sponsored by Capcom, TiMi Studio Group, or Garena. Game data is factual
reference information compiled from community sources.

## License

Code is released under the [MIT License](LICENSE).
