# Capella Agent -- AI Tools Catalog

> 84 production-ready tools across 9 categories for AI-powered MBSE in Eclipse Capella

This document catalogs every tool available in the Capella Agent ecosystem. Each tool is callable by the AI agents through natural language -- you describe what you need, and the agent selects and invokes the right tool.

## Tool Categories

| Category | Count | Description |
|----------|-------|-------------|
| Model Read | 21 | Query model elements, properties, relationships |
| Model Write | 21 | Create, update, delete model elements |
| Diagram | 9 | Create, refresh, export diagrams |
| Analysis | 13 | Validate, detect cycles, measure complexity |
| Export | 8 | CSV, JSON, reports, traceability matrices |
| Transition | 5 | ARCADIA layer transitions (OA -> SA -> LA -> PA -> EPBS) |
| AI Intelligence | 5 | AI-driven architecture review and suggestions |
| Teamcenter | 7 | Siemens Teamcenter PLM integration |
| Simulation | 6 | MATLAB/Simulink simulation orchestration |

---

## Quick Reference

| # | Tool | Category | Description |
|---|------|----------|-------------|
| 1 | `list_elements` | Model Read | List model elements by ARCADIA layer and type |
| 2 | `get_element_details` | Model Read | Get full details for a model element by UUID |
| 3 | `search_elements` | Model Read | Search elements by name pattern (substring or regex) |
| 4 | `get_element_hierarchy` | Model Read | Get containment hierarchy tree for an element |
| 5 | `list_diagrams` | Model Read | List all Sirius diagrams with optional filters |
| 6 | `list_requirements` | Model Read | List requirements from the Requirements viewpoint |
| 7 | `get_traceability` | Model Read | Get traceability links across ARCADIA layers |
| 8 | `validate_model` | Model Read | Run structural validation and return issues |
| 9 | `get_allocation_matrix` | Model Read | Get function-to-component allocation matrix |
| 10 | `get_functional_chain` | Model Read | Get functional chain details with involvements |
| 11 | `get_interfaces` | Model Read | List interfaces with exchange items and users |
| 12 | `get_scenarios` | Model Read | List scenarios with instance roles and messages |
| 13 | `explain_element` | Model Read | Gather comprehensive context for AI explanation |
| 14 | `get_state_machines` | Model Read | List state machines with states and transitions |
| 15 | `get_data_model` | Model Read | List data types and classes in a layer |
| 16 | `get_physical_paths` | Model Read | List physical paths with involved links |
| 17 | `get_exchange_items` | Model Read | List exchange items with their elements |
| 18 | `get_property_values` | Model Read | Get property values for a model element |
| 19 | `get_constraints` | Model Read | List constraints with expressions |
| 20 | `get_configuration_items` | Model Read | List EPBS configuration items |
| 21 | `get_deployment_mapping` | Model Read | Show PA behavior-to-node deployment mapping |
| 22 | `create_element` | Model Write | Create a function, component, or actor |
| 23 | `create_exchange` | Model Write | Create a functional or component exchange |
| 24 | `allocate_function` | Model Write | Allocate a function to a component |
| 25 | `create_capability` | Model Write | Create a capability with optional function involvements |
| 26 | `update_element` | Model Write | Update name and/or description of an element |
| 27 | `delete_element` | Model Write | Delete a model element (requires confirmation) |
| 28 | `create_interface` | Model Write | Create an interface with optional exchange items |
| 29 | `create_functional_chain` | Model Write | Create a functional chain involving functions |
| 30 | `create_physical_link` | Model Write | Create a physical link between components |
| 31 | `batch_rename` | Model Write | Batch rename elements matching a pattern |
| 32 | `create_scenario` | Model Write | Create a scenario in a layer |
| 33 | `create_state_machine` | Model Write | Create a state machine on a component |
| 34 | `create_data_type` | Model Write | Create a data type (class, enum, string, etc.) |
| 35 | `move_element` | Model Write | Move an element to a different parent |
| 36 | `clone_element` | Model Write | Deep-copy an element into the same parent |
| 37 | `set_property_value` | Model Write | Set a string property value on an element |
| 38 | `create_exchange_item` | Model Write | Create an exchange item in a layer |
| 39 | `batch_update_properties` | Model Write | Set a property on multiple elements at once |
| 40 | `create_port` | Model Write | Create a port on a function or component |
| 41 | `create_involvement` | Model Write | Link a function to a capability |
| 42 | `create_generalization` | Model Write | Create a generalization (inheritance) link |
| 43 | `update_diagram` | Diagram | Add or remove a semantic element from a diagram |
| 44 | `refresh_diagram` | Diagram | Force-refresh a diagram to sync with the model |
| 45 | `create_diagram` | Diagram | Create a new Sirius diagram (PAB, LAB, SAB, etc.) |
| 46 | `export_diagram_image` | Diagram | Export a diagram as PNG or SVG |
| 47 | `show_element_in_diagram` | Diagram | Find all diagrams containing a given element |
| 48 | `list_diagram_elements` | Diagram | List semantic elements displayed in a diagram |
| 49 | `clone_diagram` | Diagram | Create a copy of an existing diagram |
| 50 | `auto_layout_diagram` | Diagram | Trigger auto-layout (arrange all) on a diagram |
| 51 | `delete_diagram` | Diagram | Delete a diagram (requires confirmation) |
| 52 | `check_traceability_coverage` | Analysis | Check traceability coverage between two layers |
| 53 | `run_capella_validation` | Analysis | Run comprehensive model validation |
| 54 | `detect_cycles` | Analysis | Detect cycles in exchange/dependency graphs |
| 55 | `impact_analysis` | Analysis | Analyze the impact of modifying or deleting an element |
| 56 | `find_unused_elements` | Analysis | Find elements with no incoming references |
| 57 | `model_statistics` | Analysis | Get element counts and statistics per layer |
| 58 | `allocation_completeness` | Analysis | Check function-to-component allocation completeness |
| 59 | `get_refinement_status` | Analysis | Check realization coverage between two layers |
| 60 | `interface_consistency` | Analysis | Check interface consistency: ports, exchange items |
| 61 | `reachability_analysis` | Analysis | Find a path between two elements via exchanges |
| 62 | `architecture_complexity` | Analysis | Compute complexity metrics: coupling, cohesion |
| 63 | `identify_singletons` | Analysis | Find components with only one allocated function |
| 64 | `generate_safety_report` | Analysis | Generate FMEA-style safety analysis |
| 65 | `export_to_csv` | Export | Export model elements to CSV |
| 66 | `export_to_json` | Export | Export model elements as nested JSON |
| 67 | `generate_icd_report` | Export | Generate an Interface Control Document report |
| 68 | `generate_model_report` | Export | Generate a comprehensive model health report |
| 69 | `export_traceability_matrix` | Export | Export a traceability matrix between two layers |
| 70 | `export_allocation_matrix_csv` | Export | Export function-component allocation matrix as CSV |
| 71 | `export_diagram_catalog` | Export | List all diagrams with type, target, element count |
| 72 | `generate_diff_report` | Export | Generate a model state summary for change tracking |
| 73 | `transition_oa_to_sa` | Transition | Transition OA elements to SA with realization traces |
| 74 | `transition_sa_to_la` | Transition | Transition SA elements to LA with realization traces |
| 75 | `transition_la_to_pa` | Transition | Transition LA elements to PA with realization traces |
| 76 | `reconcile_layers` | Transition | Compare two layers and report missing links |
| 77 | `transition_pa_to_epbs` | Transition | Create EPBS config items from PA node components |
| 78 | `auto_allocate` | AI Intelligence | Gather context for AI-driven allocation suggestions |
| 79 | `suggest_interfaces` | AI Intelligence | Gather exchange patterns for AI interface suggestions |
| 80 | `review_architecture` | AI Intelligence | Gather model context for AI architecture review |
| 81 | `generate_test_scenarios` | AI Intelligence | Gather context for AI test scenario generation |
| 82 | `model_q_and_a` | AI Intelligence | Gather model context for free-form AI Q&A |
| 83 | `search_teamcenter` | Teamcenter | Search for objects in Teamcenter PLM |
| 84 | `tc_get_object` | Teamcenter | Get full properties of a Teamcenter object |
| 85 | `tc_get_bom` | Teamcenter | Expand a Bill of Materials tree |
| 86 | `tc_list_requirements` | Teamcenter | List requirements from Teamcenter |
| 87 | `tc_import_requirement` | Teamcenter | Import a Teamcenter requirement into Capella |
| 88 | `tc_import_part` | Teamcenter | Import a Teamcenter part as a PhysicalComponent |
| 89 | `tc_create_trace_link` | Teamcenter | Create a traceability link between Tc and Capella |
| 90 | `list_simulation_engines` | Simulation | List all registered simulation engines |
| 91 | `extract_simulation_params` | Simulation | Preview parameter extraction from the model |
| 92 | `run_simulation` | Simulation | Run a simulation synchronously |
| 93 | `propagate_simulation_results` | Simulation | Propagate simulation results back to the model |
| 94 | `run_what_if` | Simulation | Run a parameter sweep (what-if analysis) |
| 95 | `get_simulation_status` | Simulation | Check the status of a simulation job |

