# Changelog

All notable changes to Capella Agent are documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) · Semver: v2.

## [Unreleased] — Security Hardening Sprint

Closes all CRITICAL + HIGH + MEDIUM findings from the three-agent security
audit (credentials, tool authorisation, injection surfaces) plus 9 follow-up
adversarial findings (N1-N9). See `SECURITY.md` for the full posture summary
and `docs/SMOKE_TEST_CHECKLIST.html` section 32 for the verification checklist.

### Security
- **Credentials:** Gemini API key moved from URL query string to
  `x-goog-api-key` header. Introduced `AuthToken` wrapper with redacted
  `toString()`. Preferences page no longer pre-populates the key field (shows
  a saved-placeholder pattern instead). `setApiKey` now throws on failure
  instead of silently losing the key.
- **Error handling:** New `ErrorMessageFilter` maps provider exceptions to
  safe user-facing messages. `ProviderErrorExtractor` parses error code/type
  only — never `error.message`. Response bodies capped to 500 chars in logs.
- **XSS hardening:** New `HtmlSanitizer` (tag whitelist, strips `on*`
  handlers, blocks `javascript:`/`vbscript:`/`data:` URLs). Strict UUID
  regex validation before element-link generation. Content-Security-Policy
  meta tag locks down the chat Browser widget.
- **Path traversal:** New `PathValidator` (canonicalise + workspace
  containment + NOFOLLOW_LINKS + extension whitelist) applied to ReqIF
  import, Excel import, and diagram export.
- **Dependency upgrades:** Apache POI 5.2.5 → 5.3.0, commons-compress
  1.25.0 → 1.27.1 (closes CVE-2024-25710, CVE-2024-26308), xmlbeans
  5.2.0 → 5.3.0.
- **XML parsing:** `FEATURE_SECURE_PROCESSING` on, explicit JAXP entity
  limits, NOFOLLOW_LINKS on every file open. `.reqifz` disabled pending a
  ZipSlip-safe extractor.
- **POI limits:** Set once at bundle activation (`IOUtils.setByteArrayMaxOverride`,
  `ZipSecureFile.setMinInflateRatio(0.01)`, max entry/text size).
- **Cost & rate controls:** Per-session token budget (default 1M), per-day
  hard cap (default 10M UTC) via new `DailyTokenLimiter`, per-provider
  circuit breaker (5 errors / 120s → open 5 min, exponential backoff) via
  new `ProviderCircuitBreaker`, iteration hard ceiling of 50, per-tool-result
  byte cap 200K.
- **Consent UI:** New native SWT `WriteConsentDialog` — Deny is the default
  button, window-X = deny, LLM reasoning rendered as plain text with an
  "untrusted" label, destructive tools disable the "remember my choice"
  checkbox. Wired into `ChatSessionController` via the new `ConsentManager`
  interface with `SwtConsentManager` implementation.
- **Staging area:** Full 128-bit UUID diffIds (was 32-bit substring).
  60-second background sweeper for expired entries. 100-entry LRU cap.
  Project-name check on apply prevents cross-project replay.
- **Prompt injection:** Added bracket-form, markdown-heading, and
  line-leading role-prefix detection patterns to `ToolResultSanitizer`.
- **Audit log:** Size-based rotation (10 MB × 10 files), rolling HMAC-SHA256
  chain (key in secure preferences) for casual-tampering detection, secret
  scrubbing (JSON keys, `Authorization: Bearer`, `x-goog-api-key` etc.)
  before write, UTF-8-safe argument truncation.
- **MATLAB bridge:** New `validateModelPath` — extension whitelist
  (`.slx`/`.mdl`), workspace containment, symlink rejection, MATLAB script
  metacharacter denial. Every invocation is audited.

### Fixed
- `TestConnectionPage` stale `sendMessages` API call updated to `provider.chat`.
- `ChatJob` `toolDesc.getCategory().name()` incorrect call removed
  (`getCategory()` returns String).

## [1.0.0.beta1] — 2026-04-07

