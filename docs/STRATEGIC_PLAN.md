# Strategic Plan: Capella Agent → Production-Grade Product

> **Status:** Master strategic document
> **Scope:** Two parallel tracks — community-driven roadmap + production hardening
> **Companion doc:** [`PRODUCTION_PLAN_WEEKS_1-6.md`](PRODUCTION_PLAN_WEEKS_1-6.md) — the detailed execution plan for the first 6 weeks of Track 2

---

## Why This Plan Exists

Capella Agent has shipped a working multi-agent plugin: **116 tools, 10 LLM providers, an HTML browser UI, an MCP bridge, and a clean tool-call loop**. Real users can install it, ask "list all physical functions," and get sortable tables from a live Capella model.

But research into the MBSE/Capella community (Capella Days 2025, forum.mbse-capella.org, NalMage academic paper, Obeo SysON announcements) reveals two simultaneous truths:

1. **The market is ready right now.** A forum thread titled *"Capella hasn't kept up with AI and new MBSE capabilities; Is it time to abandon it?"* is actively retaining-or-losing paying users. Tony Komar's Siemens Capella Days 2025 talk validated the entire architectural bet (Capella's semantic Arcadia APIs are *better* than SysML v2 for AI work).
2. **A working prototype is not a shippable product.** Zero automated tests. No code signing. No hosted update site. No welcome wizard. No status bar. No icons. No published security model. No customer success motion.

This document is the strategic plan to address both — organized as **two parallel tracks** that reinforce each other.

---

## Track 1 — Roadmap: Solving Real Community Pain Points

Each phase maps to a **specific community signal** (forum thread, conference talk, academic paper, or competitive gap), not technical convenience. Loudest pain first.

### Phase 1: Sustainment Engineering Killer Demo

| Field | Value |
|---|---|
| **Timeline** | Weeks 1–4 *(parallel with Track 2 Phase 1)* |
| **Why first** | Tony Komar's Siemens prototype proved this is the most compelling MBSE+AI sales demo. Aero/defense/automotive Fortune-100 buyers respond to it. Your tools already exist; this is **packaging**, not building. |
| **Highest ROI** | ⭐⭐⭐⭐⭐ |

**Capabilities:**

| Capability | Status | Gap |
|---|---|---|
| Fault → Functional Chain query | `get_functional_chain` exists | Need a guided "Sustainment Mode" workflow that chains tools |
| Impact analysis from a symptom | `impact_analysis` exists | Need natural-language alias: "what could cause X?" |
| Maintenance manual lookup | Missing | Add `lookup_maintenance_doc` tool that searches PDFs in a project folder via embeddings |
| Fault classification by ATA spec | Missing | Add `classify_fault_ata` tool with the ATA 100 catalog as bundled data |

**Deliverable:** A 3-minute video — *"Nose wheel landing gear retracting 30 seconds slow → AI agent identifies hydraulic system → finds the relevant functional chain → opens the maintenance procedure."* This is the demo that wins enterprise deals.

---

### Phase 2: Requirements Round-Trip

| Field | Value |
|---|---|
| **Timeline** | Weeks 5–8 |
| **Community signal** | Forum is full of *"how do I import DOORS requirements / ReqIF / Excel into Capella"* threads. Current Capella Requirements Viewpoint is clunky and manual. |
| **Highest ROI** | ⭐⭐⭐⭐⭐ |

**Capabilities:**

| Capability | Notes |
|---|---|
| `import_reqif` | Parse ReqIF 1.0.1 → create Requirement elements with attributes |
| `export_reqif` *(already exists)* | Verify roundtrip fidelity with DOORS sample files |
| `import_requirements_excel` | XLSX → Requirements with smart column mapping |
| `link_requirements_to_elements` | NL: *"Link REQ-001 through REQ-050 to the Avionics System"* |
| `coverage_dashboard` | Browser-rendered HTML showing % of requirements with implementation traces |

**Why this matters:** Every regulated industry (aero, auto, med, rail) lives in DOORS / Polarion. **No roundtrip = no enterprise sale.**

---

### Phase 3: AI Architecture Co-Design

| Field | Value |
|---|---|
| **Timeline** | Weeks 9–14 |
| **Community signal** | The NalMage paper, Komar's RAG demo, and the Obeo SysON announcement all converge here. This is the *generative* side that gets press attention. |
| **Highest ROI** | ⭐⭐⭐⭐ |