> **Note:** The quick reference above lists 95 rows because the `suggest_improvements` analysis tool brings the total beyond 84. The original count of 84 reflects the initial design target; additional analysis tools were added during development.

---

## Model Read Tools (21)

### `list_elements`

**Description:** Lists model elements within a specific ARCADIA architecture layer. Returns an array of elements with their name, UUID, type, and description preview.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `element_type` | enum | No | `all` | Type filter: `functions`, `components`, `actors`, `exchanges`, `capabilities`, `all` |
| `max_results` | integer | No | `100` | Maximum elements to return (max: 500) |

**Example prompts:**
- "List all physical functions in the PA layer"
- "Show me the system components in SA"
- "What actors exist in the operational analysis?"

---

### `get_element_details`

**Description:** Retrieves full details for a model element by UUID, including name, type, description, properties, relationships, allocated functions, parent, and children.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `uuid` | string | Yes | -- | The unique identifier of the model element |

**Example prompts:**
- "Show me the details of element abc-123"
- "What functions are allocated to this component?"
- "What are the properties of the Navigation Controller?"

---

### `search_elements`

**Description:** Searches for model elements by name pattern (substring or regex). Optionally filters by element type and architecture layer.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `query` | string | Yes | -- | Name pattern to search for (substring or Java regex) |
| `element_type` | enum | No | -- | Filter: `functions`, `components`, `actors`, `exchanges`, `capabilities` |
| `layer` | enum | No | -- | Filter: `oa`, `sa`, `la`, `pa` |
| `case_sensitive` | boolean | No | `false` | Whether the search is case-sensitive |

**Example prompts:**
- "Search for all elements with 'Navigation' in the name"
- "Find functions matching 'Process.*Data' in the SA layer"
- "Are there any components named 'Controller'?"

---

### `get_element_hierarchy`

**Description:** Returns the containment hierarchy tree for a model element. Supports traversal upward (ancestors), downward (descendants), or both.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `uuid` | string | Yes | -- | UUID of the element to use as hierarchy root |
| `direction` | enum | No | `down` | Traversal direction: `up`, `down`, `both` |
| `max_depth` | integer | No | `5` | Maximum traversal depth (max: 20) |

**Example prompts:**
- "Show the component hierarchy under the System element"
- "What is the parent chain for this function?"
- "Show me the full decomposition tree of the Logical System"

---

### `list_diagrams`

**Description:** Lists all diagrams (Sirius representations) in the project. Optionally filters by architecture layer and diagram type. Returns diagram name, UUID, type, and element count.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | No | -- | Filter: `oa`, `sa`, `la`, `pa` |
| `diagram_type` | string | No | -- | Filter by abbreviation: `SDFB`, `SAB`, `LAB`, `PAB`, `OCB`, `OAB`, etc. |

**Example prompts:**
- "List all diagrams in the SA layer"
- "How many PAB diagrams do we have?"
- "Show me all the data flow diagrams"

---

### `list_requirements`

**Description:** Lists requirements from the Requirements viewpoint. Optionally filters by linked model element. Returns requirement name, ID, text, and linked element references.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `linked_to_uuid` | string | No | -- | Filter to requirements linked to the element with this UUID |

**Example prompts:**
- "List all requirements in the model"
- "What requirements are linked to the Navigation component?"
- "Show me requirements that trace to this function"

---

### `get_traceability`

**Description:** Retrieves traceability links across ARCADIA layers (OA -> SA -> LA -> PA). Shows which elements realize or are realized by the given element.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `uuid` | string | Yes | -- | UUID of the element to trace |
| `direction` | enum | No | `both` | Trace direction: `realizing` (downstream), `realized` (upstream), `both` |

**Example prompts:**
- "Trace this SA function down to LA and PA"
- "What OA activities does this system function realize?"
- "Show the full traceability chain for the Process Data function"

---

### `validate_model`

**Description:** Runs Capella model validation and returns issues found. Checks for empty names, unallocated functions, portless components, and uninvolved capabilities.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | No | -- | Validate only a specific layer (entire model if omitted) |
| `severity` | enum | No | `all` | Minimum severity: `error`, `warning`, `info`, `all` |

