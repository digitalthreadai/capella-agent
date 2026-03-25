# Capella Agent -- COO Strategy & Feature Guide

> AI-powered multi-agent ecosystem for Model-Based Systems Engineering inside Eclipse Capella.
> Three collaborating agents -- Model Chat, Teamcenter, and Simulation -- deliver
> natural-language model interaction, PLM connectivity, and CAE orchestration
> through 29 LLM-callable tools, all running natively in the Capella workbench.

| | |
|---|---|
| **Version** | 1.0.0-SNAPSHOT |
| **License** | Eclipse Public License v2.0 |
| **Platform** | Eclipse Capella 7.0 (Eclipse RCP / Equinox) |
| **Java** | 17+ |
| **Codebase** | 114 Java files, 16,210 lines across 10 projects |

---

## Table of Contents

1. [Vision & Problem Statement](#vision--problem-statement)
2. [Competitive Landscape](#competitive-landscape)
3. [Architecture Overview](#architecture-overview)
4. [Plugin Map](#plugin-map)
5. [Feature Map -- All 29 Tools](#feature-map----all-29-tools)
6. [Key Interfaces & Extension Points](#key-interfaces--extension-points)
7. [Technology Stack](#technology-stack)
8. [Data Layer -- EMF Model, Not a Database](#data-layer----emf-model-not-a-database)
9. [Security Model](#security-model)
10. [Cross-Agent Communication](#cross-agent-communication)
11. [Roadmap](#roadmap)
12. [File Structure](#file-structure)

---

## Vision & Problem Statement

Systems engineers using Eclipse Capella spend significant time on repetitive
model manipulation, manual data transfer between Capella and PLM systems, and
context-switching to run simulations. The feedback loop between model change,
simulation result, and design decision is measured in hours or days.

Capella Agent eliminates that friction by embedding three purpose-built AI
agents directly inside the Capella workbench:

- **Model Chat Agent** -- ask questions about and modify ARCADIA models in
  plain English, with full undo/redo and transactional safety.
- **Teamcenter Agent** -- search, import, and trace Siemens Teamcenter PLM
  artifacts without leaving Capella.
- **Simulation Agent** -- extract parameters from the model, run
  MATLAB/Simulink simulations, propagate results back, and perform what-if
  analysis, all from a single chat prompt.

The agents coordinate through a shared event bus, so importing a Teamcenter
part can trigger a model update, which can trigger a simulation re-run -- all
in one conversational flow.

### Design Principles

1. **Native, not remote.** Agents run inside the Eclipse process with direct
   EMF access. No REST server, no separate Python process, no file export.
2. **Provider-agnostic LLM layer.** Swap between Claude, OpenAI, Groq,
   DeepSeek, Mistral, OpenRouter, Gemini, Ollama, or a custom endpoint
   without changing tool code. Corporate users can run fully local with Ollama.
3. **Safety first.** Default mode is READ_ONLY. Write operations require
   explicit opt-in, run inside EMF transactions with full undo support, and
   are audit-logged.
4. **OSGi modularity.** Each agent is a separate bundle. Install only what
   you need. No Teamcenter license? Don't install that plugin.

---

## Competitive Landscape

| Solution | Approach | AI / NL | In-Capella | PLM | CAE | Limitations |
|----------|----------|---------|------------|-----|-----|-------------|
| **Capella Agent** | OSGi plugin ecosystem | Yes (multi-provider) | Native | Teamcenter | MATLAB/Simulink | Early stage; Capella 7.0 only |
| Python4Capella | Python scripting for Capella | No | Via bridge | No | No | Requires Python knowledge; no conversational interface |
| capellambse / mcp-capella | Headless Python library | Partial (MCP) | No (external) | No | No | Runs outside Eclipse; no live diagrams or Sirius integration |
| Teamcenter Rich Client | Desktop PLM client | No | No | Native Tc | No | No MBSE awareness; no model manipulation |
| MATLAB Capella bridges | Manual export/import | No | Partial | No | Manual | No automation; no AI orchestration |
| Manual workflow | Copy-paste, spreadsheets | No | N/A | Manual | Manual | The status quo this project replaces |

### Differentiation

Capella Agent is the only solution that combines all three capabilities --
natural-language model interaction, PLM integration, and simulation
orchestration -- inside a single Eclipse session with a unified AI layer.
Competing approaches address at most one of these concerns and require
context-switching between tools.

---

## Architecture Overview

```
+----------------------------------------------------------------------+
|                        Eclipse Capella 7.0                            |
|                                                                      |
|  +--------------------+  +-------------------+  +------------------+ |
|  | Model Chat Agent   |  | Teamcenter Agent  |  | Simulation Agent | |
|  | (16 tools)         |  | (7 tools)         |  | (6 tools)        | |
|  |                    |  |                   |  |                  | |
|  | modelchat          |  | teamcenter        |  | simulation       | |
|  | modelchat.ui       |  |                   |  | simulation.ui    | |
|  +--------+-----------+  +--------+----------+  +--------+---------+ |
|           |                       |                      |           |
|           +----------+------------+----------+-----------+           |
|                      |                       |                       |
|              +-------v-------+       +-------v-------+               |
|              |   core        |       |   core.ui     |               |
|              | LLM providers |       | Chat widget   |               |
|              | Tool registry |       | Markdown      |               |
|              | Message bus   |       +---------------+               |
|              | Security      |                                       |
|              | Config        |                                       |
|              +-------+-------+                                       |
|                      |                                               |
|              +-------v-------+                                       |
|              |   mcp         |                                       |
|              | HTTP endpoint |                                       |
|              | (localhost:   |                                       |
|              |  9847)        |                                       |
|              +-------+-------+                                       |
|                      |                                               |
+----------------------------------------------------------------------+
         |             |             |                    |
         v             v             v                    v
   Claude / OpenAI  MCP bridge   Teamcenter           MATLAB /
   / Groq / etc.    subprocess   Active Workspace     Simulink
   (HTTPS)          (stdio)      (REST)              (Engine API)
                       |
                       v
                  Claude Code
                  terminal
```

### Dependency Flow

All domain plugins depend on `core`. UI plugins depend on both `core` and
`core.ui`. The `mcp` plugin depends on `core` for tool registry access.
The `feature` project packages all eight plugins for p2 distribution, and
the `site` project publishes the update site.

```
core.ui  <--  modelchat.ui, simulation.ui
core     <--  modelchat, teamcenter, simulation, mcp
feature  <--  all eight plugins
site     <--  feature
```

---

## Plugin Map

### 1. `com.capellaagent.core` -- Foundation (35 files)

The shared infrastructure layer. Every other plugin depends on this.

| Package | Responsibility | Key Classes |
|---------|---------------|-------------|
| `core.llm` | LLM abstraction and messaging | `ILlmProvider`, `LlmProviderRegistry`, `LlmMessage`, `LlmResponse`, `LlmToolCall`, `LlmToolResult`, `LlmRequestConfig`, `LlmException` |
| `core.llm.providers` | Concrete LLM implementations | `ClaudeProvider`, `OpenAiProvider`, `OllamaProvider`, `OpenAiCompatibleProvider` (abstract base), `GroqProvider`, `DeepSeekProvider`, `MistralProvider`, `OpenRouterProvider`, `GeminiProvider`, `CustomEndpointProvider` |
| `core.tools` | Tool registry and base classes | `IToolDescriptor`, `IToolExecutor`, `ToolRegistry`, `ToolRegistration`, `ToolSchemaBuilder`, `AbstractCapellaTool`, `ToolExecutionException` |
| `core.bus` | Cross-agent event communication | `IAgentMessageBus`, `AgentMessageBus`, `AgentEvent`, `ModelChangedEvent`, `TcItemImportedEvent`, `SimulationResultEvent` |
| `core.security` | Access control and audit logging | `SecurityService`, `AccessMode`, `AuditLogger` |
| `core.config` | Preference management | `AgentConfiguration` |
| `core.session` | Conversation state | `ConversationSession` |
| `core.util` | Eclipse/Capella helpers | `JsonUtil`, `CapellaSessionUtil`, `WorkspaceUtil` |
| (root) | Plugin lifecycle | `Activator` |

### 2. `com.capellaagent.core.ui` -- Shared UI (2 files)

| Class | Responsibility |
|-------|---------------|
| `ChatComposite` | Reusable SWT chat widget with input area, message list, and send action |
| `MarkdownRenderer` | Converts LLM markdown responses to styled SWT content |

### 3. `com.capellaagent.modelchat` -- Model Chat Agent (18 files)

Read, write, and diagram tools for interacting with Capella models through
natural language.

| Class | Responsibility |
|-------|---------------|
| `ModelChatActivator` | Bundle lifecycle |
| `ModelChatToolRegistrar` | Registers all 16 model tools with the `ToolRegistry` |
| `tools/read/*` | 8 read-only model query tools |
| `tools/write/*` | 6 model mutation tools |
| `tools/diagram/*` | 2 diagram manipulation tools |

### 4. `com.capellaagent.modelchat.ui` -- Model Chat UI (6 files)

| Class | Responsibility |
|-------|---------------|
| `ModelChatView` | Dockable Eclipse view hosting the chat interface |
| `ChatJob` | Background Eclipse Job for async LLM calls |
| `SendMessageHandler` | Eclipse command handler for the send action |
| `ClearHistoryHandler` | Eclipse command handler to reset conversation |
| `ElementLinkAdapter` | Makes element references in chat responses clickable (navigates to model element) |
| `ModelChatUiActivator` | Bundle lifecycle |

### 5. `com.capellaagent.teamcenter` -- Teamcenter Agent (20 files)

| Package | Responsibility | Key Classes |
|---------|---------------|-------------|
| `client` | REST connectivity | `TcRestClient`, `TcSession`, `TcConfiguration`, `TcException` |
| `api` | Teamcenter service facades | `TcSearchService`, `TcObjectService`, `TcBomService`, `TcRequirementsService` |
| `import_` | Tc-to-Capella data mapping | `TcToCapellaMapper`, `RequirementImporter`, `PartImporter` |
| `tools` | 7 LLM-callable tools | `TcSearchTool`, `TcGetObjectTool`, `TcGetBomTool`, `TcListRequirementsTool`, `TcImportRequirementTool`, `TcImportPartTool`, `TcLinkTool` |
| (root) | Lifecycle and registration | `TcActivator`, `TcToolRegistrar` |

### 6. `com.capellaagent.simulation` -- Simulation Agent (22 files)

| Package | Responsibility | Key Classes |
|---------|---------------|-------------|
| `bridge` | Engine abstraction and MATLAB connectivity | `ISimulationEngine`, `MatlabEngineBridge`, `MatlabCommandBridge`, `SimulationResult`, `SimulationEngineException` |
| `orchestrator` | End-to-end simulation workflow | `SimulationOrchestrator`, `SimulationConfig`, `ParameterExtractor`, `ParameterMapping`, `ResultMapping`, `ResultPropagator`, `WhatIfManager`, `WhatIfSpec` |
| `config` | Simulation preferences | `SimulationPreferences` |
| `tools` | 6 LLM-callable tools | `ListEnginesTool`, `ExtractParamsTool`, `RunSimulationTool`, `PropagateResultsTool`, `WhatIfTool`, `GetSimStatusTool` |
| (root) | Lifecycle and registration | `SimActivator`, `SimToolRegistrar` |

### 7. `com.capellaagent.simulation.ui` -- Simulation Dashboard (1 file)

| Class | Responsibility |
|-------|---------------|
| `SimulationDashboardView` | Eclipse view showing active/completed simulation runs, results, and status |

### 8. `com.capellaagent.mcp` -- MCP Server Bridge (5 files)

Exposes all registered Capella Agent tools to Claude Code (or any MCP
client) via the Model Context Protocol.

| Class | Responsibility |
|-------|---------------|
| `McpActivator` | Bundle lifecycle; starts the HTTP endpoint on activation |
| `McpHttpEndpoint` | Embedded HTTP server on `localhost:9847` that accepts tool-call requests from the bridge subprocess |
| `McpToolAdapter` | Translates between MCP tool schemas and the internal `IToolDescriptor` / `IToolExecutor` contracts |
| `McpServerBridge` | Launches and manages the bridge subprocess that speaks MCP stdio (JSON-RPC 2.0) on one side and HTTP on the other |
| `McpStdioTransport` | Handles JSON-RPC 2.0 framing over stdin/stdout for the bridge subprocess |

The architecture is: **Claude Code terminal** <--stdio/JSON-RPC 2.0--> **bridge subprocess** <--HTTP--> **Eclipse plugin (McpHttpEndpoint)** <--internal API--> **ToolRegistry**.

### 9. `com.capellaagent.feature` -- Feature Packaging

Eclipse Feature project that groups all eight plugins into a single
installable unit for p2 repositories.

### 10. `com.capellaagent.site` -- Update Site

Publishes the Feature as a p2 update site that users add to their Eclipse
installation via **Help > Install New Software**.

---

## Feature Map -- All 29 Tools

### Model Chat Agent -- Read Tools (8)

| Tool Name | Category | Description | Access |
|-----------|----------|-------------|--------|
| `list_elements` | `capella.model` | List model elements by type and optional parent scope | READ |
| `get_element_details` | `capella.model` | Retrieve full details of a single element by ID | READ |
| `search_elements` | `capella.model` | Full-text search across element names and descriptions | READ |
| `get_hierarchy` | `capella.model` | Return the containment tree from a given root element | READ |
| `list_diagrams` | `capella.diagram` | List all diagrams or filter by type (xAB, xDFB, CDB, etc.) | READ |
| `get_traceability` | `capella.model` | Follow realization/refinement links between ARCADIA layers | READ |
| `list_requirements` | `capella.model` | List requirements linked to the model (Requirements VP) | READ |
| `validate_model` | `capella.model` | Run Capella validation rules and return violations | READ |

### Model Chat Agent -- Write Tools (6)

| Tool Name | Category | Description | Access |
|-----------|----------|-------------|--------|
| `create_element` | `capella.model` | Create a new model element (function, component, etc.) | WRITE |
| `create_exchange` | `capella.model` | Create a functional or component exchange between elements | WRITE |
| `allocate_function` | `capella.model` | Allocate a logical/physical function to a component | WRITE |
| `create_capability` | `capella.model` | Create an operational or system capability | WRITE |
| `update_element` | `capella.model` | Update properties (name, description, attributes) of an existing element | WRITE |
| `delete_element` | `capella.model` | Delete a model element with dependency checking | WRITE |

### Model Chat Agent -- Diagram Tools (2)

| Tool Name | Category | Description | Access |
|-----------|----------|-------------|--------|
| `update_diagram` | `capella.diagram` | Add or remove elements from a Sirius diagram | WRITE |
| `refresh_diagram` | `capella.diagram` | Force-refresh a diagram to reflect recent model changes | READ |

### Teamcenter Agent (7)

| Tool Name | Category | Description | Access |
|-----------|----------|-------------|--------|
| `search_teamcenter` | `teamcenter` | Full-text search across Teamcenter items by type and keyword | READ |
| `tc_get_object` | `teamcenter` | Retrieve a Teamcenter object by UID with full properties | READ |
| `tc_get_bom` | `teamcenter` | Retrieve the BOM (Bill of Materials) structure for an item revision | READ |
| `tc_list_requirements` | `teamcenter` | List requirements from a Teamcenter specification or folder | READ |
| `tc_import_requirement` | `teamcenter` | Import a Teamcenter requirement into the Capella model | WRITE |
| `tc_import_part` | `teamcenter` | Import a Teamcenter part/item as a physical component in Capella | WRITE |
| `tc_create_trace_link` | `teamcenter` | Create a traceability link between a Capella element and a Tc object | WRITE |

### Simulation Agent (6)

| Tool Name | Category | Description | Access |
|-----------|----------|-------------|--------|
| `list_simulation_engines` | `simulation` | Discover available simulation engines (MATLAB, etc.) and their status | READ |
| `extract_simulation_params` | `simulation` | Extract input parameters from a Capella model for a simulation run | READ |
| `run_simulation` | `simulation` | Execute a simulation model with extracted parameters | WRITE |
| `propagate_simulation_results` | `simulation` | Write simulation outputs back to Capella model element properties | WRITE |
| `run_what_if` | `simulation` | Run multiple simulation variants by sweeping parameter ranges | WRITE |
| `get_simulation_status` | `simulation` | Check the status of an in-progress or completed simulation run | READ |

---

## Key Interfaces & Extension Points

### `ILlmProvider` -- LLM Provider Contract

Every LLM backend implements this interface. The core plugin ships nine
providers (Claude, OpenAI, Groq, DeepSeek, Mistral, OpenRouter, Gemini,
Ollama, and Custom Endpoint). Six of the nine extend the
`OpenAiCompatibleProvider` abstract base class, so adding a new
OpenAI-compatible provider requires roughly 15 lines of code. Additional
providers can also be registered via the
`com.capellaagent.core.llmProvider` extension point.

```
ILlmProvider
  +-- getId(): String
  +-- getDisplayName(): String
  +-- chat(messages, tools, config): LlmResponse
```

### `IToolDescriptor` / `IToolExecutor` -- Tool Registry Pattern

Tools declare their name, description, category, and JSON Schema parameters
via `IToolDescriptor`. The `IToolExecutor` interface provides the
`execute(JsonObject)` method. `AbstractCapellaTool` implements both and adds:

- EMF session and editing-domain acquisition
- READ_ONLY / READ_WRITE security enforcement
- Transactional command execution with undo/redo
- Element serialization for LLM responses
- Audit logging on every invocation
- Structured success/error result builders
- Parameter validation helpers

### `ISimulationEngine` -- Simulation Engine Abstraction

Decouples the simulation agent from any specific CAE tool. The defined
lifecycle is: `connect()` -> `setParameters(Map)` -> `run(path, monitor)`
-> `disconnect()`. Two implementations ship: `MatlabEngineBridge` (Java
Engine API) and `MatlabCommandBridge` (CLI fallback).

### `IAgentMessageBus` -- Cross-Agent Events

A type-safe publish/subscribe bus. Publishers fire typed events
(`ModelChangedEvent`, `TcItemImportedEvent`, `SimulationResultEvent`),
and subscribers register by event class. Subscriptions return a disposable
handle.

### OSGi Extension Points

| Extension Point | Purpose |
|----------------|---------|
| `com.capellaagent.core.llmProvider` | Register custom LLM providers |
| `com.capellaagent.core.tool` | Register additional tools |
| `org.eclipse.ui.views` | Register chat and dashboard views |
| `org.eclipse.ui.commands` | Register UI commands (send, clear) |

---

## Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Platform** | Eclipse Capella 7.0 (Eclipse RCP / Equinox OSGi) | Host application and runtime container |
| **Language** | Java 17+ | All plugin code |
| **Modeling** | EMF (Eclipse Modeling Framework) | Capella metamodel access and manipulation |
| **Diagrams** | Sirius 7.0 | Programmatic diagram creation and layout |
| **UI** | SWT / Eclipse Workbench | Chat views, dashboard, preference pages |
| **JSON** | Google Gson 2.10 | Tool argument/result serialization, LLM API payloads |
| **HTTP** | `java.net.http.HttpClient` (JDK) | LLM API calls, Teamcenter REST calls |
| **LLM -- Cloud** | Anthropic Claude API, OpenAI API, Groq, DeepSeek, Mistral, OpenRouter, Google Gemini | Cloud-hosted inference (9 providers total) |
| **LLM -- Local** | Ollama, Custom Endpoint | Air-gapped / on-premises inference |
| **MCP** | Model Context Protocol (JSON-RPC 2.0 over stdio) | Claude Code integration via bridge subprocess + HTTP endpoint |
| **Security** | Eclipse Equinox Secure Storage | API key storage (never plaintext on disk) |
| **Build** | Eclipse Tycho / Maven | OSGi-aware build, p2 repository generation |
| **PLM** | Siemens Teamcenter Active Workspace REST API | Item search, BOM retrieval, requirement import |
| **CAE** | MATLAB Engine API for Java, MATLAB CLI | Simulation execution and result retrieval |
| **Testing** | JUnit 5, SWTBot (planned) | Unit and integration testing |

---

## Data Layer -- EMF Model, Not a Database

Capella Agent does not use a relational database. All persistent data lives in
the Capella model, which is an EMF resource set serialized to `.melodymodeller`
and `.aird` files.

### How Tools Access Data

```
Tool                             EMF API
-----------                      -------------------------------------------
list_elements                    EObject.eContents(), EClass filtering
get_element_details              Resource.getEObject(uri_fragment)
search_elements                  TreeIterator over resource set, name matching
create_element                   EFactory.create(), EList.add() in transaction
update_element                   EObject.eSet() in transaction
delete_element                   EcoreUtil.delete() in transaction
get_traceability                 Cross-reference resolution (AbstractTrace)
```

### Transactions and Undo

All write operations go through EMF `RecordingCommand` on the
`TransactionalEditingDomain`. This means:

- Every write is atomic -- it either completes fully or rolls back.
- Every write is undoable via the standard Eclipse **Edit > Undo** stack.
- Concurrent modifications are serialized by the editing domain.

### External Data Sources

| Source | Protocol | Data Flow |
|--------|----------|-----------|
| Teamcenter | REST / JSON | Read from Tc, map to EMF, write to Capella model |
| MATLAB/Simulink | Java Engine API or CLI | Extract params from model, send to MATLAB, receive results, write back to model |
| LLM providers | HTTPS / JSON | Send conversation + tool schemas, receive text + tool calls |

---

## Security Model

### Access Control

The `SecurityService` enforces a global access mode:

| Mode | Behavior |
|------|----------|
| `READ_ONLY` (default) | Only read tools execute. Write tools throw `ERR_PERMISSION_DENIED`. |
| `READ_WRITE` | All tools execute. Requires explicit user opt-in via preferences. |

Write tools call `requireWriteMode()` before performing any mutation.
The access mode can be changed at runtime through the Capella preferences
UI.

### API Key Storage

LLM API keys and Teamcenter credentials are stored in Eclipse Equinox Secure
Storage, which encrypts secrets using OS-level credential stores (Windows
Credential Manager, macOS Keychain, or Linux libsecret). Keys are never
written to preference files or logs.

### Audit Logging

Every tool invocation is logged through `AuditLogger` with:

- Tool name
- Input arguments (sanitized)
- Success/failure status
- Timestamp
- Result summary

Audit logging is enabled by default and can be toggled via
`AgentConfiguration`.

---

## Cross-Agent Communication

The three agents communicate through `IAgentMessageBus`, a typed
publish/subscribe event bus.

### Event Types

| Event | Publisher | Subscribers | Payload |
|-------|-----------|-------------|---------|
| `ModelChangedEvent` | Model Chat Agent (write tools) | Simulation Agent (re-extract params), UI (refresh views) | Changed element ID, change type |
| `TcItemImportedEvent` | Teamcenter Agent (import tools) | Model Chat Agent (notify user), Simulation Agent (check for new params) | Imported element ID, Tc source UID |
| `SimulationResultEvent` | Simulation Agent (run tools) | Model Chat Agent (report to user), UI (update dashboard) | Simulation ID, status, output values |

### Example Flow: End-to-End Simulation

```
User: "Import the brake caliper from Teamcenter, then simulate its thermal load"

1. Teamcenter Agent: tc_import_part("brake caliper")
   --> Creates PhysicalComponent in Capella
   --> Publishes TcItemImportedEvent

2. Simulation Agent receives TcItemImportedEvent
   --> extract_simulation_params(new component)
   --> run_simulation("thermal_analysis.slx", params)
   --> propagate_simulation_results(outputs)
   --> Publishes SimulationResultEvent

3. Model Chat Agent receives SimulationResultEvent
   --> Reports results to user in chat
```

---

## Roadmap

### Phase 1: Foundation (Weeks 1-3)

| Deliverable | Status | Details |
|-------------|--------|---------|
| `com.capellaagent.core` plugin structure | Complete | OSGi bundle with META-INF, plugin.xml, build.properties |
| `ILlmProvider` interface and registry | Complete | Provider-agnostic LLM abstraction |
| Claude, OpenAI, Ollama providers | Complete | Initial three providers with tool-calling support |
| `IToolDescriptor` / `IToolExecutor` framework | Complete | Tool registry with JSON Schema validation |
| `AbstractCapellaTool` base class | Complete | Transaction wrapping, security, audit |
| `IAgentMessageBus` and event types | Complete | Typed pub/sub for cross-agent coordination |
| `SecurityService` and `AuditLogger` | Complete | Access control, credential storage, audit trail |
| `AgentConfiguration` preferences | Complete | Eclipse preference store integration |

### Phase 2: Model Chat Agent (Weeks 3-6)

| Deliverable | Status | Details |
|-------------|--------|---------|
| 8 read tools | Complete | list, get, search, hierarchy, diagrams, traceability, requirements, validate |
| 6 write tools | Complete | create element/exchange/capability, allocate, update, delete |
| 2 diagram tools | Complete | update_diagram, refresh_diagram |
| `ChatComposite` reusable widget | Complete | SWT chat widget with markdown rendering |
| `ModelChatView` dockable view | Complete | Eclipse view with send/clear commands |
| `ElementLinkAdapter` | Complete | Clickable element references in chat |

### Phase 3: Teamcenter Agent (Weeks 6-8)

| Deliverable | Status | Details |
|-------------|--------|---------|
| `TcRestClient` with session management | Complete | Active Workspace REST, token refresh |
| 4 read tools | Complete | search, get_object, get_bom, list_requirements |
| 3 write tools | Complete | import_requirement, import_part, create_trace_link |
| `TcToCapellaMapper` | Complete | Tc item-to-EMF element mapping |
| `RequirementImporter` / `PartImporter` | Complete | Structured import with conflict detection |

### Phase 4: Simulation Agent (Weeks 8-11)

| Deliverable | Status | Details |
|-------------|--------|---------|
| `ISimulationEngine` interface | Complete | Engine abstraction with lifecycle |
| `MatlabEngineBridge` (Java API) | Complete | In-process MATLAB connection |
| `MatlabCommandBridge` (CLI fallback) | Complete | Command-line MATLAB for environments without Engine API |
| `SimulationOrchestrator` | Complete | End-to-end extract-run-propagate workflow |
| `WhatIfManager` | Complete | Parameter sweep and variant comparison |
| 6 simulation tools | Complete | list, extract, run, propagate, what_if, status |
| `SimulationDashboardView` | Complete | Eclipse view for run management |

### Phase 5: Integration & Polish (Weeks 11-13)

| Deliverable | Status | Details |
|-------------|--------|---------|
| Cross-agent event flows | Planned | Full TcItemImported -> Simulate pipeline |
| Eclipse Feature packaging | Complete | feature.xml with all 8 plugins |
| p2 Update Site | Complete | category.xml for install-new-software |
| End-to-end integration tests | Planned | Multi-agent scenario tests |
| User documentation | Planned | Install guide, user manual, tool reference |
| Performance profiling | Planned | Large model (10k+ elements) benchmarks |

### Phase 6: Claude Code Integration (Complete)

| Deliverable | Status | Details |
|-------------|--------|---------|
| `com.capellaagent.mcp` plugin | Complete | MCP Server bridge exposing all tools to Claude Code |
| `McpHttpEndpoint` | Complete | Embedded HTTP server on localhost:9847 |
| `McpServerBridge` + `McpStdioTransport` | Complete | Bridge subprocess translating MCP stdio (JSON-RPC 2.0) to HTTP |
| `McpToolAdapter` | Complete | Bidirectional translation between MCP tool schemas and internal tool contracts |
| `.mcp.json` configuration | Complete | Project-root config file for Claude Code discovery |
| `OpenAiCompatibleProvider` base class | Complete | Abstract base enabling ~15-line new provider implementations |
| 6 additional LLM providers | Complete | Groq, DeepSeek, Mistral, OpenRouter, Gemini, Custom Endpoint |

### Future Directions

| Initiative | Description |
|------------|-------------|
| **Multi-model support** | Operate across multiple open Capella projects simultaneously |
| **ReqIF integration** | Direct ReqIF import/export alongside Teamcenter |
| **OpenModelica engine** | Additional simulation engine for open-source CAE |
| **Agentic workflows** | Multi-step plans where agents chain tool calls autonomously |
| **Model diffing** | Compare model snapshots before/after agent modifications |
| **Team collaboration** | Shared conversation history across Capella Team for Eclipse |
| **Custom tool SDK** | Public API for third parties to register domain-specific tools |

---

## File Structure

```
capella-agent/
|
+-- com.capellaagent.core/                          # Foundation plugin (35 files)
|   +-- META-INF/
|   |   +-- MANIFEST.MF
|   +-- build.properties
|   +-- plugin.xml
|   +-- src/com/capellaagent/core/
|       +-- Activator.java
|       +-- bus/
|       |   +-- AgentEvent.java
|       |   +-- AgentMessageBus.java
|       |   +-- IAgentMessageBus.java
|       |   +-- ModelChangedEvent.java
|       |   +-- SimulationResultEvent.java
|       |   +-- TcItemImportedEvent.java
|       +-- config/
|       |   +-- AgentConfiguration.java
|       +-- llm/
|       |   +-- ILlmProvider.java
|       |   +-- LlmException.java
|       |   +-- LlmMessage.java
|       |   +-- LlmProviderRegistry.java
|       |   +-- LlmRequestConfig.java
|       |   +-- LlmResponse.java
|       |   +-- LlmToolCall.java
|       |   +-- LlmToolResult.java
|       |   +-- providers/
|       |       +-- ClaudeProvider.java
|       |       +-- CustomEndpointProvider.java
|       |       +-- DeepSeekProvider.java
|       |       +-- GeminiProvider.java
|       |       +-- GroqProvider.java
|       |       +-- MistralProvider.java
|       |       +-- OllamaProvider.java
|       |       +-- OpenAiCompatibleProvider.java
|       |       +-- OpenAiProvider.java
|       |       +-- OpenRouterProvider.java
|       +-- security/
|       |   +-- AccessMode.java
|       |   +-- AuditLogger.java
|       |   +-- SecurityService.java
|       +-- session/
|       |   +-- ConversationSession.java
|       +-- tools/
|       |   +-- AbstractCapellaTool.java
|       |   +-- IToolDescriptor.java
|       |   +-- IToolExecutor.java
|       |   +-- ToolExecutionException.java
|       |   +-- ToolRegistration.java
|       |   +-- ToolRegistry.java
|       |   +-- ToolSchemaBuilder.java
|       +-- util/
|           +-- CapellaSessionUtil.java
|           +-- JsonUtil.java
|           +-- WorkspaceUtil.java
|
+-- com.capellaagent.core.ui/                       # Shared UI components (2 files)
|   +-- META-INF/
|   |   +-- MANIFEST.MF
|   +-- build.properties
|   +-- src/com/capellaagent/core/ui/
|       +-- rendering/
|       |   +-- MarkdownRenderer.java
|       +-- widgets/
|           +-- ChatComposite.java
|
+-- com.capellaagent.modelchat/                     # Model Chat Agent (18 files)
|   +-- META-INF/
|   |   +-- MANIFEST.MF
|   +-- build.properties
|   +-- plugin.xml
|   +-- src/com/capellaagent/modelchat/
|       +-- ModelChatActivator.java
|       +-- ModelChatToolRegistrar.java
|       +-- tools/
|           +-- diagram/
|           |   +-- RefreshDiagramTool.java
|           |   +-- UpdateDiagramTool.java
|           +-- read/
|           |   +-- GetElementDetailsTool.java
|           |   +-- GetHierarchyTool.java
|           |   +-- GetTraceabilityTool.java
|           |   +-- ListDiagramsTool.java
|           |   +-- ListElementsTool.java
|           |   +-- ListRequirementsTool.java
|           |   +-- SearchElementsTool.java
|           |   +-- ValidateModelTool.java
|           +-- write/
|               +-- AllocateFunctionTool.java
|               +-- CreateCapabilityTool.java
|               +-- CreateElementTool.java
|               +-- CreateExchangeTool.java
|               +-- DeleteElementTool.java
|               +-- UpdateElementTool.java
|
+-- com.capellaagent.modelchat.ui/                  # Model Chat UI (6 files)
|   +-- META-INF/
|   |   +-- MANIFEST.MF
|   +-- build.properties
|   +-- plugin.xml
|   +-- src/com/capellaagent/modelchat/ui/
|       +-- ModelChatUiActivator.java
|       +-- adapters/
|       |   +-- ElementLinkAdapter.java
|       +-- commands/
|       |   +-- ClearHistoryHandler.java
|       |   +-- SendMessageHandler.java
|       +-- views/
|           +-- ChatJob.java
|           +-- ModelChatView.java
|
+-- com.capellaagent.teamcenter/                    # Teamcenter Agent (20 files)
|   +-- META-INF/
|   |   +-- MANIFEST.MF
|   +-- build.properties
|   +-- plugin.xml
|   +-- src/com/capellaagent/teamcenter/
|       +-- TcActivator.java
|       +-- TcToolRegistrar.java
|       +-- api/
|       |   +-- TcBomService.java
|       |   +-- TcObjectService.java
|       |   +-- TcRequirementsService.java
|       |   +-- TcSearchService.java
|       +-- client/
|       |   +-- TcConfiguration.java
|       |   +-- TcException.java
|       |   +-- TcRestClient.java
|       |   +-- TcSession.java
|       +-- import_/
|       |   +-- PartImporter.java
|       |   +-- RequirementImporter.java
|       |   +-- TcToCapellaMapper.java
|       +-- tools/
|           +-- TcGetBomTool.java
|           +-- TcGetObjectTool.java
|           +-- TcImportPartTool.java
|           +-- TcImportRequirementTool.java
|           +-- TcLinkTool.java
|           +-- TcListRequirementsTool.java
|           +-- TcSearchTool.java
|
+-- com.capellaagent.simulation/                    # Simulation Agent (22 files)
|   +-- META-INF/
|   |   +-- MANIFEST.MF
|   +-- build.properties
|   +-- plugin.xml
|   +-- src/com/capellaagent/simulation/
|       +-- SimActivator.java
|       +-- SimToolRegistrar.java
|       +-- bridge/
|       |   +-- ISimulationEngine.java
|       |   +-- MatlabCommandBridge.java
|       |   +-- MatlabEngineBridge.java
|       |   +-- SimulationEngineException.java
|       |   +-- SimulationResult.java
|       +-- config/
|       |   +-- SimulationPreferences.java
|       +-- orchestrator/
|       |   +-- ParameterExtractor.java
|       |   +-- ParameterMapping.java
|       |   +-- ResultMapping.java
|       |   +-- ResultPropagator.java
|       |   +-- SimulationConfig.java
|       |   +-- SimulationOrchestrator.java
|       |   +-- WhatIfManager.java
|       |   +-- WhatIfSpec.java
|       +-- tools/
|           +-- ExtractParamsTool.java
|           +-- GetSimStatusTool.java
|           +-- ListEnginesTool.java
|           +-- PropagateResultsTool.java
|           +-- RunSimulationTool.java
|           +-- WhatIfTool.java
|
+-- com.capellaagent.simulation.ui/                 # Simulation Dashboard (1 file)
|   +-- META-INF/
|   |   +-- MANIFEST.MF
|   +-- build.properties
|   +-- plugin.xml
|   +-- src/com/capellaagent/simulation/ui/views/
|       +-- SimulationDashboardView.java
|
+-- com.capellaagent.mcp/                             # MCP Server Bridge (5 files)
|   +-- META-INF/
|   |   +-- MANIFEST.MF
|   +-- build.properties
|   +-- plugin.xml
|   +-- src/com/capellaagent/mcp/
|       +-- McpActivator.java
|       +-- McpHttpEndpoint.java
|       +-- McpServerBridge.java
|       +-- McpStdioTransport.java
|       +-- McpToolAdapter.java
|
+-- com.capellaagent.feature/                       # Eclipse Feature packaging
|   +-- build.properties
|   +-- feature.xml
|
+-- com.capellaagent.site/                          # p2 Update Site
    +-- build.properties
    +-- category.xml
```

---

*Capella Agent is licensed under the Eclipse Public License v2.0.*
*Copyright (c) 2024 CapellaAgent Contributors.*