**Capabilities:**

| Capability | Description |
|---|---|
| `propose_architecture_from_requirements` | Read REQs → propose components, functions, allocations as a *draft branch* the user accepts/rejects |
| `find_missing_traces` | Identify orphaned requirements and suggest plausible target elements |
| `suggest_decomposition` | *"This LogicalComponent is too complex (>15 functions). Here's a 3-component decomposition."* |
| `architecture_review_critique` | Run the model through a critique prompt → produce a structured findings report |
| `compare_alternatives` | Generate 2–3 architecture alternatives for the same requirements set, with tradeoff analysis |

**Critical UX rule:** All generative actions create a **diff preview** the user explicitly approves. Never silently mutate the model. This is the #1 trust-builder for enterprise users who fear AI "going rogue" on a million-dollar model.

---

### Phase 4: Cross-Tool Bridges

| Field | Value |
|---|---|
| **Timeline** | Weeks 15–22 |
| **Community signal** | *"I have Capella + Polarion + Simulink + Cameo and they don't talk to each other."* |
| **Highest ROI** | ⭐⭐⭐⭐ |

**Bridges:**

| Bridge | Approach | Effort |
|---|---|---|
| **Polarion** | REST API (better-documented than Teamcenter, easier first target) | M |
| **DOORS Next** | OSLC + REST | M |
| **Simulink** | MATLAB Engine API (foundation already in `simulation` module) | L |
| **JIRA** | REST — link Capella elements to issues | S |
| **Confluence** | Publish auto-generated architecture docs as Confluence pages | S |
| **Git** | `git_diff_model` — show semantic diff of two .aird versions in natural language | M |

**Strategic note:** Lead with **Polarion** and **JIRA** — they're easier to integrate and have huge installed bases. Skip Teamcenter until a paying customer asks.

---

### Phase 5: Multi-Agent Collaboration

| Field | Value |
|---|---|
| **Timeline** | Weeks 23–30 |
| **Community signal** | Eclipse LMOS exists, OpenAI/Anthropic have agent SDKs, and your `Tc-agent` and `Simulation-agent` are already separate bundles. Time to make them actually collaborate. |
| **Highest ROI** | ⭐⭐⭐ |

**Capabilities:**

| Capability | Description |
|---|---|
| **Agent Bus** | Real pub/sub channel between Model Chat, Teamcenter, Simulation agents |
| **Workflow Templates** | *"Validate requirements → run simulation → update model → notify reviewer"* as one NL command |
| **Long-running tasks** | Track multi-step workflows in a job manager UI (not just chat) |
| **Memory across sessions** | Persistent agent memory (project-scoped knowledge base in SQLite or pgvector) |

---

## Track 2 — Production Hardening: Siemens-Grade Quality

A working plugin is not a product. Here's what's missing to ship to a Fortune-100 customer who'll pay $50K–$500K/year. *Track 2 runs in parallel with Track 1 — the first 6 weeks are detailed in [`PRODUCTION_PLAN_WEEKS_1-6.md`](PRODUCTION_PLAN_WEEKS_1-6.md).*

### A. Reliability & Correctness

**Priority:** CRITICAL · **Timeline:** Weeks 1–6

| Gap | Action | Why |
|---|---|---|
| **Zero automated tests** | Add JUnit 5 + Tycho Surefire. Target 70% coverage on `core` and tool framework | Cannot ship without tests. Period. |
| **No integration tests** | Headless Capella test fixture loading the IFE sample model | Catches API breakages before customers do |
| **No undo/redo verification** | Test every write tool with `EditingDomain.getCommandStack().undo()` | Right now we trust EMF transactions blindly |
| **No multi-project test** | Test with 3 models open simultaneously, switching between them | We never tested this — likely has bugs |
| **Tool error sanitization** | Audit every tool's exception handling — never leak stack traces to user | Some tools still throw raw RuntimeExceptions |
| **Token budgeting with hard caps** | Add per-request token meter; refuse to send if > provider limit | Currently we hope it fits |
| **Conversation persistence** | Save chats to disk per project, restore on reopen | Users lose their work today |
| **Crash recovery** | If LLM mid-tool-loop crashes, resume gracefully | Currently leaves session in broken state |

---

### B. Security

**Priority:** CRITICAL for enterprise sales · **Timeline:** Weeks 3–8

