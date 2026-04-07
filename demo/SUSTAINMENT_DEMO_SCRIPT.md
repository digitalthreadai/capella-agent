# Sustainment Engineer Demo — 5-Minute Walkthrough

> **Target audience:** Aerospace / defense sustainment engineering teams, Capella Days attendees, Fortune-100 enterprise buyers
> **Duration:** 5 minutes (scripted)
> **Prerequisites:** Capella 7.0.1 + Capella Agent 1.0.0.beta1 installed, the "In-Flight Entertainment System" sample model open in the workspace
> **Model validation:** See [`IFE_MODEL_INVENTORY.md`](IFE_MODEL_INVENTORY.md) — this script uses symptoms that actually exist in the IFE model

---

## Why this demo

Tony Komar's Siemens Capella Days 2025 presentation proved that **sustainment engineering is the killer use case for MBSE + AI**. A sustainment engineer who gets a fault report at 3am opens 4 different tools — the model, the requirements DB, the maintenance manual, the bug tracker — and spends 60–90 minutes just to file the right ticket.

This demo shows a Capella Agent reducing that loop to under a minute.

---

## Story arc (5 minutes)

| Time | Beat | Speaker / on-screen |
|---|---|---|
| 0:00–0:20 | **The pain** | Talking head or text slide: *"Sustainment engineers spend hours on fault triage. A single fault report can touch 20 different model elements across 5 layers. The model knows — but today there's no way to ask it."* |
| 0:20–0:35 | **The promise** | *"Capella Agent lets engineers ask their model in plain English. Watch a 60-second diagnosis."* |
| 0:35–2:30 | **The moment (Symptom 1)** | Live chat interaction, minimal narration |
| 2:30–3:30 | **Trust — every change is logged, every change is reversible** | Show audit log, show Edit → Undo, show diff |
| 3:30–4:30 | **The reach — offline mode, 116 tools, Claude Code MCP bridge** | Quick UI tour |
| 4:30–5:00 | **The ask** | *"If this could save your team 4 hours a week, we'd love to talk."* Contact info. |

---

## Setup (before recording)

1. Capella open, IFE project loaded, Project Explorer showing the 5 layers
2. Status bar showing: `🤖 Claude Sonnet · Ready`
3. AI Model Chat view docked on the right half of the window
4. Chat is empty (fresh conversation)
5. Mode dropdown set to **Sustainment Engineer**
6. Terminal/log window closed so no distractions

---

## The beats

### Beat 1 — Hook (0:00–0:20)

**Voice-over or first slide:**
> "It's 3am. A sustainment engineer gets a fault report from the field. Today, they open four different tools and spend 90 minutes identifying the right subsystem to dispatch. Watch the agent do it in 60 seconds."

### Beat 2 — The setup (0:20–0:35)

Screen: zoom into the AI Model Chat view. Show the mode dropdown labeled *"Sustainment Engineer"*. Show the empty-state card:
```
Hi. I can diagnose faults using your live Capella model.
Try one of these to get started:
  [The Seat TV in row 14 is rebooting every time we try to play a VOD movie. Where should I look?]
  [During an airline-imposed safety video broadcast, audio is silent on the cabin terminal but video plays fine. What functions are involved in audio routing for imposed broadcasts?]
  [The Available VOD Movies List on the seat-back display is showing yesterday's titles. The Applications Server was rebooted. Which functions feed that list?]
```

### Beat 3 — The moment (0:35–2:30)

Click the first starter prompt. It fills the input. Click Send.

**Agent response flow (chain of tool calls):**
1. `>> Executing tool: classify_fault_ata ...`
   Result: `44 - Cabin Systems` (shown as a badge in chat)
2. `>> Executing tool: lookup_fault_symptom ...`
   Result: candidate component types (Cabin Video Display Unit, Cabin Video Player, Cabin Video Unit SW)
3. `>> Executing tool: get_functional_chain ...`
   Result: the chain from *Acquire Audio Stream* → *Decode Video Packet* → *Display Video on Seat TV*
4. `>> Executing tool: impact_analysis ...`
   Result: 7 components + 12 exchanges in the impact radius

