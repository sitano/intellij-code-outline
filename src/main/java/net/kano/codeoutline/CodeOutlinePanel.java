/*
 *  Copyright (c) 2003, Keith Lea
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without 
 *  modification, are permitted provided that the following conditions 
 *  are met:
 *
 *  - Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer. 
 *  - Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution. 
 *  - Neither the name of Keith Lea nor the names of its
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission. 
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 *  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 *  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN 
 *  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 *  POSSIBILITY OF SUCH DAMAGE.
 *
 *  File created by keith @ Oct 24, 2003
 *  Modified by John.Koepi
 */

package net.kano.codeoutline;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;

import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * A code outline panel for a single text editor. The code outline panel manages
 * everything about the code outline but the text outline itself. This includes
 * painting the selection and visible region, scrolling, configuration UI, and
 * highlighting the current line.
 */
public class CodeOutlinePanel extends JPanel {
    /** A set of text attributes for highlighting the currently hovered line. */
    private static final TextAttributes CURRENTLINE_ATTRIBUTES
            = new TextAttributes(null, new Color(220, 255, 220), null,
                    null, Font.PLAIN);
    /** The color of the right margin. */
    private static final Color COLOR_RIGHTMARGIN = new Color(224, 224, 224);
    /** The background color of the visible region rectangle. */
    private static final Color COLOR_VISIBLE_BG = new Color(255, 240, 240);
    /** The border color of the visible region rectangle. */
    private static final Color COLOR_VISIBLE_BORDER = new Color(100, 20, 20);

    /** The code outline plugin instance which instantiated this panel. */
    private final CodeOutlinePlugin plugin;
    /** The project for which this code outline panel is shown. */
    private final Project project;
    /** The editor whose code is outlined in this panel. */
    private final Editor editor;
    /** The set of code outline preferences to obey. */
    private final CodeOutlinePrefs prefs;

    /** The selection offset, for use in Shift+Mouse1 dragging. */
    private int selectionOffset = -1;

    /** The range highlighter used to highlight the currently hovered line. */
    private RangeHighlighter highlighter;

    /** The text outline image used in this panel. */
    private final CodeOutlineImage image;

    /** The context menu that appears when right-clicking the code outline. */
    private JPopupMenu contextMenu = new JPopupMenu();