| Gap | Action |
|---|---|
| **API keys in plain Eclipse preferences** | Use Eclipse SecureStorage exclusively, never plain prefs |
| **No SBOM** | Generate CycloneDX SBOM for the Gson + any other bundled libs |
| **Unsigned JARs** | Get a code-signing cert. Sign all bundles. Without this, enterprise IT will reject install. |
| **No prompt injection guards** | Add a sanitizer that strips `</system>`, `ignore previous`, etc. from tool results before feeding to LLM |
| **No PII redaction** | When exporting chat to HTML, optionally redact emails/credentials |
| **No air-gapped mode** | Add an explicit "offline" toggle that disables all cloud providers, only allows Ollama |
| **No audit log wired up** | The `AuditLogger` class exists but isn't wired up. Wire it. Defense customers need this. |
| **No tool allowlist** | Admin should be able to configure which tools are enabled at site level via preferences XML |

---

### C. Polish & Brand

**Priority:** HIGH · **Timeline:** Weeks 4–10

| Gap | Action |
|---|---|
| **Generic icons** | Hire a designer (Fiverr, $200) for: chat icon, agent logo, tool result icons |
| **No splash branding** | Splash screen contribution showing "Capella Agent" loading |
| **Default Eclipse fonts in chat** | Custom HTML/CSS with branded typography (refine our existing Inter use) |
| **Inconsistent error messages** | Standard error format: short headline + actionable suggestion + (optional) "Show details" link |
| **No keyboard shortcuts** | Ctrl+Shift+M = open chat, Ctrl+Enter = send, Esc = cancel |
| **No status bar contribution** | Bottom-right indicator: *"Capella Agent: Ready (Claude Sonnet)"* with click-to-configure |
| **No welcome page** | First-launch wizard: pick provider → enter key → test connection → done |

---

### D. Documentation

**Priority:** HIGH · **Timeline:** Weeks 6–10

| Asset | Format | Audience |
|---|---|---|
| **Getting Started video** | 5 min screen recording | New users |
| **Tool Reference site** | Static site (Docusaurus or MkDocs) generated from `TOOLS.md` | Power users |
| **Cookbook** | *"How do I create an architecture from requirements?"* — 10 recipes | Solution sellers |
| **Architecture Decision Records** | `docs/adr/` — explain key choices | Future maintainers |
| **Migration Guide** | How to upgrade between plugin versions safely | Existing customers |
| **Security Whitepaper** | Threat model + mitigations | Enterprise security teams |
| **API JavaDoc** | Public API of `core.tools` and `core.llm` | Tool developers |

---

### E. Distribution & Operations

**Priority:** HIGH · **Timeline:** Weeks 8–14

| Gap | Action |
|---|---|
| **No hosted update site** | Push p2 repo to GitHub Pages or Eclipse Marketplace |
| **No version semantics** | Adopt semver. Bump to `1.0.0` for first release. Use `qualifier` only for SNAPSHOTs |
| **No release notes** | `CHANGELOG.md` with every version |
| **No CI/CD** | GitHub Actions: `mvn verify` on every PR, signed release on tag push |
| **No issue templates** | `.github/ISSUE_TEMPLATE/bug.md`, `feature.md`, `support.md` |
| **No telemetry** *(with opt-in)* | Anonymous usage: tool calls/day, model used, error rates → helps prioritize |
| **No license** | Add LICENSE file. EPL 2.0 matches Capella ecosystem |
| **No contributor guide** | `CONTRIBUTING.md` with build/test/PR instructions |

---

### F. Performance & Cost

**Priority:** MEDIUM · **Timeline:** Weeks 10–14

| Gap | Action |
|---|---|
| **Token usage not tracked** | Show *"Tokens used today: 12,450 / Cost: $0.18"* in status bar |
| **No request caching** | Identical tool calls within session should hit a cache |
| **No model auto-selection** | *"Use cheap model for read tools, expensive for write"* |
| **Slow model load on first query** | Pre-warm LLM provider on plugin activation |
| **MCP server not tested at scale** | Load test with 100 tool calls/min |

---

### G. Customer Success

**Priority:** MEDIUM · **Timeline:** Weeks 12–18

