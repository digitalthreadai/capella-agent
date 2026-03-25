package com.capellaagent.teamcenter.tools;

import com.capellaagent.core.tools.IToolDescriptor;
import com.capellaagent.core.tools.IToolExecutor;
import com.capellaagent.core.tools.ToolExecutionException;
import com.capellaagent.teamcenter.api.TcObjectService;
import com.capellaagent.teamcenter.client.TcException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tool: "tc_create_trace_link" -- Creates a traceability link between a Teamcenter
 * item and a Capella model element.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code tc_uid} (string, required) - The Teamcenter object UID</li>
 *   <li>{@code capella_uuid} (string, required) - The Capella element UUID</li>
 *   <li>{@code link_type} (string, optional, default "trace") - The type of traceability link</li>
 * </ul>
 */
public class TcLinkTool implements IToolDescriptor, IToolExecutor {

    private static final String TOOL_NAME = "tc_create_trace_link";
    private static final String DEFAULT_LINK_TYPE = "trace";

    private final TcObjectService objectService;

    /**
     * Constructs a new TcLinkTool.
     *
     * @param objectService the Teamcenter object service (used to validate the Tc UID)
     */
    public TcLinkTool(TcObjectService objectService) {
        this.objectService = objectService;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Create a traceability link between a Teamcenter object and a Capella model "
                + "element. Stores the relationship as a custom property on the Capella element "
                + "and optionally registers the link in Teamcenter. Supports link types: "
                + "'trace', 'satisfy', 'derive', 'refine', 'implement'.";
    }

    @Override
    public String getCategory() {
        return "teamcenter";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject tcUidProp = new JsonObject();
        tcUidProp.addProperty("type", "string");
        tcUidProp.addProperty("description", "The Teamcenter object UID");
        properties.add("tc_uid", tcUidProp);

        JsonObject capellaUuidProp = new JsonObject();
        capellaUuidProp.addProperty("type", "string");
        capellaUuidProp.addProperty("description", "The Capella element UUID");
        properties.add("capella_uuid", capellaUuidProp);

        JsonObject linkTypeProp = new JsonObject();
        linkTypeProp.addProperty("type", "string");
        linkTypeProp.addProperty("description",
                "Type of traceability link (default: 'trace')");
        linkTypeProp.addProperty("default", DEFAULT_LINK_TYPE);
        JsonArray linkTypeEnum = new JsonArray();
        linkTypeEnum.add("trace");
        linkTypeEnum.add("satisfy");
        linkTypeEnum.add("derive");
        linkTypeEnum.add("refine");
        linkTypeEnum.add("implement");
        linkTypeProp.add("enum", linkTypeEnum);
        properties.add("link_type", linkTypeProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("tc_uid");
        required.add("capella_uuid");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws ToolExecutionException {
        String tcUid = getRequiredString(arguments, "tc_uid");
        String capellaUuid = getRequiredString(arguments, "capella_uuid");
        String linkType = arguments.has("link_type") && !arguments.get("link_type").isJsonNull()
                ? arguments.get("link_type").getAsString()
                : DEFAULT_LINK_TYPE;

        // Validate link type
        if (!isValidLinkType(linkType)) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Invalid link_type: '" + linkType
                            + "'. Must be one of: trace, satisfy, derive, refine, implement");
        }

        try {
            // Validate that the Teamcenter object exists
            JsonObject tcObject = objectService.getProperties(tcUid);
            String tcObjectName = tcObject.has("object_name")
                    ? tcObject.get("object_name").getAsString()
                    : tcUid;

            // PLACEHOLDER: Create the trace link in the Capella model.
            // In a real implementation, this would:
            //
            // 1. Find the Capella element by UUID
            //    EObject capellaElement = findByUuid(session, capellaUuid);
            //
            // 2. Create a trace link (using Capella's AbstractTrace or
            //    Requirements VP trace link mechanism)
            //    TransactionalEditingDomain domain = session.getTransactionalEditingDomain();
            //    domain.getCommandStack().execute(new RecordingCommand(domain) {
            //        @Override
            //        protected void doExecute() {
            //            // Create appropriate trace link based on linkType
            //            PropertyValueHelpers.setStringProperty(capellaElement,
            //                "teamcenter.link." + linkType, tcUid);
            //        }
            //    });

            JsonObject result = new JsonObject();
            result.addProperty("tcUid", tcUid);
            result.addProperty("tcObjectName", tcObjectName);
            result.addProperty("capellaUuid", capellaUuid);
            result.addProperty("linkType", linkType);
            result.addProperty("status", "PLACEHOLDER_CREATED");
            result.addProperty("message",
                    "PLACEHOLDER: Trace link (" + linkType + ") registered between Tc object '"
                            + tcObjectName + "' and Capella element " + capellaUuid
                            + ". Connect Capella Trace API to enable actual link creation.");

            return result;

        } catch (TcException e) {
            throw new ToolExecutionException(ToolExecutionException.ERR_EXECUTION,
                    "Failed to create trace link: " + e.getMessage(), e);
        }
    }

    private boolean isValidLinkType(String linkType) {
        return switch (linkType.toLowerCase()) {
            case "trace", "satisfy", "derive", "refine", "implement" -> true;
            default -> false;
        };
    }

    private String getRequiredString(JsonObject args, String key) throws ToolExecutionException {
        if (!args.has(key) || args.get(key).isJsonNull()) {
            throw new ToolExecutionException(ToolExecutionException.ERR_INVALID_ARGS,
                    "Required parameter '" + key + "' is missing");
        }
        return args.get(key).getAsString();
    }
}
