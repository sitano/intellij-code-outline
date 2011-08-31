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
 *  File created by keith @ Oct 25, 2003
 *  Modified by John.Koepi
 */

package net.kano.codeoutline;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.impl.commands.InvokeLaterCmd;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * Manages the text outline image, keeping it synchronized with the current file
 * and painting it to the screen when requested.
 */
public class CodeOutlineImage {
    /** An RGB color mask for a completely transparent white. */
    private static final int COLORMASK_TRANSPARENT = 0x00FFFFFF;
    /** A completely transparent white. */
    private static final Color TRANSPARENT = new Color(255, 255, 255, 0);

    /** The logical position of the offset in a file. */
    private static final LogicalPosition LOGPOS_START
            = new LogicalPosition(0, 0);

    /** The editor being outlined. */
    private final Editor editor;
    /** The document being outlined. */
    private final Document document;

    /** The text outline image. */
    private BufferedImage img = null;
    /** An empty line. */
    private int[] emptyLine = null;

    /** The width of the image visible to the user. */
    private int visibleImgWidth = 0;
    /** The height of the image visible to the user. */
    private int visibleImgHeight = 0;
    /** Vertical scale factor */
    private double scale = 1.0;

    /** A letter thickness manager. */
    private final LetterThicknessManager thicks;

    /** The listener listening to this image. */
    private final CodeOutlineListener listener;

    /** A document listener to listen for changes in the document. */
    private final DocumentListener docListener = new DocumentListener() {
        /** The logical position of the end of the changed region. */
        private LogicalPosition oldend;

        public void beforeDocumentChange(DocumentEvent event) {
            // we need to store the old end logical position before the document
            // changes, because after it changes, there's no way to convert the
            // offset to the logical position it was in before the change.

            // for example, if the user has the text:
            // int x = 2;
            // int y = 4;
            // and selects the entire first line and presses enter, the old
            // selection end offset given in the document change event is 10.
            // before the change, the logical position of offset 10 was line 0,
            // column 10. after the change, though, the logical position of
            // offset 10 is line 1, column 9.
            oldend = editor.offsetToLogicalPosition(event.getOffset() + event.getOldLength());
        }

        public void documentChanged(DocumentEvent e) {
            try {
                updateImg(e, oldend);
            } catch (Exception ex) {
                listener.handleException(CodeOutlineImage.this, ex);
            }
        }
    };

    /**
     * Creates a new code outline image for the given editor and with the given
     * listener.
     *
     * @param editor the editor to image
     * @param listener a listener for code outline image events
     */
    public CodeOutlineImage(Editor editor, CodeOutlineListener listener) {
        if (listener == null) throw new NullPointerException();

        this.editor = editor;
        this.document = editor.getDocument();
        this.listener = listener;

        try {
            thicks = LetterThicknessManager.getInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }

        init();
    }

    /**
     * Initializes listeners.
     */
    private void init() {
        document.addDocumentListener(docListener);
    }

    /**
     * Removes listeners and flushes the code outline image data.
     */
    public void dispose() {
        document.removeDocumentListener(docListener);

        if (img != null) img.flush();
    }

