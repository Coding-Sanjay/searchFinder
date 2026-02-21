package com.searchfinder.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory that creates the "Search Finder" tool window in the IDE.
 */
class SearchFinderToolWindowFactory : ToolWindowFactory {

    companion object {
        val PANEL_KEY = Key.create<SearchFinderPanel>("SearchFinderPanel")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = SearchFinderPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.putUserData(PANEL_KEY, panel)
        toolWindow.contentManager.addContent(content)
    }
}
