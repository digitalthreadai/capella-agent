package com.capellaagent.modelchat.ui.adapters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;

// PLACEHOLDER imports for Capella navigation
// import org.polarsys.capella.core.data.capellacore.NamedElement;
// import org.polarsys.capella.core.platform.sirius.ui.navigator.CapellaNavigatorPlugin;
// import org.polarsys.capella.core.ui.semantic.browser.sirius.helpers.SiriusSelectionHelper;
// import org.eclipse.ui.IWorkbenchPage;
// import org.eclipse.ui.PlatformUI;

/**
 * Detects Capella UUID patterns in the chat conversation text and makes them clickable.
 * <p>
 * When a UUID is detected in the chat text, it is styled as a hyperlink (underlined, blue).
 * Clicking on a UUID navigates to that element in Capella's Project Explorer or
 * Semantic Browser, providing seamless integration between the chat and the model.
 * <p>
 * Capella UUIDs follow the pattern of standard UUIDs (8-4-4-4-12 hex characters)
 * or EMF-style fragment identifiers.
 *
 * <h3>UUID Pattern</h3>
 * Matches standard UUID format: {@code xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx}
 * where x is a hexadecimal digit.
 */
public class ElementLinkAdapter {

    /**
     * Regex pattern matching standard UUID format (8-4-4-4-12 hex characters).
     * This covers both standard UUIDs and Capella element identifiers.
     */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    private final StyledText styledText;
    private Color linkColor;
    private Cursor handCursor;
    private Cursor defaultCursor;

    /**
     * Constructs a new ElementLinkAdapter and attaches it to the given StyledText widget.
     *
     * @param styledText the StyledText widget to attach link detection to
     */
    public ElementLinkAdapter(StyledText styledText) {
        this.styledText = styledText;

        Display display = styledText.getDisplay();
        this.linkColor = new Color(display, 0, 102, 204);
        this.handCursor = new Cursor(display, SWT.CURSOR_HAND);
        this.defaultCursor = styledText.getCursor();

        attachMouseListener();
        attachMouseMoveListener();
    }

    /**
     * Scans the given text for UUID patterns and applies hyperlink styling.
     * <p>
     * This method should be called after appending new text to the StyledText widget,
     * passing the offset where the text begins and the text content itself.
     *
     * @param textOffset the character offset in the StyledText where this text begins
     * @param text       the text content to scan for UUIDs
     */
    public void applyLinkStyles(int textOffset, String text) {
        Matcher matcher = UUID_PATTERN.matcher(text);

        while (matcher.find()) {
            int start = textOffset + matcher.start();
            int length = matcher.end() - matcher.start();

            StyleRange linkStyle = new StyleRange();
            linkStyle.start = start;
            linkStyle.length = length;
            linkStyle.foreground = linkColor;
            linkStyle.underline = true;
            linkStyle.underlineStyle = SWT.UNDERLINE_LINK;

            styledText.setStyleRange(linkStyle);
        }
    }

    /**
     * Attaches a mouse click listener that detects clicks on UUID links.
     */
    private void attachMouseListener() {
        styledText.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(MouseEvent event) {
                String uuid = getUuidAtPosition(event.x, event.y);
                if (uuid != null) {
                    navigateToElement(uuid);
                }
            }
        });
    }

    /**
     * Attaches a mouse move listener that changes the cursor to a hand when
     * hovering over a UUID link.
     */
    private void attachMouseMoveListener() {
        styledText.addMouseMoveListener(event -> {
            String uuid = getUuidAtPosition(event.x, event.y);
            if (uuid != null) {
                if (styledText.getCursor() != handCursor) {
                    styledText.setCursor(handCursor);
                }
            } else {
                if (styledText.getCursor() != defaultCursor) {
                    styledText.setCursor(defaultCursor);
                }
            }
        });
    }

    /**
     * Determines if there is a UUID at the given pixel coordinates in the StyledText.
     *
     * @param x the x pixel coordinate
     * @param y the y pixel coordinate
     * @return the UUID string if found at the position, or null
     */
    private String getUuidAtPosition(int x, int y) {
        try {
            int offset = styledText.getOffsetAtPoint(new Point(x, y));
            if (offset < 0 || offset >= styledText.getCharCount()) {
                return null;
            }

            // Get the line containing this offset
            int lineIndex = styledText.getLineAtOffset(offset);
            int lineStart = styledText.getOffsetAtLine(lineIndex);
            String lineText = styledText.getLine(lineIndex);

            // Search for UUIDs in this line
            Matcher matcher = UUID_PATTERN.matcher(lineText);
            while (matcher.find()) {
                int uuidStart = lineStart + matcher.start();
                int uuidEnd = lineStart + matcher.end();

                if (offset >= uuidStart && offset < uuidEnd) {
                    return matcher.group();
                }
            }
        } catch (IllegalArgumentException e) {
            // getOffsetAtPoint throws if coordinates are outside text bounds
        }

        return null;
    }

    /**
     * Navigates to the Capella model element with the given UUID.
     * <p>
     * Attempts to reveal the element in the Project Explorer and optionally
     * open it in the Semantic Browser view.
     *
     * @param uuid the UUID of the element to navigate to
     */
    private void navigateToElement(String uuid) {
        // PLACEHOLDER: Capella navigation API
        //
        // The actual implementation should:
        //
        // 1. Resolve the element by UUID from the active Capella session:
        //    Session session = SessionManager.INSTANCE.getSessions().iterator().next();
        //    EObject element = null;
        //    for (Resource res : session.getSemanticResources()) {
        //        TreeIterator<EObject> it = res.getAllContents();
        //        while (it.hasNext()) {
        //            EObject obj = it.next();
        //            if (obj instanceof NamedElement ne && uuid.equals(ne.getId())) {
        //                element = obj;
        //                break;
        //            }
        //        }
        //        if (element != null) break;
        //    }
        //
        // 2. Reveal in Project Explorer:
        //    IWorkbenchPage page = PlatformUI.getWorkbench()
        //        .getActiveWorkbenchWindow().getActivePage();
        //    IViewPart explorer = page.showView(
        //        "capella.project.explorer");  // PLACEHOLDER: exact view ID
        //    if (explorer instanceof CommonNavigator nav) {
        //        nav.selectReveal(new StructuredSelection(element));
        //    }
        //
        // 3. Show in Semantic Browser:
        //    IViewPart semanticBrowser = page.showView(
        //        "org.polarsys.capella.core.ui.semantic.browser.view.SemanticBrowserID");
        //    // PLACEHOLDER: Set input on semantic browser

        System.out.println("Navigate to element with UUID: " + uuid);
    }

    /**
     * Disposes of resources held by this adapter (colors, cursors).
     */
    public void dispose() {
        if (linkColor != null && !linkColor.isDisposed()) {
            linkColor.dispose();
        }
        if (handCursor != null && !handCursor.isDisposed()) {
            handCursor.dispose();
        }
        // defaultCursor is system-managed, do not dispose
    }
}
