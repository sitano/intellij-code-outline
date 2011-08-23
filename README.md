Code Outline Plugin
===================

Description:
------------
Shows a zoomed out "outline" of your code while you're editing it.

General usage instructions:
---------------------------
 * Left mouse button: Go to position in file (do not expand fold region)
 * Double-click: Go to position in file, expand fold region if necessary
 * Ctrl+Left button: Go to position in file, no smooth scroll
 * Shift+Left button + drag: Select text
 * Middle mouse button + drag: "Preview scroll" to position in file (snaps back to original position when you release the mouse button)
 * Ctrl+Middle button + drag: "Preview scroll" to position, no smooth scroll
 * Right mouse button: Popup menu / preferences
 * Mouse wheel: Scroll page up/down

Known bugs:
-----------
 - Scale outline when it's too tall to fit
 - Plugin not deal well with deleting large amounts of text when file is too large
 - Rotate or wrap outline when toolwindow is moved to top or bottom
 - Use user's color settings for selection, background, etc
 - Problems when closing tab groups
 - Visible region draws incorrectly when first and last line visible in editor are both folded
 - Does not draw column-mode (rectangular) selection correctly
 - Hide folded code
 - Highlight highlighted regions
 - Show syntax coloring in outline
 - Plugin does not work when running on Java 6 (JDK 6.0)

Author of original project:
---------------------------
 Vendor: Keith Lea
 Email: keith at kano.net
 Based at http://plugins.intellij.net/plugin/?idea&id=160
 Bugtracker page: http://code.google.com/p/intellij-code-outline/issues/list