First tagged beta. Side-loaded release (no Marketplace listing yet, no
code-signing certificate yet). Ships the 6-week production hardening track
defined in `docs/PRODUCTION_PLAN_WEEKS_1-6.md` after the 4-reviewer audit.

### Added

- **Tycho Surefire test infrastructure** (`com.capellaagent.core.tests`
  fragment plugin). Eclipse Capella 7.0.1 ships JUnit Jupiter 5.9.2 bundles
  so no target platform extension was needed. 167 unit tests pass.
- **`ChatSessionController`** — pure-Java extraction of the chat
  orchestration loop from `ChatJob`. Builder pattern, lifecycle Listener,
  cancellation token. Testable without Eclipse runtime.
- **`TokenEstimator`** — char-density heuristic with 3-tier confidence
  (HIGH/MEDIUM/LOW) and per-tier safety multipliers for hard-cap checks.
- **`HistoryWindow`** — byte-budget sliding window with
  `tool_result` orphan prevention. Fixes the #1 cause of "Invalid request"
  400 errors from OpenAI-compatible providers when tool_calls and their
  replies get out of sync.
- **`LlmUsage`** — normalized usage record (input/output/cached/reasoning
  tokens + EXACT/ESTIMATED source).
- **`ILlmProvider.parseUsage()`** — new default method. Overridden by
  `OpenAiCompatibleProvider` (inherited by 7 providers), `ClaudeProvider`,
  and `OllamaProvider` to parse the three distinct usage JSON shapes.
- **`ToolResultSanitizer`** — prompt injection defense. Wraps every string
  field in tool results in `<untrusted>...</untrusted>` markers, strips
  zero-width/bidi steganographic characters, detects 11 common injection
  phrases including fake system tags and "ignore previous instructions".
  Wired into `ChatSessionController` by default.
- **`WriteToolGate`** — access-control gate for write tools. Supports
  read-only mode (with a by-name safety-net check), destructive-tool
  quarantine, admin blocklist, and consent-required decisions when
  suspicious context is detected.
- **`ConversationStore` + `JsonFileConversationStore`** — session
  persistence layer in a new `com.capellaagent.core.session.persistence`
  package. Atomic writes, schema versioning, project-scoped isolation,
  safe path sanitization. 13 unit tests.
- **`SessionState`** enum — foundation for 3-state crash recovery
  (CLEAN / IN_FLIGHT_NO_WRITES / IN_FLIGHT_WITH_WRITES / CRASHED).
- **`ConversationSession.restore()`** factory — rebuilds a session from
  persisted JSON while preserving the original session ID.
- **`ConversationSession.SCHEMA_VERSION`** — forward-compat field for
  the persistence layer.
- **`AgentMode`** enum — session profiles with system prompt, tool
  categories, and starter prompts. Ships with `GENERAL` and `SUSTAINMENT`
  modes. Sustainment prompt explicitly forbids UUID hallucination.
- **`AtaSpec100Catalog`** — 30 ATA Spec 100 chapters with a 60+ keyword
  index for classifying free-text fault descriptions. Zero external deps,
  works offline.
- **`SlashCommandRegistry`** — 9 default slash commands (`/help`, `/tools`,
  `/clear`, `/export`, `/general`, `/sustainment`, `/diff`, `/undo`,
  `/cancel`) with aliases and autocomplete suggestion.
- **`TokenUsageTracker`** — thread-safe singleton with per-(source,provider)
  buckets, `LongAdder` counters, observer pattern. Lives in a new
  `com.capellaagent.core.metering` package (architect A1 recommendation).
- **`WelcomeWizardModel`** — pure-Java data model for the first-launch
  wizard. Pages: PICK_PROVIDER, ENTER_API_KEY (skipped for offline),
  TEST_CONNECTION. 5 provider options including GitHub Models and Ollama.
- **`LICENSE`** — EPL 2.0 (matches Capella ecosystem)
- **`SUPPORT.md`** — bug reporting template, security disclosure path,
  compatibility matrix, privacy guidance
