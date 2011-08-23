Code Outline Plugin for IntelliJ IDEA
=====================================

Description:
------------
Shows a zoomed out "outline" of your code while you're editing it.

Requirements:
-------------
 IntelliJ IDEA 9 (and greater) and JDK6, 7

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
 + (Fixed in 1.0) Plugin does not work when running on Java 6 (JDK 6.0)
 - Exceptions when pointing to not existing place in code
 - Code Outline panel is not always rendered when open file or tab
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

IntelliJ IDEA debug note:
-------------------------
 If you want to debug it in runtime, it's recommended to increase PermGen size for
 your debug project to startup or you are able to get unpredictable jvm experience.
 To deal with it use something like this: -Xms2G -Xmx4G -XX:MaxPermSize=2G

 To run IDEA in the sandbox use:
 /usr/java/default/bin/java -agentlib:jdwp=transport=dt_socket,address=127.0.0.1:49670,suspend=y,server=n
 -Xms2G -Xmx4G -XX:MaxPermSize=2G -Xbootclasspath/a:~/idea-com/idea-IC-108.SNAPSHOT/lib/boot.jar
 -Didea.config.path=~/.IdeaIC11/system/plugins-sandbox/config
 -Didea.system.path=~/.IdeaIC11/system/plugins-sandbox/system
 -Didea.plugins.path=~/.IdeaIC11/system/plugins-sandbox/plugins
 -Didea.platform.prefix=Idea
 -Dfile.encoding=UTF-8
 -classpath /usr/java/default/lib/tools.jar:~/idea-com/idea-IC-108.SNAPSHOT/lib/idea_rt.jar:
 ~/idea-com/idea-IC-108.SNAPSHOT/lib/idea.jar:~/idea-com/idea-IC-108.SNAPSHOT/lib/bootstrap.jar:
 ~/idea-com/idea-IC-108.SNAPSHOT/lib/extensions.jar:~/idea-com/idea-IC-108.SNAPSHOT/lib/util.jar:
 ~/idea-com/idea-IC-108.SNAPSHOT/lib/openapi.jar:~/idea-com/idea-IC-108.SNAPSHOT/lib/trove4j.jar:
 ~/idea-com/idea-IC-108.SNAPSHOT/lib/jdom.jar:~/idea-com/idea-IC-108.SNAPSHOT/lib/log4j.jar
 com.intellij.idea.Main

 Also com.intellij.rt.execution.application.AppMain can be used.

Author of original project:
---------------------------
This is the fork of original [code-outline][] plugin that is not supported since 2005 year.

 [code-outline]: http://plugins.intellij.net/plugin/?idea&id=160
