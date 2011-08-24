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
 *  File created by keith @ Oct 26, 2003
 *  Modified by John.Koepi
 */

package net.kano.codeoutline;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Computes and allows access to the thickness (or "overall opacity") of each
 * ASCII text character in the system's default monospaced font.
 */
public class LetterThicknessManager {
    /**
     * The JDK 1.4 fully-qualified classname for FontDesignMetrics.
     */
    private static final String JDK_14_CLASS = "sun.awt.font.FontDesignMetrics";
    /**
     * The JDK 5.0 fully-qualified classname for FontDesignMetrics.
     */
    private static final String JDK_5_CLASS = "sun.font.FontDesignMetrics";

    /**
     * A singleton letter thickness object.
     */
    private static LetterThicknessManager singleton;

    /**
     * Returns a <code>LetterThicknessManager</code> instance.
     *
     * @return a <code>LetterThicknessManager</code> instance
     */
    public synchronized static LetterThicknessManager getInstance()
        throws ClassNotFoundException {

        if (singleton == null) {
            singleton = new LetterThicknessManager();
        }

        return singleton;
    }

    /**
     * The first character to be measured.
     */
    private static final char FIRST = 33;
    /**
     * The last character to be measured.
     */
    private static final char LAST = 126;

    /**
     * The array of character thicknesses.
     */
    private final int[] thicknesses = new int[LAST - FIRST + 1];

    /**
     * Creates a new letter thickness manager.
     */
    private LetterThicknessManager() throws ClassNotFoundException {
        Font font = new Font("monospace", Font.PLAIN, 12);

        Class fdm = getFontDesignMetricsClass();
        try {
            Class[] params = new Class[]{ Font.class };
            Object[] args = new Object[]{ font };

            FontMetrics fm = null;

            try {
                // Static fabric method (JDK6, OpenJDK7)
                Method staticMethodConstructor = fdm.getMethod("getMetrics", params);
                fm = (FontMetrics) staticMethodConstructor.invoke(null, font);
            } catch (NoSuchMethodException e) {
                // Constructor (old JDK4, 5 impl)
                Constructor constructor = fdm.getConstructor(params);
                fm = (FontMetrics) constructor.newInstance(args);
            } finally {
                setMetrics(fm);
            }
        } catch (InvocationTargetException e) {
            Throwable te = e.getTargetException();
            if (te instanceof RuntimeException) throw (RuntimeException) te;
            else throw new RuntimeException(te);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a class object representing the internal Sun class
     * <code>FontDesignMetrics</code>.
     *
     * @return a class for <code>FontDesignMetrics</code>
     * @throws ClassNotFoundException if the class can be found
     */
    private Class getFontDesignMetricsClass() throws ClassNotFoundException {
        Class fdm = getClass(JDK_14_CLASS);

        if (fdm == null)
            fdm = getClass(JDK_5_CLASS);

        if (fdm == null)
            throw new ClassNotFoundException("Could not find " + JDK_14_CLASS + " or " + JDK_5_CLASS);

        return fdm;
    }

    /**
     * Returns a class object representing the class with the given name, or
     * <code>null</code> if the specified class cannot be found.
     *
     * @param name a classname
     * @return a class object for the specified class, or <code>null</code>
     */
    private Class getClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Computes the thicknesses of the letters using the given font metrics.
     *
     * @param metrics a set of font metrics
     */
    private void setMetrics(FontMetrics metrics) {
        // create an image that can hold each character
        int mascent = metrics.getMaxAscent();

        int width = metrics.getMaxAdvance();
        int height = mascent + metrics.getMaxDescent();
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

        int size = width * height;

        // initialize the buffer
        Graphics2D g = bi.createGraphics();
        g.setBackground(Color.WHITE);
        g.setColor(Color.BLACK);
        g.setFont(metrics.getFont());

        // compute the thickness of each character
        char[] arr = new char[1];
        int max = 0;

        for (char i = FIRST; i <= LAST; i++) {
            arr[0] = i;

            g.clearRect(0, 0, width, height);
            g.drawChars(arr, 0, 1, 0, mascent);

            int val = getThickness(bi) * 255 / size;
            thicknesses[i - FIRST] = val;

            max = Math.max(max, val);
        }

        // normalize (scale) the thicknesses so the thickest character has a
        // thickness around 255
        for (int i = 0; i < thicknesses.length; i++) {
            int thickness = thicknesses[i];
            thicknesses[i] = Math.min(thickness * 255 / max, 255);
        }
    }

    /**
     * Returns the overall thickness of the given image. This method simply
     * counts the black pixels in the image.
     *
     * @param bi an image
     * @return the overall thickness of the given image
     */
    private int getThickness(BufferedImage bi) {
        int[] rgb = bi.getRGB(0, 0, bi.getWidth(), bi.getHeight(),
            null, 0, bi.getWidth());
        int thickness = 0;
        for (int value : rgb) {
            if ((value & 0xFFFFFF) == 0) thickness++;
        }
        return thickness;
    }

    /**
     * Returns the thickness of the given character, as an opacity value ranging
     * inclusively from <code>0</code> to <code>255</code>.
     *
     * @param ch a character
     * @return the thickness of the given character
     */
    public int getThickness(char ch) {
        // we return 128 if the given character's thickness is unknown, or 0 if
        // the character is an ASCII control character or null
        if (ch < FIRST) return 0;
        else if (ch > LAST) return 128;
        return thicknesses[ch - FIRST];
    }
}