    /**
     * Updates the code outline image to reflect the given document change.
     *
     * @param e a document change event
     * @param oldend the logical position of the end of the "old" changed region
     *        before the change was actually made
     */
    private synchronized void updateImg(DocumentEvent e, LogicalPosition oldend) {
        // if there's no image we don't need to do anything
        if (img == null) return;

        int offset    = e.getOffset();
        int newLength = e.getNewLength();
        int oldLength = e.getOldLength();
        int width     = visibleImgWidth;
        int height    = visibleImgHeight;
        double scale  = this.scale;

        // compute the logical positions of the old and new offsets
        LogicalPosition start = editor.offsetToLogicalPosition(offset);
        LogicalPosition newend = editor.offsetToLogicalPosition(offset + newLength);

        // if the modifications were all past the bottom border, there's nothing
        // to do
        if (getScaledLine(start.line, scale) >= height) return;

        // the number of affected lines
        int affected = Math.abs(newend.line - oldend.line) + 1;

        // if the modifications were all past the right border, there's nothing
        // to do
        if (affected == 1 && start.column >= width) return;

        // the number of lines added (a negative value means lines were removed)
        int addedLines = newend.line - oldend.line;

        /*
         * If lines count changed, then redraw all the stuff if there are:
         * 1. deleted lines
         * 2. scale factor changed (scaling)
         */
        if (addedLines != 0) {
            if (addedLines < 0 || scale < 1.0 || getScaleFactor(height, document.getLineCount()) < 1.0) {
                refreshImage();
                // TODO: Delayed redraw for last timed out change
                listener.shouldRepaint(this, new Rectangle(0, 0, width, height));
                return;
            }
        }

        /*

        This method does the following things:
        1. Copy the (unmodified) rest of the line at the end of the modified
           region
        2. Move all unmodified lines, below the changed region, up or down, if
           lines were added or removed
        3. Clear data at end of line on first line of changed region
        4. Clear middle lines, if any, in the changed region
        5. Clear data at beginning of line on last line of changed region
        6. Paste the data copied in #1 at the new end of region
        7. Re-render any data which was not copied in #6 because of insufficient
           window width
        8. Render the new data in the changed region

        */
        
        // how much of the end of the last line needs to be cleared
        int needsFilling = Math.max(0, width - newend.column);
        // how much of the end of the old last line needs to be copied to the
        // new end of the last line (this takes into account how much is there,
        // and how much space there is at the end of the new line, so no unused
        // data is copied)
        int charsToCopy = Math.min(needsFilling, Math.max(0, width - oldend.column));

        // copy the (unmodified) rest of the line at the end of the modified
        // region
        WritableRaster raster = img.getRaster();
        Object endOfLine = null;
        int oldEndLine = getScaledLine(oldend.line, scale);
        int newEndLine = getScaledLine(newend.line, scale);
        if (oldEndLine < height && newEndLine < height && charsToCopy > 0) {
            endOfLine = raster.getDataElements(oldend.column, oldEndLine, charsToCopy, 1, null);
        }

        // move unaffected lines (all lines after the lines modified).
        // this never modifies any of the lines containing the modified text.
        if (addedLines != 0) {
            int ol = getScaledLine(oldend.line + 1, scale);
            int nl = getScaledLine(newend.line + 1, scale);

            int fh = height - ol;
            int th = height - nl;
            if (fh > 0 && th > 0) {
                BufferedImage from = img.getSubimage(0, ol, width, fh);
                BufferedImage to = img.getSubimage(0, nl, width, th);

                if (addedLines > 0) moveDataDown(from, to);
                else moveDataUp(from, to);
            }
        }

        // 1. clear first line chars at end of line
        // 2. clear middle lines
        // 3. clear last line chars at beginning of line
        // 4. copy line data at old end to new end
        // 5. re-render any data which was not copied because of insufficient
        //    window width

        // 1.
        int toFill = 0;
        if (affected > 1) {
            toFill = width - Math.min(width, start.column);
        } else if (start.column < newend.column) {
            toFill = Math.min(width - start.column, newend.column - start.column);
        }

        if (toFill > 0) {
            img.setRGB(start.column, getScaledLine(start.line, scale), toFill, 1, emptyLine, 0, toFill);
        }

        if (newend.line != start.line) {
            // 2.
            int last = getScaledLine(Math.min(newend.line, height-1) - 1, scale);

            for (int i = getScaledLine(start.line + 1, scale); i <= last; i++) {
                img.setRGB(0, i, width, 1, emptyLine, 0, width);
            }

            // 3.
            if (newEndLine < height) {
                int toFillEnd = Math.min(width, newend.column);
                img.setRGB(0, newEndLine, toFillEnd, 1, emptyLine, 0, toFillEnd);
            }
        }

        // 4.
        if (endOfLine != null) {
            // copy old end of line data to new end of line
            raster.setDataElements(newend.column, newEndLine, charsToCopy, 1, endOfLine);

            // clear the rest of the line, if necessary
            int diff = needsFilling - charsToCopy;
            if (diff > 0) {
                img.setRGB(newend.column + charsToCopy, newEndLine, diff, 1, emptyLine, 0, diff);
            }
        }

        // 5.
        int minCharsToCopy = Math.max(0, charsToCopy);
        int missing = needsFilling - minCharsToCopy;
        if (missing > 0) {
            int toClear = newend.column + minCharsToCopy;
            raster.setDataElements(toClear, newEndLine, width - toClear, 1, emptyLine);
            // re-render the last line, since we don't know what the data on
            // that line was, since it was past our right margin
            int renderWidth = renderRestOfLineToImg(offset + newLength + minCharsToCopy);
            int clearx = newend.column + minCharsToCopy + renderWidth;
            if (clearx < width) {
                raster.setDataElements(clearx, newEndLine, width-clearx, 1, emptyLine);
            }
        }

        // render the new text
        CharSequence nc = e.getNewFragment();
        renderToImg(nc, 0, nc.length(), start);

        // repaint the changed region
        Rectangle toRepaint = getImgRepaintRect(offset, Math.max(newLength, oldLength));
        if (affected > 1) {
            toRepaint.height = visibleImgHeight - toRepaint.y;
        }

        listener.shouldRepaint(this, toRepaint);
    }