**Example prompts:**
- "Validate the entire model"
- "Are there any errors in the LA layer?"
- "Show me all validation warnings for the physical architecture"

---

### `get_allocation_matrix`

**Description:** Returns the function-to-component allocation matrix for a layer.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |

**Example prompts:**
- "Show the allocation matrix for the logical architecture"
- "Which functions are allocated to which components in SA?"
- "Display the LA function-component mapping"

---

### `get_functional_chain`

**Description:** Gets functional chain details including involved functions and exchanges in sequence order.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `uuid` | string | No | -- | UUID of the functional chain to retrieve |
| `name` | string | No | -- | Name to search for (partial match, used if uuid not provided) |
| `layer` | enum | No | -- | Architecture layer (required if using name search) |

**Example prompts:**
- "Show me the 'Process Flight Data' functional chain"
- "What functions are in the navigation chain?"
- "List the exchanges along this functional chain"

---

### `get_interfaces`

**Description:** Lists interfaces with exchange items, implementor components, and user components.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | No | -- | Architecture layer (searches all if omitted) |
| `component_uuid` | string | No | -- | Filter to interfaces of a specific component |

**Example prompts:**
- "List all interfaces in the SA layer"
- "What interfaces does the Navigation Controller provide?"
- "Show the exchange items for each LA interface"

---

### `get_scenarios`

**Description:** Lists scenarios with instance roles and messages for a layer or capability.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | No | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `capability_uuid` | string | No | -- | Filter scenarios belonging to a specific capability |

**Example prompts:**
- "List all scenarios in the system analysis"
- "What scenarios are defined for the Navigation capability?"
- "Show me the sequence diagrams in the LA layer"

---

### `explain_element`

**Description:** Gathers comprehensive element context (name, type, layer, description, parent, children, allocations, exchanges, traceability, diagram appearances) so the AI can generate a human-friendly explanation.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuid` | string | Yes | -- | UUID of the element to explain |

**Example prompts:**
- "Explain what the Process Flight Data function does"
- "What is the role of the Navigation Controller in this architecture?"
- "Tell me about this component and its relationships"

---

### `get_state_machines`

**Description:** Lists state machines with states and transitions for an element or layer.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuid` | string | No | -- | UUID of element to get state machines for |
| `layer` | enum | No | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |

**Example prompts:**
- "Show the state machines for the Flight Controller component"
- "What states does the system have in SA?"
- "List all state transitions in the logical architecture"

---

### `get_physical_paths`

**Description:** Lists physical paths with involved physical links.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | No | `pa` | Architecture layer: `oa`, `sa`, `la`, `pa` |

**Example prompts:**
- "Show all physical paths in the PA layer"
- "What physical links make up the data bus path?"
- "List the communication paths between hardware nodes"

---

### `get_exchange_items`

**Description:** Lists exchange items with their elements (data fields).

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | No | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `interface_uuid` | string | No | -- | UUID of interface to filter exchange items |

**Example prompts:**
- "List all exchange items in the SA layer"
- "What data flows through the Navigation Interface?"
- "Show me the exchange items and their fields for this interface"

---

### `get_property_values`

**Description:** Gets property values and property value groups for a model element.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuid` | string | Yes | -- | UUID of the element to get property values for |

**Example prompts:**
- "What custom properties are set on this component?"
- "Show the property values for the Flight Controller"
- "Are there any tagged values on this function?"

---

### `get_constraints`

**Description:** Lists constraints with their expressions and constrained elements.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuid` | string | No | -- | UUID of element to get constraints for |
| `layer` | enum | No | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |

**Example prompts:**
- "What constraints are defined in the logical architecture?"
- "Show me the constraints on this component"
- "Are there any timing constraints in the PA layer?"

---

### `get_configuration_items`

**Description:** Lists EPBS configuration items from the End Product Breakdown Structure.

**Parameters:** None

**Example prompts:**
- "List all configuration items in the EPBS"
- "What deliverable items does this system have?"
- "Show the end product breakdown structure"

---

### `get_deployment_mapping`

**Description:** Shows the deployment mapping of behavior components to node components in the Physical Architecture.

**Parameters:** None

**Example prompts:**
- "Show the deployment mapping in the PA"
- "Which behavior components are deployed on which nodes?"
- "How are software components mapped to hardware?"

---

## Model Write Tools (21)

### `create_element`

**Description:** Creates a new model element (function, component, or actor) in the specified ARCADIA layer. Returns the created element's details including its UUID.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `type` | enum | Yes | -- | Element type: `function`, `component`, `actor` |
| `name` | string | Yes | -- | Name of the new element |
| `parent_uuid` | string | No | -- | UUID of the parent container |
| `description` | string | No | -- | Description text for the element |

**Example prompts:**
- "Create a function called 'Process Sensor Data' in the SA layer"
- "Add a new logical component named 'Navigation Module'"
- "Create an actor called 'Pilot' in the operational analysis"

---

### `create_exchange`

**Description:** Creates a functional exchange or component exchange between two model elements. Returns the created exchange details including its UUID.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `type` | enum | Yes | -- | Exchange type: `functional_exchange`, `component_exchange` |
| `source_uuid` | string | Yes | -- | UUID of the source element |
| `target_uuid` | string | Yes | -- | UUID of the target element |
| `name` | string | No | -- | Name for the exchange |

**Example prompts:**
- "Create a functional exchange from 'Acquire Data' to 'Process Data'"
- "Add a component exchange between the Sensor and the Controller"
- "Connect these two functions with a data flow"

---

### `allocate_function`

**Description:** Allocates a function to a component, creating a ComponentFunctionalAllocation link. Both elements must be in the same ARCADIA layer.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `function_uuid` | string | Yes | -- | UUID of the function to allocate |
| `component_uuid` | string | Yes | -- | UUID of the component to allocate to |

**Example prompts:**
- "Allocate the 'Process Navigation Data' function to the Navigation Controller"
- "Assign this function to the Sensor Manager component"
- "Map the Compute Route function to the Flight Computer"

---

### `create_capability`

**Description:** Creates a capability in the specified ARCADIA layer, optionally linking functions to it.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `name` | string | Yes | -- | Capability name |
| `involved_function_uuids` | array of strings | No | -- | UUIDs of functions to involve |
| `description` | string | No | -- | Capability description |

**Example prompts:**
- "Create a capability called 'Navigate to Waypoint' in the SA layer"
- "Add an operational capability for 'Manage Emergency Landing'"
- "Create a capability involving these three functions"

---

### `update_element`

