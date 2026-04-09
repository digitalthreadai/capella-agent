package com.capellaagent.requirements.tools;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.business.api.session.Session;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Links a requirement to a model element, with dry-run preview and confidence scoring.
 * <p>
 * <b>Cascade approach:</b>
 * <ol>
 *   <li>If {@code element_uuid} is provided, resolve by UUID; fail explicitly if not found.</li>
 *   <li>Otherwise, compute bigram Dice coefficient between requirement text and element names.
 *       Return top 3 candidates for the user/LLM to select from.</li>
 * </ol>
 * <p>
 * <b>Guards:</b>
 * <ul>
 *   <li>Requirement text under 10 chars returns "too short for similarity matching"</li>
 * </ul>
 */
public class LinkRequirementsToElementsTool extends AbstractCapellaTool {

    private static final Logger LOG = Logger.getLogger(LinkRequirementsToElementsTool.class.getName());

    private static final String TOOL_NAME = "link_requirements_to_elements";
    private static final String DESCRIPTION =
            "Links a requirement to a Capella model element. "
            + "If element_uuid is omitted, proposes the top 3 candidate elements by name similarity. "
            + "Use dry_run=true (default) to preview. Set dry_run=false to create the trace link. "
            + "Confidence scores below 0.7 are flagged as uncertain.";

    public LinkRequirementsToElementsTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.REQUIREMENTS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("requirement_id",
                "The ReqIF identifier of the requirement (e.g., REQ-001)"));
        params.add(ToolParameter.optionalString("element_uuid",
                "UUID of the target model element. If omitted, top 3 candidates are proposed."));
        params.add(ToolParameter.optionalBoolean("dry_run",
                "If true (default), preview only. If false, create the trace link."));
        params.add(ToolParameter.optionalNumber("confidence_threshold",
                "Minimum confidence (0.0-1.0) for proposing a link. Default 0.7."));
        return params;
    }

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String requirementId = getRequiredString(parameters, "requirement_id");
        String elementUuid = getOptionalString(parameters, "element_uuid", null);
        boolean dryRun = getOptionalBoolean(parameters, "dry_run", true);
        double threshold = getOptionalDouble(parameters, "confidence_threshold", 0.7);

        try {
            // Resolve the requirement
            EObject requirement = findRequirementById(requirementId);
            if (requirement == null) {
                return ToolResult.error("Requirement not found with ID: " + requirementId);
            }

            String reqText = getRequirementText(requirement);

            // Guard: text too short for fuzzy matching
            if (elementUuid == null && (reqText == null || reqText.length() < 10)) {
                return ToolResult.error(
                        "Requirement text is too short (< 10 chars) for similarity matching. "
                        + "Provide element_uuid explicitly.");
            }

            if (elementUuid != null) {
                // Direct UUID path
                EObject target = resolveElementByUuid(elementUuid);
                if (target == null) {
                    return ToolResult.error("Element not found with UUID: " + elementUuid);
                }

                JsonObject result = new JsonObject();
                result.addProperty("requirement_id", requirementId);
                result.addProperty("requirement_text_snippet", truncate(reqText, 60));
                result.addProperty("target_name", getElementName(target));
                result.addProperty("target_uuid", elementUuid);
                result.addProperty("dry_run", dryRun);

                if (dryRun) {
                    result.addProperty("message",
                            "Will create trace link from " + requirementId
                            + " -> " + getElementName(target)
                            + ". Call with dry_run=false to commit.");
                    return ToolResult.success(result);
                }

                Session session = getActiveSession();
                final EObject finalReq = requirement;
                final EObject finalTarget = target;
                executeInTransaction(session, "Create trace link for " + requirementId, () -> {
                    try {
                        createTraceLink(finalReq, finalTarget);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                result.addProperty("message", "Trace link created successfully.");
                return ToolResult.success(result);

            } else {
                // Fuzzy matching path
                List<Candidate> candidates = findTopCandidates(reqText, 3);

                JsonObject result = new JsonObject();
                result.addProperty("requirement_id", requirementId);
                result.addProperty("requirement_text_snippet", truncate(reqText, 60));
                result.addProperty("dry_run", dryRun);

                JsonArray proposalArray = new JsonArray();
                for (Candidate c : candidates) {
                    JsonObject cObj = new JsonObject();
                    cObj.addProperty("element_name", c.name());
                    cObj.addProperty("element_uuid", c.uuid());
                    cObj.addProperty("element_type", c.type());
                    cObj.addProperty("confidence", Math.round(c.score() * 100.0) / 100.0);
                    cObj.addProperty("uncertain", c.score() < threshold);
                    proposalArray.add(cObj);
                }
                result.add("candidates", proposalArray);

                if (candidates.isEmpty()) {
                    result.addProperty("message",
                            "No suitable candidates found for requirement: " + requirementId);
                } else {
                    result.addProperty("message",
                            "Top candidates shown. Select an element_uuid and call again "
                            + "(with dry_run=false) to create the link.");
                }
                return ToolResult.success(result);
            }

        } catch (Exception e) {
            return ToolResult.error("Failed to link requirement: " + e.getMessage());
        }
    }

    /** Finds a requirement EObject by its ReqIF identifier. Returns null if not found or no session. */
    private EObject findRequirementById(String requirementId) {
        try {
            Session session = getActiveSession();
            if (session == null) return null;
            for (Resource resource : session.getSemanticResources()) {
                org.eclipse.emf.common.util.TreeIterator<EObject> it = resource.getAllContents();
                while (it.hasNext()) {
                    EObject obj = it.next();
                    String cn = obj.eClass().getName();
                    if (!cn.contains("Requirement") || cn.contains("Pkg")) continue;
                    String id = getReqId(obj);
                    if (requirementId.equals(id)) return obj;
                }
            }
        } catch (Exception e) {
            LOG.warning("Error finding requirement: " + e.getMessage());
        }
        return null;
    }

    private String getReqId(EObject obj) {
        EStructuralFeature f = obj.eClass().getEStructuralFeature("ReqIFIdentifier");
        if (f != null) {
            Object v = obj.eGet(f);
            if (v != null && !v.toString().isEmpty()) return v.toString();
        }
        f = obj.eClass().getEStructuralFeature("id");
        if (f != null) {
            Object v = obj.eGet(f);
            if (v != null && !v.toString().isEmpty()) return v.toString();
        }
        return getElementId(obj);
    }

    private String getRequirementText(EObject req) {
        EStructuralFeature f = req.eClass().getEStructuralFeature("ReqIFText");
        if (f != null) {
            Object v = req.eGet(f);
            if (v != null && !v.toString().isEmpty()) return v.toString();
        }
        return getElementDescription(req);
    }

    /** Finds top N candidates from the model using bigram Dice similarity. */
    private List<Candidate> findTopCandidates(String reqText, int n) {
        List<Candidate> all = new ArrayList<>();
        Set<String> reqBigrams = bigrams(reqText.toLowerCase());

        try {
            Session session = getActiveSession();
            if (session == null) return all;
            for (Resource resource : session.getSemanticResources()) {
                org.eclipse.emf.common.util.TreeIterator<EObject> it = resource.getAllContents();
                while (it.hasNext()) {
                    EObject obj = it.next();
                    String name = getElementName(obj);
                    if (name == null || name.isBlank()) continue;
                    // Skip requirement elements themselves
                    if (obj.eClass().getName().contains("Requirement")) continue;

                    Set<String> elBigrams = bigrams(name.toLowerCase());
                    double score = diceCoefficient(reqBigrams, elBigrams);
                    if (score > 0.0) {
                        all.add(new Candidate(name, getElementId(obj),
                                obj.eClass().getName(), score));
                    }
                }
            }
        } catch (Exception e) {
            LOG.warning("Error scanning model for candidates: " + e.getMessage());
        }

        // Sort by score descending, return top N
        all.sort((a, b) -> Double.compare(b.score(), a.score()));
        return all.subList(0, Math.min(n, all.size()));
    }

    private Set<String> bigrams(String s) {
        Set<String> bg = new HashSet<>();
        for (int i = 0; i < s.length() - 1; i++) {
            bg.add(s.substring(i, i + 2));
        }
        return bg;
    }

    private double diceCoefficient(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return (2.0 * intersection.size()) / (a.size() + b.size());
    }

    /** Creates a CapellaOutgoingRelation from requirement to element via reflection. */
    private void createTraceLink(EObject requirement, EObject target) throws Exception {
        Class<?> relFactory;
        try {
            relFactory = Class.forName(
                    "org.polarsys.kitalpha.vp.requirements.Requirements.RequirementsFactory");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "Requirements Viewpoint not installed. Cannot create trace link.");
        }

        Object factory = relFactory.getField("eINSTANCE").get(null);

        // Find createCapellaOutgoingRelation or createOutgoingRelation
        Object relation = null;
        for (java.lang.reflect.Method m : factory.getClass().getMethods()) {
            if (m.getName().contains("OutgoingRelation") && m.getParameterCount() == 0) {
                relation = m.invoke(factory);
                break;
            }
        }
        if (relation == null) {
            throw new RuntimeException("Could not create OutgoingRelation via factory.");
        }

        // Set the target element
        for (java.lang.reflect.Method m : relation.getClass().getMethods()) {
            if ((m.getName().equals("setRelatedElement") || m.getName().equals("setTarget"))
                    && m.getParameterCount() == 1) {
                m.invoke(relation, target);
                break;
            }
        }

        // Add relation to the requirement's owned relations
        for (EReference ref : requirement.eClass().getEAllContainments()) {
            if (ref.getEReferenceType().isInstance(relation)) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> list =
                        (java.util.List<Object>) requirement.eGet(ref);
                list.add(relation);
                return;
            }
        }
        throw new RuntimeException(
                "Could not find containment for OutgoingRelation in requirement.");
    }

    /**
     * Gets an optional double parameter with a default value.
     * AbstractCapellaTool does not provide getOptionalDouble, so we implement it here.
     */
    private double getOptionalDouble(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private record Candidate(String name, String uuid, String type, double score) {}
}
