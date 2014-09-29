/*
 *  Copyright (c) 2014, by Ivan Prisyazhniy <john.koepi@gmail.com>
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
 *  Modified 2011 - 2014, by Ivan Prisyazhniy <john.koepi@gmail.com>
 */

package net.kano.codeoutline;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;

/**
 * Manages the text outline image, keeping it synchronized with the current file
 * and painting it to the screen when requested.
 */
public class CodeOutlineImageEx extends CodeOutlineImage {
    /**
     * Creates a new code outline image for the given editor and with the given
     * listener.
     *
     * @param editor the editor to image
     * @param listener a listener for code outline image events
     */
    public CodeOutlineImageEx(EditorEx editor, CodeOutlineListener listener) {
        super(editor, listener);
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
    @Override
    protected void renderToImg(CharSequence chars, int offset, int len, LogicalPosition pos) {
        if (img == null) return;

        final EditorEx ex = (EditorEx)editor;
        final EditorHighlighter hl = ex.getHighlighter();
        final HighlighterIterator hi = hl.createIterator(offset);

        int line = pos.line, col = pos.column;

        // Render characters
        for (int i = offset; i < len; i++) {
            final char ch = chars.charAt(i);

            while (!hi.atEnd() && i > hi.getEnd()) {
                hi.advance();
            }

            if (ch == '\n') {
                line++;

                if (getScaledLine(line, scale) >= visibleImgHeight) break;
                col = 0;
            } else {
                if (col >= visibleImgWidth) continue;

                // Whitespaces are skipped inside drawChar
                drawChar(ch, col, line, getCharColor(editor, hi));

                col++;
            }
        }
    }


    /**
     * Renders the characters at the given document offset to the code outline
     * image until the first newline character is reached or until the right
     * edge of the image is reached and no more characters can be rendered.
     *
     * @param startOff the offset into the document at which to start rendering
     * @return how many characters were rendered, not including the newline
     */
    @Override
    protected int renderRestOfLineToImg(int startOff) {
        final CharSequence chars = document.getCharsSequence();
        final int endOff = document.getTextLength();
        if (startOff >= endOff) return 0;

        final LogicalPosition startPos = editor.offsetToLogicalPosition(startOff);
        final int line = startPos.line;

        final EditorEx ex = (EditorEx)editor;
        final EditorHighlighter hl = ex.getHighlighter();
        final HighlighterIterator hi = hl.createIterator(startOff);

        int col = startPos.column;
        int painted = 0;
        for (int i = startOff; i < endOff; i++) {
            final char ch = chars.charAt(i);

            while (!hi.atEnd() && i > hi.getEnd()) {
                hi.advance();
            }

            if (ch == '\n') break;

            if (col >= visibleImgWidth) break;

            drawChar(ch, col, line, getCharColor(editor, hi));
            painted++;
            col++;
        }
        return painted;
    }
}