- **`demo/IFE_MODEL_INVENTORY.md`** — catalog of elements in the IFE
  sample model with revised demo symptoms. The original plan referenced
  "nose wheel landing gear" which does NOT exist in the IFE model.
- **`demo/SUSTAINMENT_DEMO_SCRIPT.md`** — 5-minute written walkthrough
  for the sustainment engineering demo (Seat TV rebooting scenario).
- **`docs/PLAN_REVIEW_SYNTHESIS.md`** — 44-finding audit across 4
  reviewers (Software Architect, AI Engineer, Staff Engineer, UX Architect)
  and the revised day-by-day execution plan.
- **`docs/STRATEGIC_PLAN.md` + `docs/STRATEGIC_PLAN.html`** — the master
  2-track roadmap (feature roadmap + hardening) visible from
  `docs/index.html`.
- **`docs/PRODUCTION_PLAN_WEEKS_1-6.md` + `.html`** — the 6-week execution
  detail with a revision banner pointing to the synthesis.
- **`docs/SMOKE_TEST_CHECKLIST.md`** — 18-section manual smoke test with
  the 8 additional scenarios from the Code Reviewer's C11 finding.
- **`docs/KNOWN_LIMITATIONS.md`** — 11 documented limitations with
  planned fixes and version targets.

### Fixed

- **Tool execution loop** — assistant tool-call messages are now persisted
  to the session via `addAssistantToolCalls()`, fixing the bug where the
  LLM would silently re-call the same tool 6 times in a row because it
  never saw its own previous call in history. (Was fixed in commit
  0fb2a67 before Week 1 but documented here for completeness.)
- **Build system** — moved `icons/` out of `bin.includes` / `src.includes`
  in three UI bundles so the build doesn't fail on empty icon folders
  (real icons land in the polish phase).

### Changed

- **Test fragment packaging** — both `com.capellaagent.core.tests` and the
  main build modules now use eclipse-test-plugin vs eclipse-plugin
  appropriately. Stale p2 cache entries (`com.capellaagent.core-1.0.0.202603260245.jar`)
  from prior Capella installs are documented as a known build-environment
  gotcha in the commit messages.

### Security

- **Prompt injection defense** enabled by default in `ChatSessionController`.
  Wrap + detect + gate pipeline: tool results are sanitized before feeding
  to the LLM, suspicious content is detected with 11 regex patterns, and
  subsequent write-tool calls in the same turn are gated for consent.
- **Destructive tool quarantine**: `delete_element`, `merge_elements`,
  `batch_*`, `transition_*`, and `auto_transition_all` always require
  consent in `WriteToolGate`.

### Tests

- **Before 6-week track:** 0 automated tests
- **After 6-week track:** 167 tests pass, 0 failures, 0 errors
  - Session: 51 (ConversationSession, ChatSessionController, AgentMode,
    SlashCommandRegistry, JsonFileConversationStore)
  - LLM: 43 (LlmMessage, LlmUsage, TokenEstimator, HistoryWindow, ParseUsage)
  - Security: 25 (ToolResultSanitizer, WriteToolGate)
  - Tools: 15 (ToolParameter, ToolResult)
  - Sustainment: 11 (AtaSpec100Catalog)
  - Metering: 10 (TokenUsageTracker)
  - Config: 12 (WelcomeWizardModel)

### Known Limitations (see `docs/KNOWN_LIMITATIONS.md`)

1. Multi-tool compound undo — each tool commits separately
2. Token estimator uses char-density heuristic, not real tokenizer
3. Per-turn cost display not yet shipped
4. No polished video demo (written walkthrough only)
5. `org.eclipse.ui.intro` intentionally not used (permanent design)
6. `BrandedErrorDialog` only covers the auth-error path
7. Honest coverage target reset to ~50% (from aspirational 60%)
8. No code-signing certificate
9. Teamcenter PLM integration bundle ships with placeholder tools
10. MATLAB Simulink integration requires user-supplied engine JAR
11. No telemetry backend
