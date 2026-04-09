package com.capellaagent.requirements.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.business.api.session.Session;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.capellaagent.core.tools.AbstractCapellaTool;
import com.capellaagent.core.tools.ToolCategory;
import com.capellaagent.core.tools.ToolParameter;
import com.capellaagent.core.tools.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Imports requirements from a ReqIF 1.0.x file.
 * <p>
 * Supports {@code dry_run=true} (default) for preview without writing, and
 * {@code dry_run=false} to commit requirements to the model.
 * <p>
 * <b>XXE prevention:</b> DOCTYPE declarations are explicitly disallowed.
 * <b>Namespace handling:</b> tag names are matched by local name only, handling
 * both {@code http://www.omg.org/ReqIF/1.0} and {@code http://www.reqif.de/stdReqif}.
 * <p>
 * v1 subset: only SPEC-OBJECTS are imported. SPEC-RELATIONS and SPEC-HIERARCHIES
 * are ignored.
 */
public class ImportReqifTool extends AbstractCapellaTool {

    private static final Logger LOG = Logger.getLogger(ImportReqifTool.class.getName());

    private static final String TOOL_NAME = "import_reqif";
    private static final String DESCRIPTION =
            "Imports requirements from a ReqIF (.reqif) file. "
            + "Use dry_run=true (default) to preview the import without making changes. "
            + "Set dry_run=false to commit requirements to the model. "
            + "Requires the Requirements Viewpoint to be enabled for dry_run=false.";

    public ImportReqifTool() {
        super(TOOL_NAME, DESCRIPTION, ToolCategory.REQUIREMENTS);
    }

    @Override
    protected List<ToolParameter> defineParameters() {
        List<ToolParameter> params = new ArrayList<>();
        params.add(ToolParameter.requiredString("path", "Absolute path to the .reqif file"));
        params.add(ToolParameter.optionalBoolean("dry_run",
                "If true (default), preview only. If false, import into the model."));
        return params;
    }

    // SECURITY (C3): hard upper bound on ReqIF file size — prevents POI /
    // DOM parser DoS via a maliciously huge XML file. 100 MB is far above
    // any real-world ReqIF export.
    private static final long MAX_REQIF_BYTES = 100L * 1024 * 1024;

    // SECURITY (I4/N4): .reqifz support is removed until a ZipSlip-safe
    // extractor is implemented. Any future extractor must verify every
    // entry's normalised path stays inside the target directory.
    private static final java.util.Set<String> ALLOWED_EXTENSIONS =
            java.util.Set.of(".reqif", ".xml");

    @Override
    protected ToolResult executeInternal(Map<String, Object> parameters) {
        String rawPath = getRequiredString(parameters, "path");
        boolean dryRun = getOptionalBoolean(parameters, "dry_run", true);

        // SECURITY (C1/C2/C4/I6): centralized path validation — canonicalizes,
        // enforces workspace containment, rejects symlinks (NOFOLLOW_LINKS),
        // and checks the extension allow-list. Violations throw a
        // SecurityException with a redacted message (never echoes the raw
        // path back to the LLM or error log).
        java.nio.file.Path validatedPath;
        try {
            validatedPath = com.capellaagent.core.security.PathValidator
                    .validateInputPath(rawPath, ALLOWED_EXTENSIONS);
        } catch (SecurityException se) {
            return ToolResult.error("Rejected by path validator: " + se.getMessage());
        }
        java.io.File file = validatedPath.toFile();

        // SECURITY (C3): size cap before opening to guarantee the DOM
        // parser cannot be fed an arbitrarily large document.
        try {
            long size = java.nio.file.Files.size(validatedPath);
            if (size > MAX_REQIF_BYTES) {
                return ToolResult.error(
                    "ReqIF file exceeds maximum size of "
                    + (MAX_REQIF_BYTES / (1024 * 1024)) + " MB");
            }
        } catch (java.io.IOException ioe) {
            return ToolResult.error("Cannot read file attributes");
        }

        try {
            List<RequirementRecord> records = parseReqif(file);

            JsonObject result = new JsonObject();
            result.addProperty("dry_run", dryRun);
            result.addProperty("file", file.getName());
            result.addProperty("requirements_found", records.size());

            JsonArray preview = new JsonArray();
            for (RequirementRecord rec : records) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", rec.id());
                obj.addProperty("text_snippet", truncate(rec.text(), 80));
                preview.add(obj);
            }
            result.add("requirements", preview);

            if (dryRun) {
                result.addProperty("message",
                        "Dry run complete. " + records.size() + " requirements found. "
                        + "Call with dry_run=false to import.");
                return ToolResult.success(result);
            }

            // Check for Requirements Viewpoint
            if (!isRequirementsViewpointAvailable()) {
                return ToolResult.error(
                        "Requirements Viewpoint is not installed. "
                        + "Enable it via Window -> Preferences -> Capella -> Viewpoints, "
                        + "then restart and retry with dry_run=false.");
            }

            Session session = getActiveSession();

            // Write requirements to model
            int created = 0;
            for (RequirementRecord rec : records) {
                try {
                    final RequirementRecord finalRec = rec;
                    executeInTransaction(session, "Create requirement " + rec.id(), () -> {
                        try {
                            createRequirementInSession(session, finalRec.id(), finalRec.text());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    created++;
                } catch (Exception e) {
                    LOG.warning("Failed to create requirement " + rec.id() + ": " + e.getMessage());
                }
            }

            result.addProperty("created", created);
            result.addProperty("message", created + " requirements imported successfully.");
            return ToolResult.success(result);

        } catch (Exception e) {
            return ToolResult.error("Failed to parse ReqIF file: " + e.getMessage());
        }
    }

    /**
     * Parses SPEC-OBJECTS from a ReqIF file.
     * Handles both namespace-qualified and plain element names.
     * XXE is prevented by disabling DOCTYPE processing.
     */
    private List<RequirementRecord> parseReqif(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false); // work with local names only

        // SECURITY (I3/N3): enable FEATURE_SECURE_PROCESSING which turns on
        // JAXP limits (entity expansion, element depth, attribute count)
        // as a single switch. Combined with the individual DOCTYPE /
        // external-entity disables below, this closes XXE, billion laughs,
        // and quadratic blowup in one pass.
        try {
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (javax.xml.parsers.ParserConfigurationException ignored) {
            // Older parser without FSP — fall back to manual features below.
        }

        // XXE prevention
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        try {
            factory.setAttribute(
                    "http://javax.xml.XMLConstants/property/accessExternalDTD", "");
            factory.setAttribute(
                    "http://javax.xml.XMLConstants/property/accessExternalSchema", "");
        } catch (IllegalArgumentException ignored) {
            // Some parsers may not support these attributes
        }

        DocumentBuilder builder = factory.newDocumentBuilder();
        // SECURITY (I6/C4): NOFOLLOW_LINKS at open time — defence in depth
        // over the earlier PathValidator check, closing any TOCTOU window
        // between validation and open.
        Document doc;
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(
                file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            doc = builder.parse(in);
        }
        doc.getDocumentElement().normalize();

        List<RequirementRecord> records = new ArrayList<>();

        // Find SPEC-OBJECT elements (namespace-agnostic local name search)
        NodeList specObjects = findByLocalName(doc, "SPEC-OBJECT");
        for (int i = 0; i < specObjects.getLength(); i++) {
            Element specObj = (Element) specObjects.item(i);
            String id = extractId(specObj);
            String text = extractText(specObj);
            records.add(new RequirementRecord(id, text));
        }

        return records;
    }

    /**
     * Finds elements by local name, ignoring namespace prefix.
     */
    private NodeList findByLocalName(Document doc, String localName) {
        NodeList nl = doc.getElementsByTagName(localName);
        if (nl.getLength() > 0) return nl;
        nl = doc.getElementsByTagName("reqif:" + localName);
        if (nl.getLength() > 0) return nl;
        return doc.getElementsByTagNameNS("*", localName);
    }

    /** Extracts the ReqIF identifier from a SPEC-OBJECT element. */
    private String extractId(Element specObj) {
        String id = specObj.getAttribute("IDENTIFIER");
        if (id != null && !id.isEmpty()) return id;
        id = specObj.getAttribute("ID");
        if (id != null && !id.isEmpty()) return id;
        return "REQ-" + System.nanoTime();
    }

    /** Extracts text content from a SPEC-OBJECT. Handles STRING, XHTML, and ENUMERATION. */
    private String extractText(Element specObj) {
        // Look for ATTRIBUTE-VALUE-STRING
        NodeList stringAttrs = specObj.getElementsByTagName("ATTRIBUTE-VALUE-STRING");
        if (stringAttrs.getLength() == 0)
            stringAttrs = specObj.getElementsByTagName("reqif:ATTRIBUTE-VALUE-STRING");
        for (int i = 0; i < stringAttrs.getLength(); i++) {
            Element attr = (Element) stringAttrs.item(i);
            String val = attr.getAttribute("THE-VALUE");
            if (val != null && !val.isEmpty()) return val;
        }

        // Look for ATTRIBUTE-VALUE-XHTML (strip XML tags)
        NodeList xhtmlAttrs = specObj.getElementsByTagName("ATTRIBUTE-VALUE-XHTML");
        if (xhtmlAttrs.getLength() == 0)
            xhtmlAttrs = specObj.getElementsByTagName("reqif:ATTRIBUTE-VALUE-XHTML");
        for (int i = 0; i < xhtmlAttrs.getLength(); i++) {
            Element attr = (Element) xhtmlAttrs.item(i);
            String text = attr.getTextContent();
            if (text != null && !text.isBlank()) return text.strip();
        }

        // Fall back to element text content
        String text = specObj.getTextContent();
        return text != null ? text.strip() : "";
    }

    /** Checks whether the Requirements Viewpoint bundle is loaded. */
    private boolean isRequirementsViewpointAvailable() {
        try {
            Class.forName("org.polarsys.kitalpha.vp.requirements.Requirements.Requirement");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Creates a requirement element in the Capella model via EMF transaction. */
    private void createRequirementInSession(Session session, String id, String text) throws Exception {
        Class<?> reqFactory = Class.forName(
                "org.polarsys.kitalpha.vp.requirements.Requirements.RequirementsFactory");
        Object factory = reqFactory.getField("eINSTANCE").get(null);
        Object req = null;
        for (java.lang.reflect.Method m : reqFactory.getMethods()) {
            if (m.getName().equals("createRequirement") && m.getParameterCount() == 0) {
                req = m.invoke(factory);
                break;
            }
        }
        if (req == null) {
            throw new RuntimeException("Could not invoke createRequirement() on factory.");
        }

        // Set identifier
        setFieldReflective(req, "setReqIFIdentifier", "setId", id);

        // Set text
        setFieldReflective(req, "setReqIFText", "setDescription", text);

        // Set name for display in Capella explorer
        try {
            req.getClass().getMethod("setName", String.class).invoke(req,
                    id.length() > 50 ? id.substring(0, 50) : id);
        } catch (NoSuchMethodException ex) { /* ignore */ }

        // Add to the active project's requirement package
        addToRequirementPackage(session, req);
    }

    private void setFieldReflective(Object obj, String method1, String method2, String value) {
        try {
            obj.getClass().getMethod(method1, String.class).invoke(obj, value);
        } catch (Exception e1) {
            if (method2 != null) {
                try {
                    obj.getClass().getMethod(method2, String.class).invoke(obj, value);
                } catch (Exception e2) { /* ignore */ }
            }
        }
    }

    /** Adds a requirement EObject to the model's root requirement package. */
    private void addToRequirementPackage(Session session, Object req) throws Exception {
        for (Resource resource : session.getSemanticResources()) {
            org.eclipse.emf.common.util.TreeIterator<EObject> it = resource.getAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                String className = obj.eClass().getName();
                if (className.contains("RequirementsPkg") || className.contains("RequirementsModule")) {
                    for (EReference ref : obj.eClass().getEAllContainments()) {
                        if (ref.getEReferenceType().isInstance(req)) {
                            @SuppressWarnings("unchecked")
                            java.util.List<Object> list =
                                    (java.util.List<Object>) obj.eGet(ref);
                            list.add(req);
                            return;
                        }
                    }
                }
            }
        }
        throw new IllegalStateException(
                "No RequirementsPkg found in model. "
                + "Create a Requirements package first via the Capella Model Explorer.");
    }

    /** Simple record holding a parsed requirement. */
    private record RequirementRecord(String id, String text) {}
}
