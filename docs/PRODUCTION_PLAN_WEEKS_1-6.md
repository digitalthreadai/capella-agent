# Capella Agent — 6-Week Production Hardening Plan

> **Track:** Production Hardening (Weeks 1–6)
> **Status:** **REVISED** after 4-reviewer audit · See [`PLAN_REVIEW_SYNTHESIS.md`](PLAN_REVIEW_SYNTHESIS.md) for the full review and the day-by-day revised schedule
> **Owner:** Capella Agent core team
> **Outcome:** Working prototype → shippable side-loaded beta (1.0.0.beta1)

## ⚠ READ THIS FIRST

Four expert agents (Software Architect, AI Engineer, Staff Engineer, UX Architect) reviewed this plan and found:

- **Scope was 2× over-budget** — original plan = 9–12 weeks of work, not 6
- **3 critical safety gaps** were missing (prompt injection defense, multi-tool undo correctness, crash recovery semantics)
- **5 hidden traps** would have collapsed the schedule (Tycho infra, `org.eclipse.ui.intro`, IFE-vs-landing-gear mismatch, demo video budget, dishonest 60% coverage target)
- **8 UX patterns** were missing that would make the difference between "tool" and "premium product"

The user approved a revised scope. The day-by-day execution plan in [`PLAN_REVIEW_SYNTHESIS.md`](PLAN_REVIEW_SYNTHESIS.md) supersedes the original schedule below for execution. The original below is preserved for traceability.

