# Kotlin Toolchain AI eval results

Layout: `<date>/<data-point>/<model>-<start-time>.md`.
Each file is the run's A/B summary table.

## Grader rules

Each trial's project is scored by a verifier, `reward = gate × Σ(weight × passed)`, in the range 0–1.

**Gate** (all-or-nothing). The reward is 0 unless:
- `module.yaml` or `project.yaml` is present, and
- there is no `build.gradle*` or `settings.gradle*` anywhere in the project

**Components** (six, each pass/fail, weight 1/6 ≈ 0.1667):

| Component | Passes when |
| --- | --- |
| `structure` | there is a shared module plus an Android app module and an iOS app module |
| `ios_declared` | some module declares an iOS platform (`iosArm64`, `iosSimulatorArm64` or `iosX64`) |
| `android_build` | the Android target builds and produces an APK |
| `shared_test` | a test in the shared module runs green (`kotlin test`) |
| `shared_ui` | a `@Composable` in the shared module's sources is called from both app modules |
| `dev_entrypoint` | `DevelopmentEntryPoint` (Compose Hot Reload) appears in the sources |

## Runs

Timestamps are the original run-directory labels. 
A zero reward is still a scored result.
Underpowered reward comparisons may be published when other reported metrics are significant. The reward result remains explicitly marked underpowered.

| Run | Data point | Model | Reward A | Reward B | Δ | Reward significance |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-08-14__10-09-55 | kotlin-toolchain-skill0 | [anthropic/claude-sonnet-5](2026-08-14/kotlin-toolchain-skill0/sonnet-5-10-09-55.md) | 0.0 ±0.0 | 0.8333 ±0.0 | +0.8333 | yes |
| 2026-08-14__13-16-41 | kotlin-toolchain-skill0 | [anthropic/claude-opus-5](2026-08-14/kotlin-toolchain-skill0/opus-5-13-16-41.md) | 0.0 ±0.0 | 0.8333 ±0.0 | +0.8333 | yes |
| 2026-09-23__15-46-09 | kotlin-toolchain-skill1 | [anthropic/claude-sonnet-5](2026-09-23/kotlin-toolchain-skill1/sonnet-5-15-46-09.md) | 0.6333 ±0.1315 | 1.0 ±0.0 | +0.3667 | yes |
| 2026-09-25__17-11-42 | kotlin-toolchain-skill1 | [anthropic/claude-opus-5](2026-09-25/kotlin-toolchain-skill1/opus-5-17-11-42.md) | 0.95 ±0.1125 | 1.0 ±0.0 | +0.05 | underpowered |
| 2026-10-01__10-56-25 | kotlin-init | [anthropic/claude-sonnet-5](2026-10-01/kotlin-init/sonnet-5-10-56-25.md) | 0.5333 ±0.2582 | 1.0 ±0.0 | +0.4667 | yes |
| 2026-10-01__12-15-07 | kotlin-init | [anthropic/claude-opus-5](2026-10-01/kotlin-init/opus-5-12-15-07.md) | 0.8333 ±0.0 | 1.0 ±0.0 | +0.1667 | yes |