    /** A listener for IDEA editor scrolling events. */
    private VisibleAreaListener scrollListener = new VisibleAreaListener() {
        /** The last calculated visible area shading rectangle. */
        private Rectangle rold;

        public void visibleAreaChanged(VisibleAreaEvent e) {
            // we need to repaint the old visible area and the new visible area
            // for some reason calling getImgRepaintRect(e.getOldRectangle())
            // just doesn't work right, so we cache it in rold
            Rectangle rnew = image.getImgRepaintRect(e.getNewRectangle());
            if (rold != null) {
                repaint(rold);
            } else {
                // if we haven't recorded an old viewing rectangle yet, we can
                // try to use the given one
                Rectangle oldview = e.getOldRectangle();
                if (oldview != null) {
                    Rectangle old = image.getImgRepaintRect(oldview);
                    if (old != null) {
                        old.grow(1, 1);
                        repaint(old);
                    }
                }
            }
            if (rnew != null) {
                // the visible area box has the same dimensions as
                // getImgRepaintRect returns, so we paint one pixel out in each
                // direction
                rnew.grow(1, 1);
                repaint(rnew);
            }
            rold = rnew;
        }
    };
    /** A listener for IDEA editor text selection events. */
    private SelectionListener selectListener = new SelectionListener() {
        public void selectionChanged(SelectionEvent e) {
            // repaint the old selection area and the new selection area
            Rectangle rold = image.getImgRepaintRect(e.getOldRange());
            if (rold != null) repaint(rold);
            Rectangle rnew = image.getImgRepaintRect(e.getNewRange());
            if (rnew != null) repaint(rnew);
        }
    };
    /** A listener for mouse events in this code outline panel. */
    private MouseListener mouseListener = new MouseAdapter() {
        /**
         * The horizontal scrolling position when the user began to Preview
         * Scroll.
         */
        private int origscrollh = -1;
        /**
         * The vertical scrolling position when the user began to Preview
         * Scroll.
         */
        private int origscrollv = -1;
        /**
         * Whether the scrolling mechanism should smooth scroll when returning
         * to the original scrolling position after Preview Scrolling.
         */
        private boolean slideBack = false;

        public void mousePressed(MouseEvent e) {
            Point point = e.getPoint();
            
            double scale = image.getScale();
            if (scale < 1.0) {
                point = new Point(point.x, CodeOutlineImage.getOutScaledLine(point.y, scale));
            }

            if (SwingUtilities.isLeftMouseButton(e)) {
                // left mouse button always moves the cursor to the clicked
                // position, but what else it does varies

                // we don't want to animate if Ctrl is being held down (that's
                // what Ctrl does)
                boolean animate = !e.isControlDown();

                // we want to unfold the region where the user clicked if the
                // user double-clicked or if he is holding Shift to select text
                // (I think the user wants this to happen if he is selecting
                // text)
                boolean multiclick = e.getClickCount() >= 2;
                boolean shiftDown = e.isShiftDown();

                seekTo(point, animate, multiclick || shiftDown);

                // we want to reset the original scroll position to reset
                // Preview Scroll. this only matters when the user clicks with
                // the left mouse button while still holding down the middle
                // button - in this case, he probably wants to stop Preview
                // Scrolling.
                origscrollh = -1;
                origscrollv = -1;

                if (shiftDown) {
                    // if the user is holding shift, we want to start a
                    // selection
                    updateSelection(point, true);
                } else {
                    // if the user is not holding shift, the selection should
                    // be cleared just like if the user left-clicked somewhere
                    // in the editor itself
                    editor.getSelectionModel().removeSelection();
                }

            } else if (SwingUtilities.isRightMouseButton(e)) {
                // the right mouse button shows the context menu
                contextMenu.show(CodeOutlinePanel.this, e.getX(), e.getY());

            } else if (SwingUtilities.isMiddleMouseButton(e)) {
                // the middle mouse button is Preview Scroll - it scrolls while
                // dragging, then resets the scroll position when you release
                // the mouse button
                ScrollingModel sm = editor.getScrollingModel();

                // we want to save the scroll positions when smooth scrolling is
                // complete. otherwise, repeatedly middle-clicking too quickly
                // ends up storing an intermediate value in origscrollh and
                // origscrollv, causing the final scroll region to move.
                Rectangle vis = sm.getVisibleAreaOnScrollingFinished();
                origscrollh = vis.x;
                origscrollv = vis.y;

                // if Ctrl is being held down, we don't want to "slide" or
                // smooth scroll; we want to skip back and forth non-smoothly
                slideBack = !e.isControlDown();

                previewTo(point, slideBack);
            }
        }

        public void mouseReleased(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                // when the user releases the left mouse button, the selection
                // is finalized and the cursor goes to the specified mouse
                // position
                Point point = e.getPoint();

                double scale = image.getScale();
                if (scale < 1.0) {
                    point = new Point(point.x, CodeOutlineImage.getOutScaledLine(point.y, scale));
                }

                seekTo(point, true, e.isShiftDown());

                updateSelection(point, false);
                selectionOffset = -1;

                origscrollh = -1;
                origscrollv = -1;

            } else if (SwingUtilities.isMiddleMouseButton(e)) {
                // when the user releases the middle mouse button, Preview
                // Scrolling is complete, and the document should be scrolled
                // back to where it was when the user started Preview Scrolling

                // origscrollh and origscrollv could have been reset while the
                // user was Preview Scrolling. if this happened, we don't have
                // to do anything
                if (origscrollh == -1) return;

                scrollToXY(origscrollh, origscrollv, this.slideBack);

                origscrollh = -1;
                origscrollv = -1;
            }
        }