    /**
     * Renders the characters at the given document offset to the code outline
     * image until the first newline character is reached or until the right
     * edge of the image is reached and no more characters can be rendered.
     *
     * @param startOff the offset into the document at which to start rendering
     * @return how many characters were rendered, not including the newline
     */
    private int renderRestOfLineToImg(int startOff) {
        CharSequence chars = document.getCharsSequence();
        int endOff = document.getTextLength();
        if (startOff >= endOff) return 0;

        LogicalPosition startPos = editor.offsetToLogicalPosition(startOff);
        int line = startPos.line;
        int col = startPos.column;
        int painted = 0;
        for (int i = startOff; i < endOff; i++) {
            char ch = chars.charAt(i);

            if (ch == '\n') break;

            if (col >= visibleImgWidth) break;

            drawChar(ch, col, line, scale);
            painted++;
            col++;
        }
        return painted;
    }

    /**
     * Renders a single character at the given line and column.
     *
     * @param ch the character to render
     * @param col the column number
     * @param line the line number
     * @param scale scale factor
     */
    private void drawChar(char ch, int col, int line, double scale) {
        if (!Character.isWhitespace(ch)) {
            int thickness = thicks.getThickness(ch);
            img.setRGB(col, getScaledLine(line, scale), thickness << 24);
        }
    }

    /**
     * Returns a logical position in the editor that corresponds the given point
     * in this code outline image.
     *
     * @param point the code outline image coordinates
     * @return a logical position corresponding to the given coordinates
     */
    public LogicalPosition getPositionFromPoint(Point point) {
        int x = Math.max(0, point.y);
        int y = Math.max(0, point.x);
        return new LogicalPosition(x, y);
    }

    /**
     * Returns the area in this code outline image that should be repainted to
     * update the given text region.
     *
     * @param offset the offset at which the text region to be repainted starts
     * @param length the length of the text region to repaint
     * @return the area in this code outline image that should be repainted to
     *         update the given region
     */
    public Rectangle getImgRepaintRect(int offset, int length) {
        return getImgRepaintRect(new TextRange(offset, offset + length));
    }

    /**
     * Returns the area in this code outline image that should be repainted to
     * update the given text region.
     *
     * @param range the region to be updated
     * @return the area in this code outline image that should be repainted to
     *         update the given region
     */
    public Rectangle getImgRepaintRect(TextRange range) {
        if (img == null) return null;

        int len = document.getTextLength();
        int startoff = range.getStartOffset();
        int endoff = range.getEndOffset();

        LogicalPosition start;
        if (startoff > len) {
            // this region is past the end of the file, so we repaint the entire
            // area below the end of the file
            LogicalPosition eof = editor.offsetToLogicalPosition(len);
            return getRectangle(0, eof.line, visibleImgWidth + 1, visibleImgHeight-eof.line + 1, scale);
        } else {
            start = editor.offsetToLogicalPosition(startoff);
            if (endoff > len) {
                // the region spans the end of the file
                return getRectangle(0, start.line, visibleImgWidth + 1, visibleImgHeight-start.line + 1, scale);
            }
        }

        LogicalPosition end = editor.offsetToLogicalPosition(endoff);

        if (start.line == end.line) {
            // we only need to repaint the characters modified on that line
            return getRectangle(start.column, start.line, visibleImgWidth + 1, end.line + 1, scale);
        } else {
            // we should repaint the entire lines that changed
            return getRectangle(0, start.line, visibleImgWidth + 1, end.line + 1, scale);
        }
    }