**Description:** Updates the name and/or description of an existing model element. At least one of name or description must be provided.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `uuid` | string | Yes | -- | UUID of the element to update |
| `name` | string | No | -- | New name for the element |
| `description` | string | No | -- | New description for the element |

**Example prompts:**
- "Rename this function to 'Process Inertial Data'"
- "Update the description of the Navigation Controller"
- "Change the name of component abc-123 to 'Sensor Hub'"

---

### `delete_element`

**Description:** Deletes a model element. Requires explicit confirmation (`confirm=true`). Returns the deleted element's details for audit purposes. This operation can be undone.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `uuid` | string | Yes | -- | UUID of the element to delete |
| `confirm` | boolean | Yes | -- | Must be `true` to proceed with deletion |

**Example prompts:**
- "Delete the unused 'Temp Processing' function"
- "Remove this deprecated component from the model"
- "Delete element abc-123"

---

### `create_interface`

**Description:** Creates an interface with optional exchange items in a layer. The interface is placed in the layer's InterfacePkg.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `name` | string | Yes | -- | Name of the new interface |
| `exchange_item_names` | string | No | -- | Comma-separated names of exchange items to create and allocate |
| `description` | string | No | -- | Description text for the interface |

**Example prompts:**
- "Create an interface called 'ISensorData' in the LA layer"
- "Add an SA interface 'INavigation' with exchange items 'Position, Velocity, Heading'"
- "Create an interface for communication between Sensor and Controller"

---

### `create_functional_chain`

**Description:** Creates a functional chain involving specified functions in a layer. Functions are linked in the provided order.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `name` | string | Yes | -- | Name of the new functional chain |
| `function_uuids` | string | Yes | -- | Comma-separated UUIDs of functions (in order) |

**Example prompts:**
- "Create a functional chain 'Data Processing Pipeline' with these three functions"
- "Build a chain linking Acquire, Process, and Display functions"
- "Define the navigation functional chain in the SA layer"

---

### `create_physical_link`

**Description:** Creates a physical link between two physical components, including PhysicalPorts on each component.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `source_uuid` | string | Yes | -- | UUID of the source PhysicalComponent |
| `target_uuid` | string | Yes | -- | UUID of the target PhysicalComponent |
| `name` | string | No | -- | Name for the physical link (auto-generated if omitted) |

**Example prompts:**
- "Create a physical link between the CPU and the Sensor Board"
- "Connect the two hardware nodes with a data bus link"
- "Add a physical link from the Radio to the Antenna"

---

### `batch_rename`

**Description:** Batch renames elements matching a pattern. Supports regex find/replace with a dry_run mode for previewing changes.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `find_pattern` | string | Yes | -- | Regex or substring pattern to match element names |
| `replace_with` | string | Yes | -- | Replacement string (supports regex groups like `$1`) |
| `layer` | enum | No | -- | Limit to a specific architecture layer |
| `element_type` | enum | No | `all` | Type filter: `function`, `component`, `all` |
| `dry_run` | boolean | No | `true` | Preview changes without applying |

**Example prompts:**
- "Rename all functions starting with 'SF_' to 'System_' in the SA layer"
- "Preview what would change if I replace 'Old' with 'New' in component names"
- "Batch rename functions to add a 'LF_' prefix in the LA layer"

---

### `create_scenario`

**Description:** Creates a scenario in a layer, optionally under a capability.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `name` | string | Yes | -- | Name of the scenario |
| `capability_uuid` | string | No | -- | UUID of capability to add the scenario to |

**Example prompts:**
- "Create a scenario called 'Normal Flight' in the SA layer"
- "Add a new scenario under the Navigation capability"
- "Create an operational scenario for 'Emergency Landing'"

---

### `create_state_machine`

**Description:** Creates a state machine with an initial region on a component.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuid` | string | Yes | -- | UUID of the component to add state machine to |
| `name` | string | Yes | -- | Name of the state machine |

**Example prompts:**
- "Add a state machine called 'Flight Modes' to the Controller component"
- "Create a state machine for the Navigation Module"
- "Define a new state machine on this component"

---

### `create_data_type`

**Description:** Creates a data type (class, enum, string, numeric, boolean) in a layer's data package.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `type` | enum | Yes | -- | Data type kind: `class`, `enum`, `string`, `numeric`, `boolean` |
| `name` | string | Yes | -- | Name of the data type |

**Example prompts:**
- "Create an enum called 'FlightPhase' in the SA layer"
- "Add a numeric data type 'Altitude' to the LA data model"
- "Create a class called 'WaypointData' in the system analysis"

---

### `move_element`

**Description:** Moves a model element from its current container to a different parent container.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuid` | string | Yes | -- | UUID of the element to move |
| `target_parent_uuid` | string | Yes | -- | UUID of the new parent container |

**Example prompts:**
- "Move this function under the Navigation sub-function"
- "Relocate the Sensor component to be a child of the Hardware Package"
- "Move element abc-123 to parent def-456"

---

### `clone_element`

**Description:** Deep-copies a model element (including all children) into the same parent container.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuid` | string | Yes | -- | UUID of the element to clone |
| `new_name` | string | No | -- | Name for the clone (default: original name + " (Copy)") |

**Example prompts:**
- "Clone the Navigation Controller as 'Backup Navigation Controller'"
- "Duplicate this function with all its sub-functions"
- "Make a copy of this component"

---

### `set_property_value`

**Description:** Sets a string property value on a model element. Creates the property if it does not exist; updates it if it does.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuid` | string | Yes | -- | UUID of the element |
| `property_name` | string | Yes | -- | Name of the property value |
| `property_value` | string | Yes | -- | Value to set |

**Example prompts:**
- "Set the 'Safety Level' property of this component to 'DAL-A'"
- "Add a property 'Version' with value '2.0' on this function"
- "Tag this element with status 'Reviewed'"

---

### `create_exchange_item`

**Description:** Creates an exchange item in a layer's data package.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `name` | string | Yes | -- | Name of the exchange item |
| `kind` | enum | No | `FLOW` | Exchange mechanism: `EVENT`, `FLOW`, `OPERATION`, `SHARED_DATA` |

**Example prompts:**
- "Create an exchange item called 'NavigationCommand' in the SA layer"
- "Add a FLOW exchange item 'SensorReading' to the logical architecture"
- "Create an EVENT exchange item for 'AlertTriggered'"

---

### `batch_update_properties`

