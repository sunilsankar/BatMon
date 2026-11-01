# OpenCode Instructions — BatMon

## Source of truth
Read `SPECIFICATION.md` before implementation. It is the product source of truth. Keep `ARCHITECTURE.md`, `IMPLEMENTATION_PLAN.md`, and `README.md` aligned with implementation.

## Engineering rules
- Use Kotlin, Jetpack Compose, Material 3, and minimum SDK 28.
- Prefer stable compatible dependencies.
- Keep UI, battery logic, persistence, MQTT, webhooks, and background execution separated.
- Composables must not perform network or persistence operations directly.
- Use ViewModels and repositories for application state.
- Do not add unrelated permissions or collect unnecessary personal data.
- Never hard-code credentials, device IDs, or event timestamps.
- Never log secrets or disable TLS verification.
- Keep MQTT and webhooks independently usable.
- Home Assistant, not the Android app, owns smart-plug switching.
- Treat stale battery data as unsafe for automated switching.
- Do not promise exact WorkManager schedules or uninterrupted monitoring after force-stop.
- Make every visible control functional; no placeholder buttons in completed screens.
- Support phones/tablets and portrait/landscape layouts.
- Maintain accessibility labels and light/dark themes.

## Workflow
1. Inspect the repository before editing.
2. Read the relevant spec sections.
3. Implement one phase from `IMPLEMENTATION_PLAN.md` at a time.
4. Add/update tests with each feature.
5. Run relevant tests and builds; fix failures before proceeding.
6. Update documentation when behaviour changes.
7. Report exactly what was tested and what remains unverified.
8. Never claim an APK exists unless it was actually built successfully.

## Quality bar
Prefer simple, maintainable code over speculative abstraction. Validate thresholds and configuration at boundaries. Use bounded retries, stable entity IDs, unique webhook event IDs, sanitized errors, and explicit stale-data handling.