    /**
     * Returns the area in this code outline image that should be repainted to
     * update the given editor viewing region. The editor viewing region can be
     * obtained with methods like {@link ScrollingModel#getVisibleArea()}
     * <br><br>
     * This method returns a rectangle one pixel wider and taller than the
     * rectangle returned by {@link #getImgRect(Rectangle)}.
     *
     * @param rectangle an editor viewing area region
     * @return the area that should be repainted to update the given region
     */
    public Rectangle getImgRepaintRect(Rectangle rectangle) {
        Rectangle rect = getImgRect(rectangle);
        if (rect == null) return null;

        rect.height++;
        rect.width++;
        return rect;
    }

    /**
     * Returns a the rectangle to be drawn on this code outline image to
     * represent the given editor visible region.
     *
     * @param visible an editor visible region rectangle
     * @return a rectangle that represents the given editor visible region
     */
    public Rectangle getImgRect(Rectangle visible) {
        if (img == null) return null;

        // we try to see how wide the visible area is by first getting the
        // position of the last character visible on the first line, and then
        // that position on the last line. a bug in xyToLogicalPosition when
        // code folding is present forces us to do this, and this code still
        // messes up when collapsed folding regions are present on the first and
        // last visible lines.
        Rectangle visibleRect;

        LogicalPosition start = editor.xyToLogicalPosition(
                new Point(visible.x, visible.y));
        LogicalPosition start2 = editor.xyToLogicalPosition(
                new Point(visible.x + visible.width, visible.y));
        LogicalPosition end = editor.xyToLogicalPosition(
                new Point(visible.x + visible.width,
                        visible.y + visible.height));

        int w = Math.min(visibleImgWidth-start.column,
                Math.max(start2.column, end.column)-start.column)-1;
        int h = end.line-start.line;

        visibleRect = getRectangle(start.column, start.line, w, h, scale);

        return visibleRect;
    }

    /**
     * Generates the data for a horizontal line of the given width containing
     * pixels of the given color.
     *
     * @param width the width of the line to create
     * @param color a color
     * @return the data for the line
     */
    private static int[] genColoredLine(int width, int color) {
        int[] emptyLine = new int[width];
        for (int i = 0; i < emptyLine.length; i++) {
            emptyLine[i] = color;
        }
        return emptyLine;
    }

    /**
     * Copies data between the given images, starting at the bottom of the
     * images and copying then moving upwards line by line. This method is
     * suitable for when the two given images are subimages of the same image,
     * and their data may overlap.
     *
     * @param from the source image
     * @param to the destination image
     */
    private static void moveDataDown(BufferedImage from, BufferedImage to) {
        Raster raster = from.getRaster();
        WritableRaster outRaster = to.getRaster();

        int width = outRaster.getWidth();
        int height = outRaster.getHeight();
        int startX = outRaster.getMinX();
        int startY = outRaster.getMinY();

        Object tdata = null;

        for (int i = startY+height-1; i >= startY; i--)  {
            tdata = raster.getDataElements(startX,i,width,1,tdata);
            outRaster.setDataElements(startX,i,width,1, tdata);
        }
    }

    /**
     * Copies data between the given images, starting at the top of the images
     * and copying then moving downwards line by line. This method is
     * suitable for when the two given images are subimages of the same image,
     * and their data may overlap.
     *
     * @param from the source image
     * @param to the destination image
     */
    private static void moveDataUp(BufferedImage from, BufferedImage to) {
        Raster raster = from.getRaster();
        WritableRaster outRaster = to.getRaster();

        int width = outRaster.getWidth();
        int height = Math.min(raster.getHeight(), outRaster.getHeight());
        int startX = outRaster.getMinX();
        int startY = outRaster.getMinY();

        Object tdata = null;

        for (int i = startY; i < startY+height; i++)  {
            tdata = raster.getDataElements(raster.getMinX(), i, width, 1,
                    tdata);
            outRaster.setDataElements(startX, i, width, 1, tdata);
        }
    }

    public static Rectangle getRectangle(int x, int y, int width, int height, double scale) {
        return new Rectangle(x, getScaledLine(y, scale), width, getScaledLine(height, scale));
    }
    