**Description:** Sets a property on multiple elements in a single transaction.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuids` | string | Yes | -- | Comma-separated UUIDs of elements to update |
| `property_name` | string | Yes | -- | Property name to set (e.g., `name`, `description`, or a custom property) |
| `property_value` | string | Yes | -- | Value to set |

**Example prompts:**
- "Set the description to 'TBD' on all five of these elements"
- "Update the 'Status' property to 'Approved' for these components"
- "Tag all these functions with 'Phase2'"

---

### `create_port`

**Description:** Creates a port on a function or component.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuid` | string | Yes | -- | UUID of the function or component |
| `port_type` | enum | Yes | -- | Port type: `function_input`, `function_output`, `component`, `physical` |
| `name` | string | No | -- | Name for the port (auto-generated if omitted) |

**Example prompts:**
- "Add an input port to this function"
- "Create a physical port on the Sensor hardware component"
- "Add a component port named 'DataOut' to the Controller"

---

### `create_involvement`

**Description:** Links a function to a capability via an involvement relationship.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `capability_uuid` | string | Yes | -- | UUID of the capability |
| `function_uuid` | string | Yes | -- | UUID of the function to involve |

**Example prompts:**
- "Add the 'Compute Route' function to the Navigation capability"
- "Link this function to the 'Emergency Landing' capability"
- "Involve these functions in the capability"

---

### `create_generalization`

**Description:** Creates a generalization (inheritance) link where the sub-type extends the super-type.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `sub_uuid` | string | Yes | -- | UUID of the sub-type (child) |
| `super_uuid` | string | Yes | -- | UUID of the super-type (parent) |

**Example prompts:**
- "Make 'Military Aircraft' a subtype of 'Aircraft'"
- "Create a generalization from SpecializedSensor to Sensor"
- "Add an inheritance link between these two classes"

---

## Diagram Tools (9)

### `update_diagram`

**Description:** Adds or removes a semantic element from an existing Sirius diagram. "Add" triggers a diagram refresh; "remove" hides the element without deleting it from the model.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `diagram_uuid` | string | Yes | -- | UUID of the diagram to modify |
| `element_uuid` | string | Yes | -- | UUID of the semantic element to add/remove |
| `action` | enum | Yes | -- | Action: `add` or `remove` |

**Example prompts:**
- "Add the new Sensor component to the SAB diagram"
- "Remove the deprecated function from this diagram"
- "Show this element in the architecture blank diagram"

---

### `refresh_diagram`

**Description:** Forces a refresh of a Sirius diagram to synchronize it with the underlying model. Use after model changes to ensure the diagram is up to date.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `diagram_uuid` | string | Yes | -- | UUID of the diagram to refresh |

**Example prompts:**
- "Refresh the SA architecture blank diagram"
- "Sync this diagram with the latest model changes"
- "Update the diagram to show newly created elements"

---

### `create_diagram`

**Description:** Creates a new Sirius diagram (PAB, LAB, SAB, OAB, etc.) in a layer.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `diagram_type` | enum | Yes | -- | Type: `OAB`, `OAIB`, `SAB`, `SDFB`, `LAB`, `LDFB`, `PAB`, `PDFB` |
| `target_uuid` | string | No | -- | UUID of element to scope diagram to (default: root architecture) |
| `name` | string | No | -- | Custom name (auto-generated if omitted) |

**Example prompts:**
- "Create a new PAB diagram for the physical architecture"
- "Make a system data flow blank (SDFB) diagram"
- "Create an LAB diagram scoped to the Navigation subsystem"

---

### `export_diagram_image`

**Description:** Exports a diagram as a PNG or SVG image to a file path.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `diagram_uuid` | string | Yes | -- | UUID of the diagram to export |
| `format` | enum | No | `png` | Image format: `png` or `svg` |
| `output_path` | string | No | -- | File path to save (default: workspace temp directory) |

**Example prompts:**
- "Export the SAB diagram as PNG"
- "Save this diagram as an SVG file"
- "Export all architecture diagrams as images"

---

### `show_element_in_diagram`

**Description:** Finds all diagrams that contain a given model element.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuid` | string | Yes | -- | UUID of the model element to find in diagrams |

**Example prompts:**
- "Which diagrams show the Navigation Controller?"
- "Find all diagrams containing this function"
- "Where does this component appear in the diagrams?"

---

### `list_diagram_elements`

**Description:** Lists the semantic elements displayed in a specific diagram.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `diagram_uuid` | string | Yes | -- | UUID of the diagram |

**Example prompts:**
- "What elements are shown in this diagram?"
- "List the contents of the SA architecture blank"
- "Which components appear in this PAB diagram?"

---

### `clone_diagram`

**Description:** Creates a copy of an existing diagram with a new name.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `diagram_uuid` | string | Yes | -- | UUID of the diagram to clone |
| `new_name` | string | No | -- | Name for the cloned diagram (default: original + " (Copy)") |

**Example prompts:**
- "Clone this diagram as 'SAB - Review Version'"
- "Make a copy of the PAB diagram for editing"
- "Duplicate this data flow diagram"

---

### `auto_layout_diagram`

**Description:** Triggers auto-layout (arrange all) on a diagram to improve readability.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `diagram_uuid` | string | Yes | -- | UUID of the diagram to layout |

**Example prompts:**
- "Auto-layout this diagram"
- "Arrange all elements in the SAB diagram"
- "Clean up the layout of this data flow diagram"

---

### `delete_diagram`

**Description:** Deletes a diagram from the model. Requires confirmation.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `diagram_uuid` | string | Yes | -- | UUID of the diagram to delete |
| `confirm` | boolean | No | `false` | Must be `true` to confirm deletion |

**Example prompts:**
- "Delete the old SAB draft diagram"
- "Remove this unused diagram from the model"
- "Delete diagram abc-123"

---

## Analysis Tools (13)

### `check_traceability_coverage`

**Description:** Checks traceability coverage between two ARCADIA layers. For each function and component in the source layer, reports whether it has realization traces to the target layer.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `source_layer` | enum | Yes | -- | Source (higher-level) layer: `oa`, `sa`, `la`, `pa` |
| `target_layer` | enum | Yes | -- | Target (lower-level) layer: `oa`, `sa`, `la`, `pa` |

**Example prompts:**
- "Check traceability coverage from OA to SA"
- "Are all SA functions traced to the logical architecture?"
- "Show me gaps in the SA-to-LA traceability"

---

### `run_capella_validation`

**Description:** Runs comprehensive model validation with detailed issue reporting. Checks for missing descriptions, empty packages, unconnected ports, interfaces with no exchange items, functions with no exchanges, and naming convention violations.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | No | -- | Validate a specific layer (all if omitted) |
| `element_uuid` | string | No | -- | Validate a specific element and its children |

**Example prompts:**
- "Run full validation on the model"
- "Validate just the SA layer"
- "Check this specific component for validation issues"

---

### `detect_cycles`

**Description:** Detects cycles in functional or component exchange graphs using DFS-based cycle detection.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `relationship_type` | enum | No | `functional_exchange` | Type: `functional_exchange`, `component_exchange`, `dependency` |

**Example prompts:**
- "Are there any cycles in the LA functional exchange graph?"
- "Detect circular dependencies in the SA component exchanges"
- "Check for cycles in the physical architecture"

---

### `impact_analysis`

**Description:** Analyzes the impact of modifying or deleting an element. Uses cross-reference analysis to find all references classified by type (allocations, exchanges, traces, diagram appearances).

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuid` | string | Yes | -- | UUID of the element to analyze |