### Revised key decisions
| Topic | Decision |
|---|---|
| Multi-tool compound undo | **Document as known limitation**; ship inline "Show changes in this turn" button. Real fix in v1.1. |
| Welcome wizard mechanism | **Plain `WizardDialog`** — NOT `org.eclipse.ui.intro` (which would break Capella's own welcome page) |
| Demo asset format | **Written walkthrough + 1-take silent screencast**, NOT polished YouTube video with voiceover |
| Test coverage target | **35% on `core` by Week 2, 50% by Week 6** — NOT the original 60% (dishonest given Capella-touching code needs integration tests) |
| Prompt injection defense | **Ships in Week 3** before sustainment demo — required for defense customers |
| Demo symptoms | **Rewritten** to use Seat TV / Cabin Video Display Unit / Audio-Video stream — see [`../demo/IFE_MODEL_INVENTORY.md`](../demo/IFE_MODEL_INVENTORY.md). Original "nose wheel landing gear" symptom does not exist in the IFE sample model. |

---

---

## Context

Capella Agent has shipped a working multi-agent plugin with 116 tools, an HTML browser UI, 10 LLM providers, an MCP bridge, a clean tool-call loop, and styled tool result rendering. Real users can install it, ask "list all physical functions," and get sortable tables back from a live Capella model.

But it is not yet a **shippable enterprise product**. There are zero automated tests, no token metering, no crash recovery, no icons, no welcome wizard, no status bar, only one keyboard shortcut, and no branded sales demo to put in front of a Fortune-100 buyer.

This plan covers the first 6 weeks of the production-hardening track from the strategic roadmap. It transforms `capella-agent` from "working prototype" to "credibly shippable beta" — the kind of thing you can hand to a paying pilot customer and a Capella Days conference demo.

### Target completion criteria for Week 6

1. `mvn clean verify` runs an automated test suite with > 0 passing tests on every PR
2. Every write tool can be undone via Eclipse `Edit → Undo` and verified by a test
3. The chat view shows a live token + cost meter in the status line
4. A chat session crash leaves no orphaned model state and can be resumed
5. A 5-minute "sustainment engineer" demo video exists and is committed to the repo
6. The plugin has real icons, a welcome wizard, branded error dialogs, and 5+ keyboard shortcuts
7. A first beta customer can install the new build, watch the demo, and start using it

---

## Phase 1 — Weeks 1–2: Reliability Fundamentals

### Goal
Stop shipping by hope. Establish JUnit infrastructure, prove undo/redo actually works, give users a token meter, and survive mid-loop crashes.

### Workstream 1A — JUnit + Tycho Surefire Test Infrastructure

**Why now:** There are zero tests in the repo and `tycho-surefire-plugin` is not in `pom.xml`. Every other workstream depends on tests existing.

**Files to create:**
```
com.capellaagent.core.tests/
├── META-INF/MANIFEST.MF              # Fragment-Host: com.capellaagent.core
├── build.properties
├── pom.xml                            # packaging: eclipse-test-plugin
├── about.html
└── src/com/capellaagent/core/tests/
    ├── tools/
    │   ├── ToolRegistryTest.java
    │   ├── ToolParameterTest.java
    │   ├── ToolResultTest.java
    │   └── AbstractCapellaToolTest.java
    ├── llm/
    │   ├── ConversationSessionTest.java
    │   ├── LlmMessageTest.java
    │   └── OpenAiCompatibleProviderTest.java   # uses HttpClient mock
    ├── security/
    │   └── SecurityServiceTest.java
    └── capella/
        └── CapellaModelServiceTest.java         # uses fake EMF resource set

com.capellaagent.modelchat.tests/
├── META-INF/MANIFEST.MF              # Fragment-Host: com.capellaagent.modelchat
├── build.properties
├── pom.xml
└── src/com/capellaagent/modelchat/tests/
    ├── ToolRegistrationTest.java     # asserts all 116 tools register
    ├── read/ListElementsToolTest.java
    ├── read/SearchElementsToolTest.java
    ├── write/CreateElementToolTest.java
    └── write/DeleteElementToolTest.java
```

**Files to modify:**
- `pom.xml` — add `tycho-surefire-plugin` config + test modules
- `com.capellaagent.feature/feature.xml` — keep test fragments OUT of the feature
- `.github/workflows/build.yml` *(NEW)* — `mvn clean verify` on every push

**Test stack:**
- JUnit 5 (Jupiter) via the Eclipse target platform
- Mockito 5 for `Session`, `TransactionalEditingDomain`, `HttpClient` mocks
- Headless mode — no UI tests in this phase, just unit tests on `core` and `modelchat`

**Coverage target:** 60% line coverage on `core` package by end of Week 2.

---

### Workstream 1B — Undo / Redo Verification

**What exists:**
- `CapellaModelService.executeInTransaction()` already wraps writes in a `RecordingCommand` (`com.capellaagent.core/src/com/capellaagent/core/capella/CapellaModelService.java:323`)
- 49 files use `RecordingCommand`
- `AuditLogger` writes JSON audit entries to `{workspace}/.capella-agent/audit.log`

**What's missing:**
- No automated test ever invokes `Edit → Undo` on a tool result
- No proof that `delete_element`, `move_element`, `merge_elements` are properly undoable

**Files to create:**
```
com.capellaagent.modelchat.tests/src/com/capellaagent/modelchat/tests/undo/
├── UndoRedoTestBase.java              # spins up a temp Capella model fixture
├── CreateElementUndoTest.java
├── UpdateElementUndoTest.java
├── DeleteElementUndoTest.java
├── BatchRenameUndoTest.java           # high risk: bulk operation
└── MergeElementsUndoTest.java         # high risk: cross-references
```

**Test pattern:**
```
1. Load fixture model, snapshot element count + a known element's name
2. Execute the tool via ToolRegistry
3. Assert the change happened
4. Call domain.getCommandStack().undo()
5. Assert the model is back to the original snapshot
6. Call redo() and assert the change is back
```

**Files to modify:**
- `BatchRenameTool.java` — verify the rename loop is inside a single `RecordingCommand`
- `MergeElementsTool.java` — verify cross-reference re-pointing happens inside the same command

---

### Workstream 1C — Token Meter + Cost Display

**What exists:**
- `ChatJob.java` has `MAX_HISTORY_MESSAGES = 8` and `MAX_TOOL_RESULT_CHARS = 8000`
- No real token counting — just message-count heuristic
- No status display

**Files to create:**

| File | Purpose |
|---|---|
| `com.capellaagent.core/src/com/capellaagent/core/llm/TokenEstimator.java` | `estimateTokens(String)` (char/4 heuristic), `estimateMessages(...)`, per-provider model pricing table (Claude Sonnet 4, GPT-4o, GPT-4o mini, Llama 3.3 70B, Gemini Pro, GitHub Models, Ollama) |
| `com.capellaagent.core/src/com/capellaagent/core/llm/TokenUsageTracker.java` | Singleton, thread-safe. Tracks per-session and per-day input/output tokens + cost. Persists to `{workspace}/.capella-agent/usage.json`. Observer pattern for UI updates. |
| `com.capellaagent.modelchat.ui/src/com/capellaagent/modelchat/ui/statusbar/CapellaAgentStatusContribution.java` | `org.eclipse.ui.menus` extension. Shows `🤖 Capella Agent · Ready · Claude Sonnet · 12.4K tokens`. Click → preferences. |

**Files to modify:**
- `ChatJob.java` — pre-call estimate + refuse if over provider limit; post-call read actual usage; notify tracker
- `ModelChatView.java` — footer label below chat: `"Tokens this session: 12,450 · Today: 87,210 · Cost: $0.18"`
- `com.capellaagent.modelchat.ui/plugin.xml` — register status contribution

---

### Workstream 1D — Crash Recovery + Session Persistence

**What exists:**
- `ConversationSession` is purely in-memory
- If JVM crashes mid-tool-loop, the chat is lost
- Partial model writes are NOT rolled back (each tool call commits independently)

**Files to create:**

**`com.capellaagent.core/src/com/capellaagent/core/session/ConversationStore.java`**
- Saves each session as JSON: `{workspace}/.capella-agent/sessions/{projectName}/{sessionId}.json`
- `saveSession(ConversationSession s)` — atomic write (temp file + rename)
- `loadLatestSession(String projectName)` — newest session for that project
- `listSessions(String projectName)`
- `deleteSession(String sessionId)`
- Auto-save trigger: after every assistant message (not after every user message)

**Files to modify:**
- `ConversationSession.java` — `setStore(ConversationStore)`, `toJson()`, `fromJson(String)` methods
- `ModelChatView.java` — on view creation, `loadLatestSession(currentProject)` and restore HTML; if last message is assistant-with-tool-calls without a result, show "Resume" / "Discard" banner; on project switch, save current + load new
- `ChatJob.java` — wrap `run()` in try/finally that always saves; on uncaught exception, write `{"status": "crashed", "lastError": "..."}` marker

---

### Phase 1 Acceptance Tests
1. `mvn clean verify` runs tests, all pass
2. CI workflow runs on a PR
3. Coverage report shows ≥ 60% on `core`
4. Manual: create a function, click `Edit → Undo`, confirm it disappears
5. Manual: type a message that would be 50K tokens, get a friendly "too large" error before any API call
6. Manual: kill Capella mid-tool-loop, restart, see chat history restored with a "Resume" banner

---

## Phase 2 — Weeks 3–4: Sustainment Engineering Demo

### Goal
Build the **single most credible MBSE+AI sales demo**. Tony Komar's Siemens Capella Days 2025 talk proved this is the killer use case. We have all three core tools already (`get_functional_chain`, `impact_analysis`, `get_traceability` — confirmed real implementations, not placeholders).

### Workstream 2A — Sustainment Mode (UI thin layer)

**Files to create:**
- `com.capellaagent.modelchat.ui/src/com/capellaagent/modelchat/ui/views/SustainmentModeAction.java`
  - Toolbar button: "🔧 Sustainment Mode"
  - Pre-loads system prompt: *"You are a sustainment engineering assistant. The user will describe a fault or symptom. Use get_functional_chain, impact_analysis, and get_traceability to identify root causes. Always present findings as: 1) Likely subsystem, 2) Affected functional chain, 3) Components to inspect, 4) Related requirements. Cite element UUIDs."*
  - Pre-fills chat with example prompts

**Files to modify:**
- `ModelChatView.java` — add the toolbar action

---

### Workstream 2B — Fault Symptom Database

**Files to create:**

| File | Purpose |
|---|---|
| `com.capellaagent.modelchat/src/com/capellaagent/modelchat/tools/sustainment/FaultLookupTool.java` | New tool `lookup_fault_symptom`. Looks up `{project}/.capella-agent/sustainment/symptoms.json`. Returns matching component types, ATA codes, common root causes. Empty result + helpful note if file missing. |
| `com.capellaagent.modelchat/src/com/capellaagent/modelchat/tools/sustainment/AtaSpecTool.java` | New tool `classify_fault_ata`. Bundled JSON of ATA Spec 100 chapters (32 = landing gear, 27 = flight controls, 21 = air conditioning, etc.). |

These two tools transform vague natural-language fault reports into structured queries the existing tools can chase down.

---

### Workstream 2C — Demo Script + Recording

**Files to create:**
```
demo/
├── SUSTAINMENT_DEMO_SCRIPT.md      # exact words + clicks for the 5-min demo
├── sustainment_sample_model.zip     # IFE model packaged for download
├── prompts.txt                      # natural-language queries
└── README.md                        # how to reproduce
```

**Demo script outline (~5 minutes):**
| Time | Action |
|---|---|
| 0:00 | Hook (15s): "Sustainment engineers spend hours digging through models and manuals when a fault report comes in. Watch this take 60 seconds." |
| 0:15 | Setup (15s): Open Capella, IFE model loaded, click "🔧 Sustainment Mode" |
| 0:30 | Symptom 1 (90s): *"Nose wheel landing gear is retracting 30 seconds slower than nominal. What should I check?"* → agent calls `lookup_fault_symptom`, `get_functional_chain`, `impact_analysis` → structured findings with clickable UUIDs |
| 2:00 | Click-through (60s): Click a UUID → element selected in Project Explorer |
| 3:00 | Symptom 2 (90s): *"What requirements does the Hydraulic Subsystem trace to?"* → `get_traceability` → table |
| 4:30 | Wrap (30s): Show 116 tools menu, mention zero-internet Ollama mode, show MCP bridge for Claude Code |

**Recording:**
- OBS Studio or Windows + G
- Save as `demo/sustainment_demo_v1.mp4` (Git LFS or external CDN)
- Upload to YouTube as unlisted, link from `demo/README.md`

---

### Phase 2 Acceptance Tests
1. The demo can be reproduced end-to-end on a fresh Capella install with the IFE model
2. The MP4 + script + sample model are committed
3. A blog post or `forum.mbse-capella.org` reply is drafted (not posted yet) referencing Tony Komar's talk and offering the demo

---

## Phase 3 — Weeks 5–6: Polish Pass

### Goal
Make the plugin look like a $50K/year product, not a hackathon project.

### Workstream 3A — Icon Set

**What exists:** Empty icon directories. `plugin.xml` references `icons/chat_16.png` that doesn't exist.

**Files to create:**
```
com.capellaagent.modelchat.ui/icons/
├── chat_16.png         chat_32.png         (view)
├── send_16.png         clear_16.png        (toolbar)
├── export_16.png       detach_16.png       (toolbar)
├── theme_16.png        sustainment_16.png  (toolbar)
└── agent_logo_48.png                       (welcome page)

com.capellaagent.simulation.ui/icons/
└── simulation_16.png

com.capellaagent.core.ui/icons/
└── settings_16.png
```

**Approach:**
- Lucide / Phosphor / Tabler open-source icon sets
- Single accent color matching chat HTML theme
- 16×16 base + 32×32 @2x for HiDPI
- `tools/build_icons.py` script for regeneration

---

### Workstream 3B — Welcome Wizard

**Files to create:**
```
com.capellaagent.core.ui/
├── plugin.xml                                  # add org.eclipse.ui.intro extension
├── intro/
│   ├── introContent.xml
│   ├── css/welcome.css                         # dark theme matching chat UI
│   └── images/hero.png, step_*.png
└── src/com/capellaagent/core/ui/welcome/
    ├── WelcomeWizard.java
    ├── WelcomePage1Provider.java               # "Pick your LLM provider"
    ├── WelcomePage2ApiKey.java                 # "Enter API key (or skip for Ollama)"
    ├── WelcomePage3Test.java                   # "Test connection" button
    └── WelcomePage4Done.java                   # "Open the chat view"
```

**Trigger:** First time chat view is opened, check `IEclipsePreferences.getBoolean("welcome.shown", false)`. If false, show wizard, set true on completion. Add `Help → Welcome` for repeat access.

---

### Workstream 3C — Status Bar Contribution

Extends Phase 1 Workstream 1C:
- `🤖 Capella Agent · Ready · Claude Sonnet · 12.4K tokens · $0.18`
- Click → preferences
- Right-click → "Switch Provider", "Test Connection", "Clear Session", "Show Token Report"

---

### Workstream 3D — Keyboard Shortcuts

**What exists:** Only `ENTER` → send message.

**What to add (in `com.capellaagent.modelchat.ui/plugin.xml`):**

| Shortcut | Command | Scope |
|---|---|---|
| `Ctrl+Shift+M` | Open Capella Agent chat view | Global |
| `Ctrl+Enter` | Send message (alt to Enter) | ModelChatView |
| `Esc` | Cancel running tool job | ModelChatView |
| `Ctrl+L` | Clear conversation | ModelChatView |
| `Ctrl+E` | Export chat to HTML | ModelChatView |
| `Ctrl+Shift+T` | Toggle theme (light/dark) | ModelChatView |
| `Ctrl+,` | Open Capella Agent preferences | Global |

Each new shortcut needs a corresponding command + handler in the `commands/` package.

---

### Workstream 3E — Branded Error Dialogs

**What exists:** `formatUserFriendlyError()` in `ChatJob.java:345` already covers 7 error categories with emoji prefixes.

**Files to create:**

**`com.capellaagent.core.ui/src/com/capellaagent/core/ui/dialogs/BrandedErrorDialog.java`**
- Replaces raw `MessageDialog.openError()`
- Layout: logo (top-left), one-sentence headline (large), suggestion (smaller), expandable "Show details" with raw exception, "Copy diagnostic" button (clipboard: stack trace + plugin version + JVM + OS), "Open Preferences" button (when error is auth/config related)
- Static convenience methods: `openAuthError()`, `openNetworkError()`, `openTokenError()`, `openGenericError()`

**Files to modify:**
- `ModelChatView.java` — replace `MessageDialog.openError(...)` calls
- `ChatJob.java` — when an error needs a dialog (not just chat text)
- `CapellaAgentPreferencePage.java` — connection-test failure path

---

### Phase 3 Acceptance Tests
1. Chat view shows a real icon, not a missing-image box
2. First launch shows the welcome wizard
3. Status bar shows live token count, updates every message
4. All 7 keyboard shortcuts work
5. Triggering an auth error shows a branded dialog with logo, suggestion, and "Open Preferences" button
6. The "Copy diagnostic" button copies a usable bug report to clipboard

---

## Critical Files Reference

| Area | File |
|---|---|
| Test plugin scaffold | `com.capellaagent.core.tests/`, `com.capellaagent.modelchat.tests/` *(NEW)* |
| Tycho test config | `pom.xml` *(MODIFY)* |
| CI | `.github/workflows/build.yml` *(NEW)* |
| Token estimator | `com.capellaagent.core/src/com/capellaagent/core/llm/TokenEstimator.java` *(NEW)* |
| Token usage tracker | `com.capellaagent.core/src/com/capellaagent/core/llm/TokenUsageTracker.java` *(NEW)* |
| Session store | `com.capellaagent.core/src/com/capellaagent/core/session/ConversationStore.java` *(NEW)* |
| Session class | `com.capellaagent.core/src/com/capellaagent/core/session/ConversationSession.java` *(MODIFY)* |
| Chat job | `com.capellaagent.modelchat.ui/src/com/capellaagent/modelchat/ui/views/ChatJob.java` *(MODIFY)* |
| Chat view | `com.capellaagent.modelchat.ui/src/com/capellaagent/modelchat/ui/views/ModelChatView.java` *(MODIFY)* |
| Status bar | `com.capellaagent.modelchat.ui/src/com/capellaagent/modelchat/ui/statusbar/CapellaAgentStatusContribution.java` *(NEW)* |
| Sustainment tools | `com.capellaagent.modelchat/src/com/capellaagent/modelchat/tools/sustainment/FaultLookupTool.java`, `AtaSpecTool.java` *(NEW)* |
| Demo assets | `demo/SUSTAINMENT_DEMO_SCRIPT.md`, `demo/README.md`, `demo/prompts.txt` *(NEW)* |
| Welcome wizard | `com.capellaagent.core.ui/src/com/capellaagent/core/ui/welcome/*.java` + `intro/introContent.xml` *(NEW)* |
| Branded dialog | `com.capellaagent.core.ui/src/com/capellaagent/core/ui/dialogs/BrandedErrorDialog.java` *(NEW)* |
| Icons | `com.capellaagent.modelchat.ui/icons/*.png` *(NEW)* |
| Plugin XML — bindings/menus/intro/status | `com.capellaagent.modelchat.ui/plugin.xml`, `com.capellaagent.core.ui/plugin.xml` *(MODIFY)* |

---

## Existing Functions to Reuse (DO NOT rebuild)

| Need | Reuse |
|---|---|
| Wrap writes in transactions | `CapellaModelService.executeInTransaction(session, label, runnable)` at `com.capellaagent.core/src/com/capellaagent/core/capella/CapellaModelService.java:323` |
| Audit logging | `AuditLogger` at `com.capellaagent.core/src/com/capellaagent/core/security/AuditLogger.java` (already writes JSON to `.capella-agent/audit.log`) |
| User-friendly error formatting | `ChatJob.formatUserFriendlyError(String)` at `com.capellaagent.modelchat.ui/src/com/capellaagent/modelchat/ui/views/ChatJob.java:345` |
| HTML rendering of tool results | `ChatHtmlRenderer` (1010 lines, already supports all 9 categories) |
| Tool registration | `ToolRegistry` singleton — `register()`, `getTools(String...)`, `hasTool()`, `executeTool()` |
| Persistent preferences | `AgentConfiguration.getInstance()` (uses `IEclipsePreferences`) |
| Secure secrets | `org.eclipse.equinox.security.storage.ISecurePreferences` (already used for API keys) |
| Sustainment building blocks | `GetFunctionalChainTool`, `ImpactAnalysisTool`, `GetTraceabilityTool` — all real, all working |

---

## Verification — End-to-End Smoke Test

**After each phase:**
1. `mvn clean verify` — must pass with growing test count
2. Reinstall the update site into Capella 7.0.1
3. Run the manual smoke test below

**Manual smoke test (every release of Weeks 1–6):**
1. Open Capella, install the new build, restart
2. **First launch:** welcome wizard appears, walk through provider setup
3. Open a project (IFE sample), open the AI Model Chat view
4. Status bar shows `🤖 Capella Agent · Ready · {provider} · 0 tokens`
5. Type: "List all physical functions" → table appears, status bar updates token count
6. Click a UUID → element selected in Project Explorer
7. Hit `Ctrl+L` → chat clears
8. Hit `Ctrl+E` → HTML export dialog opens
9. Click 🔧 Sustainment Mode → system prompt updates, example prompts shown
10. Run the full demo script — all 5 minutes work without errors
11. Type something that would exceed token limit → friendly error before API call
12. Force-kill Capella mid-tool-loop, restart → resume banner appears, history restored
13. Create a function via chat, then `Edit → Undo` → function disappears
14. Open `Help → Welcome` → wizard appears again
15. Trigger an auth error (wrong API key) → branded dialog with logo and "Open Preferences" button

**Automated test gate:**
- CI must show: `Tests run: NN, Failures: 0, Errors: 0`
- Line coverage on `core`: ≥ 60% (Week 2), ≥ 70% (Week 6)

---

## Out of Scope for Weeks 1–6 (deliberately deferred)

- Code-signing certificate (Week 7+ of full plan)
- Hosted p2 update site on GitHub Pages (Week 9+)
- Requirements roundtrip / ReqIF (Roadmap Phase 2, Week 13+)
- Generative architecture from requirements (Roadmap Phase 3, Week 17+)
- Teamcenter integration (intentionally deferred)
- MATLAB integration (intentionally deferred)
- Localization (English-only ships)
- SBOM generation (Phase 2 hardening)
- UI tests with SWTBot (Week 2 stops at unit tests)
