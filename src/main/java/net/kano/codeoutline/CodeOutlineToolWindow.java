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
 *  File created by keith @ Oct 22, 2003
 *  Modified by John.Koepi
 */

package net.kano.codeoutline;

import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The code outline tool window content panel. As of version 0.0.1, this simply
 * shows another component which renders the code outline; this class's job is
 * only to display the appropriate panel.
 */
public class CodeOutlineToolWindow extends JPanel {
    /**
     * A set of grid bag constraints applied to the currently visible code
     * outline panel.
     */
    private static final GridBagConstraints GBC_DEFAULT
            = new GridBagConstraints(0, 0, 1, 1, 1, 1,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                    new Insets(0, 0, 0, 0), 0, 0);

    private final CodeOutlinePlugin plugin;
    private final Project project;
    private final FileEditorManager fem;

    /** The panel currently being displayed. */
    private volatile CodeOutlinePanel currentPanel = null;

    private Map<FileEditor, CodeOutlinePanel> editor2panel = new IdentityHashMap<FileEditor, CodeOutlinePanel>();
    private Map<VirtualFile, CodeOutlinePanel> file2panel = new IdentityHashMap<VirtualFile, CodeOutlinePanel>();

    private final ToolWindowManagerListener toolWindowManagerListener = new ToolWindowManagerListener() {
        @Override
        public void toolWindowRegistered(@NotNull String id) { }

        @Override
        public void stateChanged() {
            ToolWindowManager twm = ToolWindowManager.getInstance(project);
            String activeToolWindowId = twm.getActiveToolWindowId();
            if (activeToolWindowId != null && activeToolWindowId.equals(CodeOutlinePlugin.TOOLWINDOW_ID)) {
                ToolWindow tw = twm.getToolWindow(activeToolWindowId);
                if (tw.isVisible()) {
                    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                    if (editor != null) {
                        final VirtualFile vFile = ((EditorEx) editor).getVirtualFile();
                        final FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(vFile);
                        CodeOutlinePanel panel = getPanel(fileEditor);
                        if (panel != null && panel != getCurrentPanel()) {
                            final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
                            ((FileEditorManagerEx) fileEditorManager).notifyPublisher(new Runnable() {
                                @Override
                                public void run() {
                                    final FileEditorManagerEvent event = new FileEditorManagerEvent(
                                            fileEditorManager, vFile, fileEditor, vFile, fileEditor);
                                    final FileEditorManagerListener
                                            publisher = fileEditorManager.getProject().getMessageBus().syncPublisher(
                                            FileEditorManagerListener.FILE_EDITOR_MANAGER);
                                    publisher.selectionChanged(event);
                                }
                            });
                        }
                    }
                }
            }
        }
    };

    /**
     * An editor listener for the given project, to create and destroy code
     * outline panels.
     */
    private FileEditorManagerListener editorListener = new FileEditorManagerListener() {
        @Override
        public void fileOpened(FileEditorManager source, VirtualFile file) {
            FileEditor fileEditor = source.getSelectedEditor(file);
            if (fileEditor instanceof TextEditor) {
                CodeOutlinePanel panel = openPanel(fileEditor, file);
                /* Force panel sub-component switch, cause we have no guaranty it will come
                 * after we register new instance.
                 */
                if (checkCurrentPanel(source, file, panel))
                    repaint();
            }
        }

        @Override
        public void fileClosed(FileEditorManager source, VirtualFile file) {
            closePanel(file);
        }

        /**
         * Switch panel if given editor is selected
         */
        private boolean checkCurrentPanel(FileEditorManager source, VirtualFile file, CodeOutlinePanel panel) {
            EditorEx selectedEditorEx = ((EditorEx)source.getSelectedTextEditor());
            if (selectedEditorEx.getVirtualFile() == file && !isAncestorOf(panel)) {
                replacePanel(panel);
                return true;
            }
            
            return false;
        }

        /**
         * This often comes before fileOpened event (or may not).
         * This event comes when current text editor changed. When you click another tab for i.e.
         * This event does not come when selecting last opened tab at project open.
         */
        @Override
        public void selectionChanged(final FileEditorManagerEvent event) {
            CodeOutlinePanel panel = editor2panel.get(event.getNewEditor());
            replacePanel(panel);
            repaint();
        }

        /**
         * Replaces current panel with new panel.
         */
        private void replacePanel(CodeOutlinePanel panel) {
            if (currentPanel != null) {
                remove(currentPanel);
            }

            currentPanel = panel;

            if (panel != null) {
                add(panel, GBC_DEFAULT);
            }
        }
    };

    {
        // we buffer a lot already, so we don't want to be double-buffered
        setDoubleBuffered(false);

        setLayout(new GridBagLayout());
    }

    /**
     * Creates a new code outline tool window for the given project and with the
     * given plugin parent.
     *
     * @param plugin the plugin instance that instantiated this tool window
     * @param project the project for which this tool window is registered
     */
    public CodeOutlineToolWindow(CodeOutlinePlugin plugin, Project project) {
        this.plugin = plugin;
        this.project = project;
        this.fem = FileEditorManager.getInstance(this.project);
        this.fem.addFileEditorManagerListener(editorListener);
        FileDocumentManager docMgr = FileDocumentManager.getInstance();
        for (FileEditor fileEditor : fem.getAllEditors()) {
            if (fileEditor instanceof TextEditor) {
                Editor textEditor = ((TextEditor) fileEditor).getEditor();
                VirtualFile file = docMgr.getFile(textEditor.getDocument());
                if (file != null) {
                    openPanel(fileEditor, file);
                }
            }
        }
    }

    /**
     * Opens a code outline panel for the given file editor and file. The panel
     * is not shown, only created.
     *
     * @param fileEditor a file editor
     * @param file a file
     */
    private synchronized CodeOutlinePanel openPanel(FileEditor fileEditor, VirtualFile file) {
        Editor editor = ((TextEditor) fileEditor).getEditor();
        CodeOutlinePanel panel = new CodeOutlinePanel(plugin, project, editor);

        editor2panel.put(fileEditor, panel);
        file2panel.put(file, panel);

        return panel;
    }

    /**
     * Closes an open code outline panel for the given file, if any. If visible,
     * the associated panel is hidden.
     *
     * @param file a file
     */
    private synchronized void closePanel(VirtualFile file) {
        CodeOutlinePanel panel = file2panel.remove(file);

        if (panel == null) return;

        panel.dispose();
        editor2panel.values().remove(panel);

        if (currentPanel == panel) {
            remove(panel);
            repaint();
        }
    }

    /**
     * Attempts to free all resources and components used by this tool window.
     */
    public synchronized void stop() {
        fem.removeFileEditorManagerListener(editorListener);
        for (CodeOutlinePanel panel : editor2panel.values()) {
            panel.dispose();
        }
        editor2panel.clear();
        file2panel.clear();
    }

    public synchronized CodeOutlinePanel getPanel(FileEditor editor) {
        return editor2panel.get(editor);
    }

    public CodeOutlinePanel getCurrentPanel() {
        return currentPanel;
    }

    /**
     * Returns this tool window's parent code outline plugin instance.
     *
     * @return the parent code outline plugin
     */
    public CodeOutlinePlugin getPlugin() { return plugin; }

    public ToolWindowManagerListener getToolWindowManagerListener() {
        return toolWindowManagerListener;
    }
}