**Example prompts:**
- "What would be affected if I delete this component?"
- "Show the impact analysis for the Process Data function"
- "How many diagrams and exchanges reference this element?"

---

### `find_unused_elements`

**Description:** Finds model elements with no incoming references (potentially unused, candidates for cleanup).

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `element_type` | enum | No | `all` | Type: `function`, `component`, `interface`, `exchange_item`, `capability`, `all` |

**Example prompts:**
- "Find unused functions in the SA layer"
- "Are there any orphaned interfaces in the LA?"
- "Show me unreferenced components that might be dead code"

---

### `model_statistics`

**Description:** Returns element counts and statistics per ARCADIA layer. Counts functions, components, actors, exchanges, allocations, interfaces, capabilities, scenarios, diagrams, and state machines.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | No | -- | Specific layer to report (all layers if omitted) |

**Example prompts:**
- "Give me model statistics for all layers"
- "How many functions and components are in the SA layer?"
- "Show me a summary of the model's size and complexity"

---

### `allocation_completeness`

**Description:** Checks function-to-component allocation completeness for a layer. Reports unallocated functions and components with no allocated functions.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |

**Example prompts:**
- "Check allocation completeness in the logical architecture"
- "Which functions in the SA have no component allocation?"
- "Are there empty components with no functions assigned?"

---

### `get_refinement_status`

**Description:** Checks realization coverage between two ARCADIA layers. Reports which functions and components have realization links and which do not.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `source_layer` | enum | Yes | -- | Source (lower) layer: `oa`, `sa`, `la`, `pa` |
| `target_layer` | enum | Yes | -- | Target (higher) layer: `oa`, `sa`, `la`, `pa` |

**Example prompts:**
- "Check refinement status between SA and LA"
- "How complete is the LA to PA realization?"
- "Which SA functions have no LA counterpart?"

---

### `interface_consistency`

**Description:** Checks interface consistency: verifies that ports match interfaces and exchange items are properly allocated.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |

**Example prompts:**
- "Check interface consistency in the LA layer"
- "Are all component ports properly connected to interfaces?"
- "Verify exchange item allocation for SA interfaces"

---

### `reachability_analysis`

**Description:** Finds a path between two elements via functional exchanges using BFS traversal.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `source_uuid` | string | Yes | -- | UUID of the source element |
| `target_uuid` | string | Yes | -- | UUID of the target element |

**Example prompts:**
- "Is there a data path from the Sensor function to the Display function?"
- "Find the exchange chain between these two components"
- "Can data reach from function A to function B?"

---

### `architecture_complexity`

**Description:** Computes architecture complexity metrics including coupling, cohesion, and per-component analysis.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |

**Example prompts:**
- "Measure the architecture complexity of the LA layer"
- "What is the coupling between components in the system analysis?"
- "Show complexity metrics for the physical architecture"

---

### `identify_singletons`

**Description:** Finds components with only one allocated function (potentially over-decomposed).

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |

**Example prompts:**
- "Find singleton components in the LA layer"
- "Which components have only one function? Should they be merged?"
- "Identify over-decomposed components in the SA"

---

### `generate_safety_report`

**Description:** Generates an FMEA-style safety analysis report from model structure, identifying potential failure modes based on single points of failure and exchange dependencies.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `sa`, `la`, `pa` |

**Example prompts:**
- "Generate a safety report for the physical architecture"
- "Run FMEA analysis on the LA layer"
- "Identify potential failure modes in the system architecture"

---

## Export Tools (8)

### `export_to_csv`

**Description:** Exports model elements to a CSV file with columns: Name, ID, Type, Description, Parent, Layer.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `element_type` | enum | Yes | -- | Type: `functions`, `components`, `exchanges`, `capabilities`, `interfaces`, `all` |
| `output_path` | string | No | -- | File path for CSV output (default: temp directory) |

**Example prompts:**
- "Export all SA functions to CSV"
- "Create a CSV of all components across the logical architecture"
- "Export the complete LA element list to a spreadsheet"

---

### `export_to_json`

**Description:** Exports model elements as a nested JSON structure. Can export an entire layer or a subtree rooted at a specific element.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuid` | string | No | -- | UUID of root element to export (exports subtree) |
| `layer` | enum | No | -- | Architecture layer (used if element_uuid not provided) |
| `max_depth` | integer | No | `3` | Maximum traversal depth (max: 10) |
| `output_path` | string | No | -- | File path to save JSON (returns inline for small models) |

**Example prompts:**
- "Export the SA layer as JSON"
- "Dump this component's subtree to JSON"
- "Export the entire logical architecture structure as JSON to a file"

---

### `generate_icd_report`

**Description:** Generates an Interface Control Document (ICD) report showing each component's ports, provided interfaces, required interfaces, and exchange items.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `component_uuid` | string | No | -- | UUID of a specific component (all components if omitted) |

**Example prompts:**
- "Generate an ICD report for the SA layer"
- "Show the interface control document for the Navigation Controller"
- "Create an ICD for all LA components"

---

### `generate_model_report`

**Description:** Generates a comprehensive model health report combining statistics, allocation completeness, and traceability coverage across all layers.

**Parameters:** None

**Example prompts:**
- "Generate a model health report"
- "Create a comprehensive report for the model review"
- "Show me the overall model quality summary"

---

### `export_traceability_matrix`

**Description:** Exports a traceability matrix between two ARCADIA layers showing which source elements realize which target elements.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `source_layer` | enum | Yes | -- | Source (higher-level) architecture layer |
| `target_layer` | enum | Yes | -- | Target (lower-level) architecture layer |

**Example prompts:**
- "Export the OA-to-SA traceability matrix"
- "Generate a traceability matrix from SA to LA"
- "Show me which SA functions map to which LA functions"

---

### `export_allocation_matrix_csv`

**Description:** Exports the function-to-component allocation matrix as a CSV file.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |
| `output_path` | string | No | -- | File path for CSV output (default: temp directory) |

**Example prompts:**
- "Export the LA allocation matrix as CSV"
- "Save the SA function-component mapping to a spreadsheet"
- "Generate an allocation matrix CSV for the physical architecture"

---

### `export_diagram_catalog`

**Description:** Lists all diagrams in the model with type, target element, and element count.

**Parameters:** None

**Example prompts:**
- "Export a catalog of all diagrams in the model"
- "List every diagram with its type and element count"
- "How many diagrams exist and what types are they?"

---

### `generate_diff_report`

**Description:** Generates a model state summary for change tracking. Provides a snapshot of element counts per resource and modification status.

**Parameters:** None

**Example prompts:**
- "Generate a diff report for the model"
- "What resources have been modified since last save?"
- "Show me a snapshot of the current model state"

---

## Transition Tools (5)

### `transition_oa_to_sa`

**Description:** Transitions elements from Operational Analysis (OA) to System Analysis (SA). Creates corresponding SystemFunctions for each OperationalActivity and SystemComponents for each Entity, with realization traces between the pairs.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuids` | string | No | -- | Comma-separated UUIDs of specific OA elements (all if omitted) |