        public void mouseExited(MouseEvent e) {
            // when the mouse exits the code outline panel, the currently
            // hovered line must be de-highlighted
            mouseout();
        }
    };
    /** A mouse motion listener for this code outline panel. */
    private MouseMotionListener mouseMotionListener
            = new MouseMotionListener() {
        public void mouseDragged(MouseEvent e) {
            Point point = e.getPoint();

            double scale = image.getScale();
            if (scale < 1.0) {
                point = new Point(point.x, CodeOutlineImage.getOutScaledLine(point.y, scale));
            }

            // the currently hovered line needs to be updated
            mouseover(point);

            if (SwingUtilities.isLeftMouseButton(e)) {
                // dragging with the left mouse button should move the cursor
                // and update the selected text region, if the user is creating
                // a selection by holding Shift
                seekTo(point, false, false);
                updateSelection(point, false);

            } else if (SwingUtilities.isMiddleMouseButton(e)) {
                // dragging with the middle mouse button should Preview Scroll
                // to the given location
                previewTo(point, false);
            }
        }

        public void mouseMoved(MouseEvent e) {
            // when the mouse moves, the currently hovered line should be
            // updated
            mouseover(e.getPoint());
        }
    };
    /** A mouse wheel listener for the code ouline panel. */
    private MouseWheelListener mouseWheelListener = new MouseWheelListener() {
        public void mouseWheelMoved(MouseWheelEvent e) {
            ScrollingModel sm = editor.getScrollingModel();

            // we disable animation for page up/down like IDEA does
            try {
                sm.disableAnimation();
                // scroll one page up/down for each mouse wheel click
                sm.scrollVertically(sm.getVerticalScrollOffset()
                        + (e.getWheelRotation() * sm.getVisibleArea().height));
            } finally {
                sm.enableAnimation();
            }
        }
    };
    /**
     * A listener for determining when the panel should be repainted to reflect
     * changes in the text outline.
     */
    private CodeOutlineListener repaintListener = new CodeOutlineListener() {
        public void shouldRepaint(CodeOutlineImage image, Rectangle region) {
            repaint(region);
        }

        public void handleException(CodeOutlineImage image, Exception e) {
            plugin.handleException(e);
        }
    };

    /**
     * A property change listener for detecting changes in the currently hovered
     * line highlighting option.
     */
    private PropertyChangeListener highlightPrefListener
            = new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    updateHighlightedLine();
                }
            };

    /** The "Animated Scrolling" menu item. */
    private JCheckBoxMenuItem animatedMenuItem
            = new JCheckBoxMenuItem(new AnimateOptionAction());
    /** The "Highlight Current Line" menu item. */
    private JCheckBoxMenuItem highlightMenuItem
            = new JCheckBoxMenuItem(new HighlightOptionAction());

    { // init
        // we are already buffering the text outline. when painting, we mostly
        // just draw rectangles around the text ouline image, which is pretty
        // fast, so we don't need to double-buffer the panel itself
        setDoubleBuffered(false);

        // changes in the the "Animated Scrolling" checkbox should be reflected
        // in our preferences object
        animatedMenuItem.addPropertyChangeListener("selected",
                new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                boolean animated = ((Boolean) evt.getNewValue()).booleanValue();
                prefs.setAnimated(animated);
            }
        });
        // changes in the the "Highlight Current Line" checkbox should be
        // reflected in our preferences object
        highlightMenuItem.addPropertyChangeListener("selected",
                new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                boolean highlighted
                        = ((Boolean) evt.getNewValue()).booleanValue();
                prefs.setHighlightLine(highlighted);
            }
        });

        contextMenu.add(animatedMenuItem);
        contextMenu.add(highlightMenuItem);
        contextMenu.addSeparator();
        contextMenu.add(new RefreshAction());
        // the context menu's checkboxes are only updated from the code outline
        // preferences object when they are needed (before the menu is shown)
        contextMenu.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuCanceled(PopupMenuEvent e) { }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { }

            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                animatedMenuItem.setSelected(prefs.isAnimated());
                highlightMenuItem.setSelected(prefs.isHighlightLine());
            }
        });

        addMouseListener(mouseListener);
        addMouseMotionListener(mouseMotionListener);
        addMouseWheelListener(mouseWheelListener);
    }

    /**
     * Creates a new code outline panel for the given plugin, project, and
     * editor.
     *
     * @param plugin a code outline plugin instance
     * @param project the project with which this panel is associated
     * @param editor the editor whose contents this panel outlines
     */
    public CodeOutlinePanel(CodeOutlinePlugin plugin, Project project,
            Editor editor) {
        this.plugin = plugin;
        this.project = project;
        this.editor = editor;
        image = new CodeOutlineImage(editor, repaintListener);
        prefs = plugin.getPrefs();

        init();
    }

    /**
     * Initializes listeners.
     */
    private void init() {
        prefs.addPropertyChangeListener("highlightLine", highlightPrefListener);
        editor.getScrollingModel().addVisibleAreaListener(scrollListener);
        editor.getSelectionModel().addSelectionListener(selectListener);
    }

    /**
     * Removes listeners and tells the text outline image to dispose of itself
     * as well.
     */
    public void dispose() {
        image.dispose();
        prefs.removePropertyChangeListener("highlightLine",
                highlightPrefListener);
        editor.getScrollingModel().removeVisibleAreaListener(scrollListener);
        editor.getSelectionModel().removeSelectionListener(selectListener);
    }

    /**
     * Moves the caret to the end of the line corresponding to the given
     * position, with the given options. If the user has selected not to animate
     * scrolling, the value of <code>animate</code> is ignored. If
     * <code>insideCollapsed</code> is <code>false</code>, the cursor will be
     * placed at either the beginning of the folding region or at the end of the
     * line on which the folding region ends.
     * <br><br>
     * TODO: This method does not support seeking inside a collapsed folding
     * region which is not the last folding region on the line gracefully.
     *
     * @param point the point in the code outline panel whose corresponding
     *        location should be the new caret position
     * @param animate whether any scrolling involved in seeking should be
     *        animated
     * @param insideCollapsed whether the cursor should be placed within
     *        collapsed folding regions
     */
    private void seekTo(Point point, boolean animate, boolean insideCollapsed) {
        LogicalPosition pos = image.getPositionFromPoint(point);
        int offset = getEndOfLineOffset(pos);
        seekTo(offset, insideCollapsed);
        scrollTo(pos, animate);
    }

    /**
     * Moves the caret to the given position, with the given options. If the
     * user has selected not to animate scrolling, the value of
     * <code>animate</code> is ignored. If <code>insideCollapsed</code> is
     * <code>false</code>, the cursor will be placed at either the beginning of
     * the folding region or at the end of the line on which the folding region
     * ends.
     *
     * @param offset the offset to set as the new caret position
     * @param insideCollapsed whether the cursor should be placed within
     *        collapsed folding regions
     */
    private void seekTo(int offset, boolean insideCollapsed) {
        FoldingModel fm = editor.getFoldingModel();
        if (!insideCollapsed && fm.isOffsetCollapsed(offset)) {
            // we need to find a nearby offset which is not inside a collapsed
            // folding region
            int oldoff = offset;
            offset = findUncollapsedOffset(offset);
            if (oldoff > offset) {
                // if the offset we found is past the original offset, we can
                // safely skip to the end of the line. doing so otherwise might
                // cause an infinite loop going back and forth.
                offset = getEndOfLineOffset(
                        editor.offsetToLogicalPosition(offset));
            }
        }

        editor.getCaretModel().moveToOffset(offset);
    }

    /**
     * Returns an offset into the document which describes the end of the line
     * given in the given <code>LogicalPosition</code>.
     *
     * @param pos a position
     * @return the offset into the document at the end of the line on which the
     *         given position lies
     */
    private int getEndOfLineOffset(LogicalPosition pos) {
        LogicalPosition nextline = new LogicalPosition(pos.line+1, 0);
        int offset = editor.logicalPositionToOffset(nextline);
        return Math.max(0, offset - 1);
    }

    /**
     * Returns an offset into the document near the given offset which is not in
     * a collapsed folding region. If none can be found, the offset
     * <code>0</code> is returned.
     *
     * @param offset an offset
     * @return an offset near the given offset which is not collapsed
     */
    private int findUncollapsedOffset(int offset) {
        FoldingModel fm = editor.getFoldingModel();
        int last = editor.getDocument().getTextLength();
        for (int i = offset; i <= last; i++) {
            if (!fm.isOffsetCollapsed(i)) return i;
        }
        for (int i = offset; i >= 0; i--) {
            if (!fm.isOffsetCollapsed(i)) return i;
        }

        return 0;
    }

    /**
     * Preview Scrolls to the given position. If the user has selected not to
     * animate code outline scrolling operations, the value of
     * <code>animate</code> is ignored.
     *
     * @param point a point in the code outline panel corresponding to the
     *        position to scroll to
     * @param animate whether the scrolling should be animated
     */
    private void previewTo(Point point, boolean animate) {
        LogicalPosition pos = image.getPositionFromPoint(point);
        scrollTo(pos, animate);
    }

    /**
     * Returns whether a scroll operation should "cut" instead of animating,
     * based on the given value and the user's preferences.
     *
     * @param animate whether animation is suggested
     * @return whether a scroll operation should cut instead of animating
     */
    private boolean shouldCut(boolean animate) {
        return !prefs.isAnimated() || !animate;
    }

    /**
     * Scrolls to the given coordinates. If the user has chosen not to animate
     * scrolling operations, the value of <code>animate</code> is ignored.
     *
     * @param scrollh the x-axis position, in pixels
     * @param scrollv the y-axis position, in pixels
     * @param animate whether this operation should be animated
     */
    private void scrollToXY(int scrollh, int scrollv, boolean animate) {
        ScrollingModel sm = editor.getScrollingModel();
        boolean cut = shouldCut(animate);
        try {
            if (cut) sm.disableAnimation();
            sm.scrollHorizontally(scrollh);
            sm.scrollVertically(scrollv);
        } finally {
            if (cut) sm.enableAnimation();
        }
    }

    /**
     * Scrolls to make the editor caret visible. If the user has chosen not to
     * animate scrolling operations, the value of <code>animate</code> is ignored.
     *
     * @param animate whether this operation should be animated
     */
    private void scrollToCaret(boolean animate) {
        ScrollingModel sm = editor.getScrollingModel();
        boolean cut = shouldCut(animate);
        try {
            if (cut) sm.disableAnimation();
            sm.scrollToCaret(ScrollType.MAKE_VISIBLE);
        } finally {
            if (cut) sm.enableAnimation();
        }
    }

    /**
     * Scrolls so the given position is in the center of the window. If the user
     * has chosen not to animate scrolling operations, the value of
     * <code>animate</code> is ignored.
     *
     * @param pos the position to scroll to
     * @param animate whether this operation should be animated
     */
    private void scrollTo(LogicalPosition pos, boolean animate) {
        ScrollingModel sm = editor.getScrollingModel();
        boolean cut = shouldCut(animate);
        try {
            if (cut) sm.disableAnimation();
            // Handy centring by x-axis
            // This is work around for use of MAKE_VISIBLE
            Point targetPoint = editor.logicalPositionToXY(pos);
            int viewX = sm.getVisibleArea().x;
            int viewWidth = sm.getVisibleArea().width;
            int deltaX = targetPoint.x - (viewX + viewWidth / 2);
            if (deltaX > 0) {
                LogicalPosition pos2 = editor.xyToLogicalPosition(new Point(viewX + viewWidth + deltaX, 0));
                pos = new LogicalPosition(pos.line, pos2.column - 5);
            } else {
                int newX = Math.max(0, viewX + deltaX);
                LogicalPosition pos2 = editor.xyToLogicalPosition(new Point(newX, 0));
                pos = new LogicalPosition(pos.line, pos2.column);
            }
            // Don't know why MAKE_CENTER does wrong.
            sm.scrollTo(pos, ScrollType.MAKE_VISIBLE);
        } finally {
            if (cut) sm.enableAnimation();
        }
    }

    /**
     * Updates the text selection region using the given code outline panel
     * coordinates. If no original selection offset is set, the value of
     * <code>create</code> determines whether a selection will be created (if
     * <code>create</code> is <code>true</code>) or no change will be made to
     * the selection (if <code>create</code> is <code>false</code>).
     *
     * @param point a point in the code outline panel
     * @param create whether to create an initial selection offset if none is
     *        set
     */
    private void updateSelection(Point point, boolean create) {
        SelectionModel sm = editor.getSelectionModel();
        LogicalPosition pos = image.getPositionFromPoint(point);
        int off = editor.logicalPositionToOffset(pos);
        if (selectionOffset == -1) {
            // no initial selection offset has been set, so no selection can
            // be made. we can set the selection offset, though, if create is
            // true.
            if (!create) return;
            selectionOffset = off;
        }

        // set the selection from the lowest offset to the highest
        if (off > selectionOffset) sm.setSelection(selectionOffset, off);
        else sm.setSelection(off, selectionOffset);
    }

    /**
     * The last position of the mouse on the code outline panel, or
     * <code>null</code> if the mouse is not hovering over the panel.
     */
    private Point lastMousePoint = null;

    /**
     * Highlights the line associated with the given code outline panel
     * position, if the user has this option enabled.
     *
     * @param point a point in the code outline panel
     */
    private synchronized void mouseover(Point point) {
        lastMousePoint = point;
        if (!prefs.isHighlightLine()) return;

        highlightCurrentLine();
    }

    /**
     * Resets the last mouse point field and erases any highlighted line.
     */
    private synchronized void mouseout() {
        lastMousePoint = null;

        clearHighlightedLine();
    }

    /**
     * Currently scale factor is static 1:1
     */
    private int getLineFromMousePointY(int mousePointY) {
        return mousePointY;
    }
    
    /**
     * Highlights the line specified by <code>lastMousePoint</code>. This method
     * does nothing if <code>lastMousePoint</code> is <code>null</code>.
     */
    private void highlightCurrentLine() {
        if (lastMousePoint == null) return;

        clearHighlightedLine();
        MarkupModel mm = editor.getMarkupModel();
        int line = getLineFromMousePointY(lastMousePoint.y);
        if (line >= 0 && line < editor.getDocument().getLineCount()) {
            highlighter = mm.addLineHighlighter(line, 100, CURRENTLINE_ATTRIBUTES);
        }
    }

    /**
     * Highlights the line which should be highlighted according to the last
     * mouse position; clears the highlighted line if the user has this option
     * turned off.
     */
    private void updateHighlightedLine() {
        if (prefs.isHighlightLine() && lastMousePoint != null) {
            highlightCurrentLine();
        } else {
            clearHighlightedLine();
        }
    }

    /**
     * Erases the highlighting for the currently highlighted line.
     */
    private void clearHighlightedLine() {
        if (highlighter != null) {
            editor.getMarkupModel().removeHighlighter(highlighter);
            highlighter = null;
        }
    }

    /**
     * Draws a selection block between the two given positions.
     *
     * @param g the graphics device to paint to
     * @param from the starting position
     * @param to the ending position
     */
    private void drawSelection(Graphics2D g, LogicalPosition from, LogicalPosition to) {
        int toFillStart;
        double scale = image.getScale();

        if (from.line == to.line) toFillStart = to.column-from.column;
        else toFillStart = getWidth() - from.column;
        // Start to draw first line of selected block ....[----
        drawLine(g, from.column, from.line, from.column + toFillStart, from.line, scale);

        int lineDiff = Math.abs(from.line - to.line);
        if (lineDiff >= 2) {
            fillRectNotZero(g, 0, from.line + 1, getWidth(),
                to.line - from.line - 1, scale);
            // Draw finish line at next line of bottom of the box
            int toLine =
                CodeOutlineImage.getScaledLine(from.line + 1, scale) +
                CodeOutlineImage.getScaledLine(to.line - from.line - 1, scale);
            g.drawLine(0, toLine, to.column, toLine);
        } else if (from.line != to.line) {
            // Next line overlaps with to.line, to get next line and draw it (to prevent hiding)
            int fromLine = CodeOutlineImage.getScaledLine(from.line, scale);
            g.drawLine(0, fromLine + 1, to.column, fromLine + 1);
        }
    }

    public void fillRectNotZero(Graphics2D g, int x, int y, int width, int height, double scale) {
        y = image.getScaledLine(y, scale);
        height = image.getScaledLine(height, scale);
        if (height < 1) height = 1;
        g.fillRect(x, y, width, height);
    }

    public void fillRect(Graphics2D g, int x, int y, int width, int height, double scale) {
        g.fillRect(x, image.getScaledLine(y, scale), width, image.getScaledLine(height, scale));
    }

    public void drawLine(Graphics2D g, int x1, int y1, int x2, int y2, double scale) {
        g.drawLine(x1, image.getScaledLine(y1, scale), x2, image.getScaledLine(y2, scale));
    }

    /**
     * Repaints the entire code outline panel, reloading the editor text
     * completely by recaching the file.
     */
    public void refresh() {
        image.refreshImage();
        repaint();
    }

    protected void paintComponent(Graphics g1) {
        Graphics2D g = (Graphics2D) g1;

        // make sure the text outline image is big enough
        image.checkImage(getGraphicsConfiguration(), getWidth(), getHeight());

        // fill the whole area with white
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, getWidth(), getHeight());

        // compute the area that should be painted as the visible region
        Rectangle visible = editor.getScrollingModel().getVisibleArea();
        Rectangle visibleRect = image.getImgRect(visible);

        // draw the visible area background
        if (visibleRect != null) {
            visibleRect.grow(1, 1);
            g.setColor(COLOR_VISIBLE_BG);
            g.fill(visibleRect);
        }

        // draw the right margin
        EditorSettings editorSettings = editor.getSettings();
        if (editorSettings.isRightMarginShown()) {
            int margin = editorSettings.getRightMargin(project);
            g.setColor(COLOR_RIGHTMARGIN);
            g.drawLine(margin, 0, margin, getHeight());
        }

        // draw the selection
        SelectionModel sm = editor.getSelectionModel();
        if (sm.hasSelection()) {
            LogicalPosition sstart = editor.offsetToLogicalPosition(sm.getSelectionStart());
            LogicalPosition send  = editor.offsetToLogicalPosition(sm.getSelectionEnd());

            g.setColor(Color.BLUE);
            drawSelection(g, sstart, send);
        }

        // draw the visible area border, over the margin and the selection
        if (visibleRect != null) {
            g.setColor(COLOR_VISIBLE_BORDER);
            g.draw(visibleRect);
        }

        // draw the text itself
        image.paint(g);
    }

    /**
     * An action that {@linkplain CodeOutlinePanel#refresh() refreshes} the
     * display, reloading the editor text completely.
     */
    private class RefreshAction extends AbstractAction {
        /**
         * Creates a new Refresh action.
         */
        public RefreshAction() {
            super("Refresh");
            putValue(AbstractAction.MNEMONIC_KEY, new Integer(KeyEvent.VK_R));
        }

        public void actionPerformed(ActionEvent e) {
            refresh();
        }
    }
    /**
     * An action that updates the animated scrolling option.
     */
    private class AnimateOptionAction extends AbstractAction {
        /**
         * Creates a new animation preference update action.
         */
        public AnimateOptionAction() {
            super("Animated Scroll");
            putValue(MNEMONIC_KEY, new Integer(KeyEvent.VK_A));
        }

        public void actionPerformed(ActionEvent e) {
            prefs.setAnimated(animatedMenuItem.isSelected());
        }
    }
    /**
     * An action that updates the highlight current line option.
     */
    private class HighlightOptionAction extends AbstractAction {
        /**
         * Creates a new highlight current line preference update action.
         */
        public HighlightOptionAction() {
            super("Highlight Current Line");
            putValue(MNEMONIC_KEY, new Integer(KeyEvent.VK_H));
        }

        public void actionPerformed(ActionEvent e) {
            prefs.setHighlightLine(highlightMenuItem.isSelected());
        }
    }
}
