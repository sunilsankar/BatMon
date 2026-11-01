# BatMon Implementation Plan

## Phase 1 — Foundation
- Initialize Kotlin Android project with minimum SDK 28.
- Configure Gradle Kotlin DSL and documented JDK/SDK.
- Add Compose Material 3 theme and responsive navigation.
- Build dashboard/settings skeleton.
- Add build and test instructions.

**Exit criteria:** clean build; dashboard renders on compact and expanded layouts.

## Phase 2 — Battery monitoring
- Implement BatteryRepository and supported battery fields.
- Implement threshold engine and persisted re-arming state.
- Add local notifications and permission handling.
- Persist settings with DataStore.
- Add unit tests for thresholds, startup evaluation, and duplicate suppression.

**Exit criteria:** deterministic threshold tests pass.

## Phase 3 — Background reliability
- Add low-power mode.
- Add optional foreground service only where permitted and explicitly enabled.
- Handle process recreation, reboot restrictions, and stale data.
- Add monitoring diagnostics.

**Exit criteria:** limitations are documented and background behaviour is tested on representative versions.

## Phase 4 — MQTT/Home Assistant
- Add broker configuration, TLS, credentials, connection test, and reconnection.
- Publish state and availability.
- Implement Home Assistant MQTT discovery.
- Test stable entity IDs and stale-state handling.

**Exit criteria:** expected entities appear in Home Assistant using a test broker/instance.

## Phase 5 — Webhooks
- Add per-event configuration.
- Implement URL validation, secure HTTP, timeouts, payload templates, and test delivery.
- Implement durable queue and bounded exponential retry.
- Add event IDs and sanitized delivery history.

**Exit criteria:** success, transient failure, permanent failure, and offline queue tests pass.

## Phase 6 — Charging automation
- Supply example Home Assistant automation YAML.
- Add stale-data checks, manual override, unavailable-switch handling, and restart recovery.
- Test simulated 45% ON and 80% OFF transitions.

**Exit criteria:** automation does not switch based on stale data and avoids repeated commands.

## Phase 7 — Polish and release
- Complete phone/tablet responsive layouts.
- Add light/dark themes and accessibility.
- Run unit, integration, and UI tests.
- Build a debug APK and record the actual build result.
- Finish setup and troubleshooting docs.

## Working rules
After each phase, run tests, fix regressions, update docs, and record remaining limitations. Do not skip test failures to advance.
