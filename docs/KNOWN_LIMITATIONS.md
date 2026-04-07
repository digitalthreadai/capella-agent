# Capella Agent — Known Limitations (1.0.0.beta1)

> **Status:** Beta release, side-loaded zip from GitHub Releases
> **Audience:** pilot customers and early adopters

Every item below was explicitly cut from the 6-week production plan after the 4-reviewer audit. Each entry describes the limitation, why it shipped this way, and what's planned.

---

## 1. Multi-tool compound undo

**Limitation:** When the agent performs multiple model changes in a single turn (e.g., "create three functions and link them"), each tool call commits its own `RecordingCommand`. A single `Edit → Undo` only reverses the most recent one. Users need to undo N times to fully revert a multi-tool turn.

**Why this ships:** The fix (a single `CompoundCommand` wrapping each LLM turn) is ~5 days of risky work in the chat orchestration loop. The staff reviewer (C10) recommended shipping this as a documented limitation and deferring the real fix to v1.1.

**Planned fix:** v1.1 — compound-command-per-turn in `ChatSessionController`.

---

## 2. Token estimation accuracy

**Limitation:** `TokenEstimator` uses a character-density heuristic. Real OpenAI tokens are computed via `cl100k_base` / `o200k_base`, and Anthropic has its own tokenizer. The estimator can be off by 15-30% depending on content.

**Why this ships:** Integrating `jtokkit` as an OSGi bundle is a multi-day side quest. The heuristic has a built-in safety margin (1.30× LOW / 1.15× MEDIUM / 1.05× HIGH confidence) and the `fitsWithin()` check defaults to 85% of the provider cap.

**Workaround:** When a provider returns a real `usage` block, `TokenUsageTracker` records the exact numbers. The estimate is only used before the first call.

**Planned fix:** v1.2 — jtokkit integration.

---

## 3. Per-turn cost display

**Limitation:** The cost meter shows session aggregate, not per-turn cost.

**Planned fix:** v1.1 — per-turn delta in the token strip.

---

## 4. No polished video demo

**Limitation:** Ships with a written walkthrough (`demo/SUSTAINMENT_DEMO_SCRIPT.md`) but no edited video with voiceover.

**Why this ships:** A polished 5-minute demo is 3-5 days of engineering time. The staff reviewer (C1) noted that Fortune-100 buyers prefer live Zoom demos over YouTube videos.

**Planned fix:** Week 8+ post-beta1.

---

## 5. `org.eclipse.ui.intro` not used

**Limitation:** We ship a plain `WizardDialog` on first chat-view open, not an integration with Eclipse's product intro framework. "Help → Welcome to Capella" still shows Capella's own intro.

**Why this ships:** `org.eclipse.ui.intro` only supports one active intro per product. Overriding Capella's would break its own welcome page (Code Reviewer C3).

**Planned fix:** None. This is the correct permanent design.

---

## 6. BrandedErrorDialog only covers the auth-error path

**Limitation:** Only the authentication failure path uses the branded dialog. Other error paths still use raw `MessageDialog.openError()`.

**Why this ships:** Capella's own `StatusManager` reports errors via a different code path; full migration risks collision (UX D6, Code C9).

**Planned fix:** v1.1 — full audit and migration.

---

## 7. Realistic coverage on `core`

**Limitation:** The original plan targeted 60% line coverage on `com.capellaagent.core` by Week 2. This release ships with ~50% coverage on pure-Java paths and 0% on Capella-touching code.

**Why this ships:** Honest number — hitting 60% required mocked-Capella unit tests (false confidence) or a full integration test fixture (1+ week of work). See Code Reviewer C6.

**Planned fix:** v1.1 — headless Capella test fixture for `CapellaModelService`.

---

## 8. Code signing

**Limitation:** Plugin JARs are not signed with a commercial certificate. Windows SmartScreen will warn.

**Why this ships:** $400-$600/year cost deferred to v1.0 real release.

**Workaround:** Side-load from GitHub Releases. Right-click → Properties → Unblock.

**Planned fix:** v1.0 real release.

---

## 9. Teamcenter PLM integration

**Limitation:** The `com.capellaagent.teamcenter` bundle exists and builds, but the actual Teamcenter REST calls are NOT implemented. The 7 placeholder tools will return "not implemented".

**Why this ships:** Teamcenter integrations are customer-specific. Generic implementations break in 90% of environments.

**Planned fix:** Custom integration engagements per customer. Polarion REST is a better first cross-tool bridge (roadmap Phase 4).

---

## 10. MATLAB Simulink integration

**Limitation:** The `com.capellaagent.simulation` bundle builds but MATLAB Engine API for Java is not bundled (MATLAB licensing).

**Planned fix:** v1.2 — bring-your-own MATLAB guide.

---

## 11. No telemetry

**Limitation:** Zero usage telemetry sent to any backend. No visibility into crash rates, tool usage, or errors across the beta fleet.

**Planned fix:** v1.1 — opt-in telemetry with privacy notice.

---

## Summary table

| # | Limitation | Planned fix |
|---|---|---|
| 1 | Multi-tool compound undo | v1.1 — `CompoundCommand` per turn |
| 2 | Token estimator accuracy | v1.2 — jtokkit for OpenAI family |
| 3 | Per-turn cost display | v1.1 — token strip delta |
| 4 | No polished video | Week 8+ (post-beta1) |
| 5 | Wizard vs `org.eclipse.ui.intro` | Permanent (this IS the design) |
| 6 | BrandedErrorDialog coverage | v1.1 — full migration |
| 7 | Honest core coverage | v1.1 — integration test fixture |
| 8 | Code signing | v1.0 real release |
| 9 | Teamcenter integration | Per-customer engagement |
| 10 | MATLAB integration | v1.2 — user supplies engine JAR |
| 11 | Telemetry | v1.1 — opt-in |