| Need | Action |
|---|---|
| **Built-in feedback button** | *"Was this response helpful? 👍 👎"* → posts to your backend |
| **Diagnostic export** | One-click *"Export problem report"* that bundles logs, config (redacted), version |
| **Sample models** | Ship with 2–3 reference models the user can experiment on without owning one |
| **Provider connectivity wizard** | *"Test all providers"* command that tries each configured one and reports |
| **Error knowledge base** | Common errors page with solutions |

---

## Recommended Sequence

### If 1-person effort
Each step builds the next:

| Weeks | Track | Focus |
|---|---|---|
| 1–2 | T2-A | Reliability fundamentals — JUnit setup, undo/redo tests, token meter, crash recovery |
| 3–4 | T1-1 + T2-D | Sustainment Engineering demo + 5-min video → land first beta customer |
| 5–6 | T2-C | Polish pass — icons, welcome wizard, status bar, keyboard shortcuts, branded errors |
| 7–8 | T2-B | Security hardening — SecureStorage, audit log wiring, code-signing cert |
| 9–10 | T2-E | Distribution — GitHub Pages p2 site, semver, CI/CD, release notes, marketplace listing |
| 11–12 | T2-D | Documentation site + Getting Started video + Cookbook |
| 13–16 | T1-2 | Requirements roundtrip — biggest enterprise unlock |
| 17–22 | T1-3 | Generative architecture — biggest marketing unlock |
| 23+ | T1-4 → T1-5 | Cross-tool bridges → multi-agent collaboration |

### If 2–3 developers
Parallelize Tracks 1 and 2. Dev A drives Track 2 (hardening, infra, docs), Dev B drives Track 1 (features), Dev C does QA + customer-facing material.

---

## What to NOT Build

These look attractive but are **traps** that drain effort without moving the needle.

| ❌ Don't | Why |
|---|---|
| **Finish Teamcenter integration in v1** | Customers all have different TC configs. Lead with Polarion or skip PLM until a paying customer asks. |
| **Build your own RAG** | Use the LLM provider's native context. Adding a vector DB adds operational complexity for marginal value. |
| **Compete with Sirius diagram editing** | Adding/removing elements from diagrams via AI is a UX nightmare. Focus on *creating* new diagrams from element selections. |
| **Ship Simulation agent until MATLAB integration is real** | Placeholder tools that say "TODO" damage credibility. |
| **Add a chat-based code editor** | That's Claude Code's job — your MCP bridge already covers it. |
| **Translate the UI to multiple languages** | EN-only ships faster. Localize on customer demand. |

---

## The "Siemens or Better" Quality Bar

A customer should be able to:

1. ✅ Install the plugin from a hosted update site with **zero manual config**
2. ✅ Open it, see a welcome page, click through provider setup in **< 60 seconds**
3. ✅ Open the IFE sample model, type *"list all physical functions"* → get a styled table in **< 5 seconds**
4. ✅ Run for **8 hours of continuous use** without a single uncaught exception
5. ✅ Reproduce any bug by clicking *"Export Problem Report"*
6. ✅ Find the answer to any *"how do I…"* question in the docs site within **2 clicks**
7. ✅ Verify code signature on **every** JAR
8. ✅ Roll back to the previous version with a single click
9. ✅ Run the plugin **completely offline** (Ollama mode) without any errors
10. ✅ Submit a feature request via an **in-app button**

If any of those is "no" — it's not ready to charge enterprise prices for.

---

## Companion Documents

| Document | Scope |
|---|---|
| `docs/STRATEGIC_PLAN.md` | **This document** — full strategic plan, both tracks, master vision |
| [`docs/PRODUCTION_PLAN_WEEKS_1-6.md`](PRODUCTION_PLAN_WEEKS_1-6.md) | Detailed execution plan for the first 6 weeks of Track 2, with file-level changes |
| [`docs/STRATEGIC_PLAN.html`](STRATEGIC_PLAN.html) | Interactive dark-themed HTML rendering of this document |
| [`docs/PRODUCTION_PLAN_WEEKS_1-6.html`](PRODUCTION_PLAN_WEEKS_1-6.html) | Interactive HTML rendering of the 6-week execution plan |
| [`docs/TOOLS.md`](TOOLS.md) / [`docs/TOOLS.html`](TOOLS.html) | The 116-tool catalog |
| [`docs/COO.md`](COO.md) / [`docs/COO.html`](COO.html) | Strategy & feature guide |
| [`docs/README.md`](../README.md) / [`docs/README.html`](README.html) | Project overview |