**Example prompts:**
- "Transition all OA elements to the SA layer"
- "Move these three operational activities to the system analysis"
- "Generate SA functions from the operational analysis"

---

### `transition_sa_to_la`

**Description:** Transitions elements from System Analysis (SA) to Logical Architecture (LA). Creates LogicalFunctions and LogicalComponents with realization traces.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuids` | string | No | -- | Comma-separated UUIDs of specific SA elements (all if omitted) |

**Example prompts:**
- "Transition SA to LA"
- "Create logical architecture elements from the system analysis"
- "Refine these system functions into logical functions"

---

### `transition_la_to_pa`

**Description:** Transitions elements from Logical Architecture (LA) to Physical Architecture (PA). Creates PhysicalFunctions and PhysicalComponents with realization traces.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuids` | string | No | -- | Comma-separated UUIDs of specific LA elements (all if omitted) |

**Example prompts:**
- "Transition from LA to PA"
- "Create physical components from the logical architecture"
- "Generate the physical architecture from LA"

---

### `reconcile_layers`

**Description:** Compares two ARCADIA layers and reports missing or broken realization links. Finds elements in the source layer with no realization in the target layer and vice versa.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `source_layer` | enum | Yes | -- | Source (higher-level) architecture layer |
| `target_layer` | enum | Yes | -- | Target (lower-level) architecture layer |

**Example prompts:**
- "Reconcile the SA and LA layers"
- "Find elements that exist in SA but not in LA"
- "Are there broken realization links between LA and PA?"

---

### `transition_pa_to_epbs`

**Description:** Creates EPBS ConfigurationItems from PA node (hardware) components that are not yet mapped.

**Parameters:** None

**Example prompts:**
- "Create EPBS configuration items from the physical architecture"
- "Transition PA node components to EPBS"
- "Generate the end product breakdown structure"

---

## AI Intelligence Tools (5)

These tools gather structured model context and pass it to the LLM for AI-driven analysis and suggestions. They do not modify the model directly -- instead, they return data that the AI agent uses to formulate recommendations.

### `auto_allocate`

**Description:** Gathers unallocated functions and available components for AI-driven allocation suggestions. Returns structured context so the LLM can propose function-to-component allocation.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `sa`, `la`, `pa` |

**Example prompts:**
- "Suggest how to allocate unassigned functions in the LA"
- "Which functions should go to which components?"
- "Help me with function allocation in the SA layer"

---

### `suggest_interfaces`

**Description:** Gathers exchange patterns to identify component pairs that communicate and their exchange items, enabling AI-driven interface suggestions.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `sa`, `la`, `pa` |

**Example prompts:**
- "Suggest interfaces based on the LA exchange patterns"
- "What interfaces should I define between these communicating components?"
- "Recommend interface definitions for the system analysis"

---

### `review_architecture`

**Description:** Gathers component decomposition, exchange patterns, and metrics so the LLM can analyze the architecture and provide improvement recommendations.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |

**Example prompts:**
- "Review the system architecture and suggest improvements"
- "Analyze the LA for architectural problems"
- "Is this a good decomposition? What would you change?"

---

### `generate_test_scenarios`

**Description:** Gathers capabilities, functional chains, and scenarios to enable AI-driven test scenario generation.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `layer` | enum | Yes | -- | Architecture layer: `oa`, `sa`, `la`, `pa` |

**Example prompts:**
- "Generate test scenarios for the SA capabilities"
- "What test cases should I write based on the functional chains?"
- "Suggest verification scenarios for the system analysis"

---

### `model_q_and_a`

**Description:** Gathers comprehensive model context for free-form Q&A about the system architecture. The LLM uses this context to answer arbitrary questions.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `question` | string | Yes | -- | The question about the model to answer |
| `layer` | enum | No | -- | Specific layer to focus on (all if omitted) |

**Example prompts:**
- "How does the navigation subsystem work?"
- "What is the purpose of the Data Fusion component?"
- "Explain the relationship between the Sensor and the Controller"

---

## Teamcenter Tools (7)

These tools integrate with Siemens Teamcenter PLM for requirements management, parts import, and cross-tool traceability.

### `search_teamcenter`

**Description:** Search for objects in Teamcenter PLM by query string. Optionally filter by object type (e.g., Item, ItemRevision, Requirement). Returns matching objects with their UIDs, names, and types.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `query` | string | Yes | -- | The search query text |
| `object_type` | string | No | -- | Filter by Tc object type (e.g., `Item`, `Requirement`) |
| `max_results` | integer | No | `25` | Maximum results to return |

**Example prompts:**
- "Search Teamcenter for 'navigation sensor'"
- "Find all requirements in Teamcenter containing 'altitude'"
- "Look up the radar module part in PLM"

---

### `tc_get_object`

**Description:** Retrieve all properties of a Teamcenter object by its UID. Returns the full property set including name, type, status, owner, dates, and all custom attributes.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `uid` | string | Yes | -- | The Teamcenter object UID |

**Example prompts:**
- "Get the details of Teamcenter object uid-abc-123"
- "What properties does this Teamcenter item have?"
- "Show me the full metadata for this PLM part"

---

### `tc_get_bom`

**Description:** Expand a Bill of Materials (BOM) tree from Teamcenter starting at a given top-level UID. Returns a hierarchical structure of BOM lines with name, type, quantity, and child components.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `uid` | string | Yes | -- | The UID of the top-level BOM line or Item Revision |
| `depth` | integer | No | `3` | Maximum depth to expand |

**Example prompts:**
- "Show the BOM tree for the aircraft assembly"
- "Expand the bill of materials for this item revision"
- "Get the 2-level BOM for the sensor module"

