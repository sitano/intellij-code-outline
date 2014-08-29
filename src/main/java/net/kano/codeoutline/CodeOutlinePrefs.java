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
 *  File created by keith @ Oct 27, 2003
 *  Modified 2011 - 2014, by Ivan Prisyazhniy <john.koepi@gmail.com>
 */

package net.kano.codeoutline;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Holds application-wide preferences for the code outline plugin.
 */
public class CodeOutlinePrefs {
    /** For property change listeners. */
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /** Whether scrolling via the code outline should be animated. */
    private boolean animated = true;
    /**
     * Whether the current line should be highlighted in the editor on
     * mouseover.
     */
    private boolean highlightLine = true;

    /**
     * Returns whether scrolling via the code outline should be animated.
     *
     * @return whether scrolling via the code outline should be animated
     */
    public boolean isAnimated() { return animated; }

    /**
     * Sets whether scrolling via the code outline should be animated.
     *
     * @param animated whether scrolling via the code outline should be animated
     */
    public void setAnimated(boolean animated) {
        boolean old = this.animated;

        this.animated = animated;

        pcs.firePropertyChange("animated", old, animated);
    }

    /**
     * Sets whether the line over which the mouse is hovering in the code
     * outline should be highlighted in the editor.
     *
     * @param highlightLine whether the currently moused-over line in the code
     *        outline should be highlighted in the editor
     */
    public void setHighlightLine(boolean highlightLine) {
        boolean old = this.highlightLine;

        this.highlightLine = highlightLine;

        pcs.firePropertyChange("highlightLine", old, highlightLine);
    }

    /**
     * Returns whether the line over which the mouse is hovering in the code
     * outline should be highlighted in the editor.
     *
     * @return whether the currently moused-over line in the code outline should
     *         be highlighted in the editor
     */
    public boolean isHighlightLine() { return highlightLine; }

    /**
     * Adds the given property change listener for all properties.
     *
     * @param l a property change listener
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }
    /**
     * Adds the given property change listener for the given property.
     *
     * @param property the property to listen for
     * @param l a property change listener
     */
    public void addPropertyChangeListener(String property,
            PropertyChangeListener l) {
        pcs.addPropertyChangeListener(property, l);
    }
    /**
     * Removes the given property change listener for the given property.
     *
     * @param property the property to stop listening for
     * @param l a property change listener
     */
    public void removePropertyChangeListener(String property,
            PropertyChangeListener l) {
        pcs.removePropertyChangeListener(property, l);
    }
    /**
     * Removes the given property change listener which was listening for all
     * properties.
     *
     * @param l a property change listener
     */
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }
}
