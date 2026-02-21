package com.searchfinder.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.searchfinder.ui.SearchFinderToolWindowFactory

/**
 * Action registered under Tools menu that opens the Search Finder tool window
 * and focuses the search input field.
 */
class SearchProjectAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Search Finder") ?: return

        toolWindow.show {
            // Focus the search input
            val content = toolWindow.contentManager.getContent(0) ?: return@show
            val panel = content.getUserData(SearchFinderToolWindowFactory.PANEL_KEY) ?: return@show
            panel.focusSearchField()
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