    public int getScaledLine(int line) {
        return getScaledLine(line, scale);
    }

    public static int getOutScaledLine(int line, double scale) {
        return (int)(1.0 * line / scale);
    }

    public static int getScaledLine(int line, double scale) {
        return (int)Math.floor(scale * line);
    }

    private static double getScaleFactor(int height, int lines) {
        double scale = 1.0 * height / lines;
        if (scale > 1.0) scale = 1000.0;
        else scale = Math.floor(scale * 1000.0) - 1.0;
        return scale / 1000.0;
    }

    public double getScale() {
        return scale;
    }

    /**
     * Renders the given characters to the code outline image starting at the
     * given position.
     *
     * @param document render full document
     * @param pos the position at which to start rendering
     */
    private void renderToImg(Document document, LogicalPosition pos) {
        if (img == null) return;

        CharSequence chars = document.getCharsSequence();
        int lines = document.getLineCount(), len = chars.length();

        double scale = getScaleFactor(visibleImgHeight, lines);
        this.scale = scale;

        int line = pos.line, col = pos.column;

        // Render characters
        for (int i = 0; i < len; i++) {
            char ch = chars.charAt(i);

            if (ch == '\n') {
                line++;

                if (getScaledLine(line, scale) >= visibleImgHeight) break;
                col = 0;
            } else {
                if (col >= visibleImgWidth)
                    continue;

                // Whitespaces are skipped inside drawChar
                drawChar(ch, col, line, scale);

                col++;
            }
        }
    }


    /**
     * Renders the given characters to the code outline image starting at the
     * given position.
     *
     * @param chars a character array
     * @param offset the offset into the given array to start rendering
     * @param len the number of characters to render
     * @param pos the position at which to start rendering
     */
    private void renderToImg(CharSequence chars, int offset, int len, LogicalPosition pos) {
        if (img == null) return;

        int line = pos.line;
        int col = pos.column;
        double scale = this.scale;

        // Render characters
        for (int i = offset; i < len; i++) {
            char ch = chars.charAt(i);

            if (ch == '\n') {
                line++;

                if (getScaledLine(line, scale) >= visibleImgHeight) break;
                col = 0;
            } else {
                if (col >= visibleImgWidth) continue;

                // Whitespaces are skipped inside drawChar
                drawChar(ch, col, line, scale);

                col++;
            }
        }
    }

    /**
     * Ensures that the backing image is as large or larger than the given
     * dimensions. If it is not, the image is re-created and the text outline is
     * re-rendered.
     *
     * @param gc a graphics configuration object
     * @param width the minimum width of the image
     * @param height the minimum height of the image
     */
    public synchronized boolean checkImage(GraphicsConfiguration gc, int width, int height) {
        if (emptyLine == null || visibleImgWidth != width) {
            emptyLine = genColoredLine(width, COLORMASK_TRANSPARENT);
        }

        visibleImgWidth = width;
        visibleImgHeight = height;

        if (img == null || img.getWidth() < width || img.getHeight() < height) {
            if (img != null) {
                // clear out the old image data
                img.flush();
                img = null;
            }

            // we allow 40 pixels of resizing before generating a new image
            if (gc == null) return false;

            img = gc.createCompatibleImage(width + 40, height + 40, Transparency.TRANSLUCENT);

            refreshImage();

            return true;
        } else {
            // if the viewing area is now larger than what we've been painting,
            // we should repaint the whole thing
            if (width > img.getWidth() || height > img.getHeight()) {
                refreshImage();

                return true;
            }
        }

        return false;
    }

    /**
     * Clears the backing image and re-renders it from the editor text.
     */
    public void refreshImage() {
        if (img == null) return;

        Graphics2D g = img.createGraphics();
        g.setBackground(TRANSPARENT);
        g.clearRect(0, 0, visibleImgWidth, visibleImgHeight);

        genImage();
    }

    /**
     * Renders the text in the editor to the backing image.
     */
    private void genImage() {
        renderToImg(document, LOGPOS_START);
    }

    /**
     * Paints this code outline image to the given graphics device.
     *
     * @param g a graphics device
     */
    public void paint(Graphics2D g) {
        if (img == null) return;

        g.drawImage(img, null, 0, 0);
    }
}