**Final assistant text (rendered in chat):**
```
Fault diagnosis:

  Likely subsystem: Cabin Video Display Unit
  ATA chapter: 44 - Cabin Systems
  Affected functional chain: Display VOD Movie on Seat TV
    (uuid: 07256f31-32c7-42f5-b36b-bebd43492921)

  Components to inspect (in priority order):
    • Cabin Video Display Unit — power cycling symptom is most often here
    • Cabin Video Player (SW) — software reset may indicate OOM
    • Cabin Video Unit SW — check firmware version
    • Decode Video Packet function — verify codec support for the VOD file

  Related requirements:
    • REQ-IFE-047 "Seat TV shall play VOD content with no interruption"
    • REQ-IFE-091 "Seat TV shall display within 2 seconds of selection"

  Recommended action: Pull the Cabin Video Display Unit for row 14,
  verify firmware against the latest cabin video baseline, then re-seat
  into the Cabin Terminal.
```

**On-screen callout:** elapsed time stamp shows **0:47**. That's the payoff line.

### Beat 4 — Click-through (1:45–2:30)

Click the UUID in the assistant response. The Project Explorer jumps to *"Display VOD Movie on Seat TV"* and highlights it.
Show the Sirius diagram opening automatically with that functional chain in focus.

### Beat 5 — Trust (2:30–3:30)

Talk over a series of short shots:
- Open `Window → Show View → Audit Log` — show the JSON entries: every tool call, every parameter, every element UUID touched, timestamped
- Back in chat, show the "View changes" button on each write tool result (Week 5 polish will add it). Click → diff between before/after
- Show `Edit → Undo`. Say: *"Every single action the agent took is on the Eclipse undo stack. Nothing is silent. Nothing is irreversible."*

### Beat 6 — Reach (3:30–4:30)

Quick cuts:
- Open `Window → Preferences → Capella Agent`. Switch provider from Claude to **Ollama (Local)**. Show a single "Ready — offline" indicator in status bar. Say: *"For ITAR or air-gapped networks, the entire assistant runs locally on your laptop."*
- Open the tools catalog: `docs/TOOLS.html` or a screenshot of it. Say: *"116 tools across 9 categories — read, write, diagram, analysis, export, transitions, AI-assisted. You just saw 4 of them."*
- Show a terminal with `claude` running. Type *"List all physical functions from the IFE model via the Capella MCP bridge"*. It works. Say: *"If your team already uses Claude Code, you get all 116 tools in your terminal too via the Model Context Protocol bridge."*

### Beat 7 — The ask (4:30–5:00)

**Voice-over + contact card slide:**
> "Capella Agent is open source, EPL 2.0. We're looking for 5–10 pilot customers for the 1.0 launch. If this could save your team 4 hours a week, let's talk. email@example.com, or check digitalthreadai.com."

---

## Recording notes

- **Take count:** Budget 3–5 takes. The LLM responses vary slightly between runs — if the agent picks a slightly different tool order on take 3, restart.
- **Audio:** Screen recording with voiceover. If the voiceover proves too slow to produce, ship a silent screencast + captions overlay for the first version and layer audio later.
- **Length:** Keep it under 5 minutes. At 5:01 you've lost 40% of viewers.
- **Tools:** OBS Studio (free) for capture, DaVinci Resolve (free) for cuts.
- **Output format:** 1080p30, H.264 MP4, <100 MB so it can live on GitHub or a CDN.
- **Captions:** Add SRT captions at least on the agent's response text — it's too long to read quickly in the video otherwise.

## Fallback: silent screencast + written walkthrough

If video production is blocked, ship the **written walkthrough** (this file) plus a **single-take silent screencast** (<2 minutes, no narration, just a clean recording of the agent responding). The written doc is what enterprise buyers actually read; the screencast is the forum post asset.

## What NOT to show

- Don't show token usage or cost — distracting, comes across as "AI hype"
- Don't show the preferences page credential setup — boring, belongs in the docs
- Don't mention version numbers or release dates — they age badly
- Don't improvise — the symptoms in this script are the ones that are verified to work against the IFE model. Deviating is a risk of a dead demo.

## Verification checklist

Before recording:
- [ ] The IFE model is loaded
- [ ] At least one Physical Architecture diagram is openable
- [ ] `classify_fault_ata` returns `44 - Cabin Systems` for the "Seat TV rebooting" prompt (run it manually first)
- [ ] `lookup_fault_symptom` returns cabin video components for the same prompt
- [ ] `get_functional_chain` finds a chain containing *"Display Video on Seat TV"*
- [ ] Clicking a UUID in the chat navigates to the element in Project Explorer
- [ ] Mode dropdown shows "Sustainment Engineer" as an option
- [ ] Empty state shows the 3 sustainment starter prompts
- [ ] Audit log file exists at `.capella-agent/audit.log`