---

### `tc_list_requirements`

**Description:** List requirements from Teamcenter. Provide either a specification UID to list its child requirements, or a search query to find requirements by text content.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `spec_uid` | string | No | -- | UID of a requirement specification to list children |
| `query` | string | No | -- | Search query to find requirements by text |

**Example prompts:**
- "List all requirements in the navigation spec"
- "Search Teamcenter requirements for 'latency'"
- "Show the child requirements of specification uid-xyz"

---

### `tc_import_requirement`

**Description:** Import a requirement from Teamcenter into the active Capella model. Fetches the requirement data, maps it to a Capella requirement element, and creates it in the specified architecture layer. The Teamcenter UID is stored for traceability.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `tc_uid` | string | Yes | -- | The Teamcenter requirement UID to import |
| `target_layer` | string | Yes | -- | The Capella architecture layer: `oa`, `sa`, `la`, `pa` |
| `link_to_uuid` | string | No | -- | UUID of a Capella element to link the requirement to |

**Example prompts:**
- "Import the navigation accuracy requirement from Teamcenter into SA"
- "Bring this Teamcenter requirement into the Capella model"
- "Import requirement uid-abc and link it to the Navigation function"

---

### `tc_import_part`

**Description:** Import a part (Item) from Teamcenter as a Capella PhysicalComponent. Fetches the part data, maps it to a component, and creates it in the specified layer. The Teamcenter UID and part number are stored for traceability.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `tc_uid` | string | Yes | -- | The Teamcenter part/Item UID to import |
| `target_layer` | string | No | `pa` | The Capella architecture layer |

**Example prompts:**
- "Import the radar module from Teamcenter into the PA"
- "Bring the sensor hardware part into Capella"
- "Import this PLM item as a physical component"

---

### `tc_create_trace_link`

**Description:** Create a traceability link between a Teamcenter object and a Capella model element. Stores the relationship as a custom property and optionally registers the link in Teamcenter.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `tc_uid` | string | Yes | -- | The Teamcenter object UID |
| `capella_uuid` | string | Yes | -- | The Capella element UUID |
| `link_type` | string | No | `trace` | Link type: `trace`, `satisfy`, `derive`, `refine`, `implement` |

**Example prompts:**
- "Create a traceability link from the Teamcenter requirement to this SA function"
- "Link the PLM part to the Capella physical component"
- "Create a 'satisfy' link between this requirement and the system function"

---

## Simulation Tools (6)

These tools orchestrate MATLAB/Simulink simulations from within the Capella modeling environment, enabling parameter extraction, simulation execution, and result propagation.

### `list_simulation_engines`

**Description:** List all registered simulation engines with their availability status. Returns engine IDs, display names, and whether they are currently available (installed, licensed, and accessible).

**Parameters:** None

**Example prompts:**
- "What simulation engines are available?"
- "List the registered simulation tools"
- "Is MATLAB/Simulink available for simulation?"

---

### `extract_simulation_params`

**Description:** Preview what simulation parameters would be extracted from the given Capella model elements. Returns available numeric/string property values that can be used as simulation inputs.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `element_uuids` | array of strings | Yes | -- | UUIDs of Capella elements to extract from |

**Example prompts:**
- "What parameters can I extract from these components for simulation?"
- "Preview the simulation input parameters for the Flight Controller"
- "Show extractable values from these model elements"

---

### `run_simulation`

**Description:** Runs a simulation synchronously using the specified engine and model file. Extracts parameters from Capella elements and optionally propagates results back to the model.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `engine_id` | string | Yes | -- | The simulation engine to use |
| `model_path` | string | Yes | -- | Path to the simulation model file |
| `element_uuids` | array of strings | Yes | -- | Capella element UUIDs for parameter extraction |
| `auto_propagate` | boolean | No | `true` | Whether to auto-propagate results back to the model |

**Example prompts:**
- "Run the thermal simulation with parameters from these components"
- "Execute the Simulink model using the Flight Controller parameters"
- "Run a simulation and propagate results back to the Capella model"

---

### `propagate_simulation_results`

**Description:** Manually propagate results from a completed simulation run back to the Capella model. Use when `auto_propagate` was set to false or to re-propagate results.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `simulation_id` | string | Yes | -- | The simulation run ID to propagate |

**Example prompts:**
- "Propagate the results from simulation run sim-123 back to the model"
- "Apply the latest simulation results to the Capella elements"
- "Write simulation outputs back to the model"

---

### `run_what_if`

**Description:** Runs a parameter sweep (what-if analysis) across multiple parameter values. Executes simulations for each combination and returns comparative results.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `engine_id` | string | Yes | -- | The simulation engine to use |
| `model_path` | string | Yes | -- | Path to the simulation model |
| `element_uuids` | array of strings | Yes | -- | Capella element UUIDs for parameter extraction |
| `parameter_ranges` | object | Yes | -- | Parameter name to array of values (the sweep ranges) |

**Example prompts:**
- "Run a what-if analysis varying the sensor refresh rate from 10 to 100 Hz"
- "Sweep the antenna gain parameter across these values and compare"
- "What happens if I change the processing delay from 1ms to 10ms?"

---

### `get_simulation_status`

**Description:** Check the status of a running or completed simulation job. Returns the current status, progress percentage, and any available results.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `simulation_id` | string | Yes | -- | The simulation run ID to check |

**Example prompts:**
- "What is the status of simulation run sim-456?"
- "Is the thermal simulation still running?"
- "Check if the last simulation completed successfully"

---

## Appendix: ARCADIA Layer Reference

| Layer Code | Full Name | Description |
|------------|-----------|-------------|
| `oa` | Operational Analysis | Captures operational needs and activities performed by users and entities |
| `sa` | System Analysis | Defines what the system must do (system functions and interfaces) |
| `la` | Logical Architecture | Decomposes the system into logical components and their interactions |
| `pa` | Physical Architecture | Maps logical elements to physical hardware and software components |
| `epbs` | End Product Breakdown Structure | Defines the deliverable configuration items |

## Appendix: Diagram Type Reference

| Type | Full Name | Layer |
|------|-----------|-------|
| `OAB` | Operational Architecture Blank | OA |
| `OAIB` | Operational Activity Interaction Blank | OA |
| `SAB` | System Architecture Blank | SA |
| `SDFB` | System Data Flow Blank | SA |
| `LAB` | Logical Architecture Blank | LA |
| `LDFB` | Logical Data Flow Blank | LA |
| `PAB` | Physical Architecture Blank | PA |
| `PDFB` | Physical Data Flow Blank | PA |
