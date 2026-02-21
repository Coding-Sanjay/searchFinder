package com.searchfinder.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.searchfinder.model.FileType
import com.searchfinder.model.SearchResult
import com.searchfinder.scanner.ProjectScanner
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.*
import java.io.File
import java.io.FileWriter
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * Main UI panel for the Search Finder plugin.
 *
 * Layout:
 * ┌──────────────────────────────────────────┐
 * │  🔍 Search Finder         [Stats] [CSV]  │  ← Header
 * ├──────────────────────────────────────────┤
 * │  [Search field] [Options] [▶ Search]     │  ← Search bar
 * ├──────────────────────────────────────────┤
 * │  📊 Stats panel (collapsible)            │  ← Stats
 * ├──────────────────────────────────────────┤
 * │  Type │ File │ Line │ Match Context      │  ← Results
 * │  KT   │ X.kt │ 42   │ BottomSheet...    │
 * │  JAVA │ Y.jv │ 15   │ AlertDialog...    │
 * ├──────────────────────────────────────────┤
 * │  [✓KT] [✓Java] [✓XML]  Filter: [___]   │  ← Filters
 * ├──────────────────────────────────────────┤
 * │  Showing 42 of 128 results              │  ← Footer
 * └──────────────────────────────────────────┘
 */
class SearchFinderPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val tableModel = SearchTableModel()
    private val table = JBTable(tableModel)
    private val searchField = JTextField(30)
    private val caseSensitiveCheck = JCheckBox("Aa", false)
    private val regexCheck = JCheckBox(".*", false)
    private val wholeWordCheck = JCheckBox("W", false)
    private val summaryLabel = JLabel("Ready to search")
    private val statsPanel = JPanel()
    private var statsVisible = false

    // Colors
    private val bgPrimary = JBColor(Color(0xFAFAFA), Color(0x2B2B2B))
    private val bgSecondary = JBColor(Color(0xFFFFFF), Color(0x313335))
    private val bgHeader = JBColor(Color(0xF0F4FF), Color(0x1E2A3A))
    private val accentColor = JBColor(Color(0x4A90D9), Color(0x5B9FE8))
    private val accentHover = JBColor(Color(0x3A7BC8), Color(0x6BB0F9))
    private val textPrimary = JBColor(Color(0x1A1A2E), Color(0xE0E0E0))
    private val textSecondary = JBColor(Color(0x666680), Color(0xA0A0B0))
    private val borderColor = JBColor(Color(0xE0E4F0), Color(0x3C3F41))
    private val stripeColor = JBColor(Color(0xF5F7FF), Color(0x2F3133))

    init {
        background = bgPrimary
        border = EmptyBorder(0, 0, 0, 0)

        add(createHeaderPanel(), BorderLayout.NORTH)
        add(createCenterPanel(), BorderLayout.CENTER)
        add(createFooterPanel(), BorderLayout.SOUTH)

        setupTable()
        setupKeyboardShortcut()
    }

    // ──────────────────────────────────────────────────────────────
    // Header: Title + Search Bar + Action Buttons
    // ──────────────────────────────────────────────────────────────

    private fun createHeaderPanel(): JPanel {
        val header = JPanel(BorderLayout())
        header.background = bgHeader
        header.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor),
            EmptyBorder(10, 16, 10, 16)
        )

        // Top row: Title + action buttons
        val topRow = JPanel(BorderLayout())
        topRow.isOpaque = false

        val titleLabel = JLabel("🔍 Search Finder")
        titleLabel.font = Font("Segoe UI", Font.BOLD, 16)
        titleLabel.foreground = textPrimary
        topRow.add(titleLabel, BorderLayout.WEST)

        val actionButtons = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))
        actionButtons.isOpaque = false

        val statsButton = createStyledButton("📊 Stats")
        statsButton.addActionListener { toggleStats() }
        actionButtons.add(statsButton)

        val csvButton = createStyledButton("📁 Export CSV")
        csvButton.addActionListener { exportCsv() }
        actionButtons.add(csvButton)

        topRow.add(actionButtons, BorderLayout.EAST)

        // Bottom row: Search bar
        val searchRow = JPanel(BorderLayout(8, 0))
        searchRow.isOpaque = false
        searchRow.border = EmptyBorder(10, 0, 0, 0)

        // Search field styling
        searchField.font = Font("JetBrains Mono", Font.PLAIN, 14)
        searchField.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1, true),
            EmptyBorder(8, 12, 8, 12)
        )
        searchField.toolTipText = "Type a key to search (e.g. BottomSheet, AlertDialog, api_key)"

        // Enter key triggers search
        searchField.addActionListener { runSearch() }

        // Options panel
        val optionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        optionsPanel.isOpaque = false

        caseSensitiveCheck.toolTipText = "Case Sensitive"
        caseSensitiveCheck.font = Font("JetBrains Mono", Font.BOLD, 11)
        caseSensitiveCheck.isOpaque = false
        caseSensitiveCheck.foreground = textSecondary

        regexCheck.toolTipText = "Regular Expression"
        regexCheck.font = Font("JetBrains Mono", Font.BOLD, 11)
        regexCheck.isOpaque = false
        regexCheck.foreground = textSecondary

        wholeWordCheck.toolTipText = "Whole Word"
        wholeWordCheck.font = Font("JetBrains Mono", Font.BOLD, 11)
        wholeWordCheck.isOpaque = false
        wholeWordCheck.foreground = textSecondary

        optionsPanel.add(caseSensitiveCheck)
        optionsPanel.add(regexCheck)
        optionsPanel.add(wholeWordCheck)

        val searchButton = createAccentButton("▶  Search")
        searchButton.addActionListener { runSearch() }

        searchRow.add(searchField, BorderLayout.CENTER)
        searchRow.add(optionsPanel, BorderLayout.WEST)
        searchRow.add(searchButton, BorderLayout.EAST)

        val headerContent = JPanel(BorderLayout())
        headerContent.isOpaque = false
        headerContent.add(topRow, BorderLayout.NORTH)
        headerContent.add(searchRow, BorderLayout.SOUTH)

        header.add(headerContent, BorderLayout.CENTER)
        return header
    }

    // ──────────────────────────────────────────────────────────────
    // Center: Stats Panel (collapsible) + Results Table
    // ──────────────────────────────────────────────────────────────

    private fun createCenterPanel(): JPanel {
        val center = JPanel(BorderLayout())
        center.background = bgPrimary

        // Stats panel (hidden by default)
        statsPanel.layout = BoxLayout(statsPanel, BoxLayout.Y_AXIS)
        statsPanel.background = bgSecondary
        statsPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor),
            EmptyBorder(12, 16, 12, 16)
        )
        statsPanel.isVisible = false

        // Table in scroll pane
        val scrollPane = JBScrollPane(table)
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.background = bgPrimary

        // Filter bar
        val filterBar = createFilterBar()

        center.add(statsPanel, BorderLayout.NORTH)
        center.add(scrollPane, BorderLayout.CENTER)
        center.add(filterBar, BorderLayout.SOUTH)

        return center
    }

    private fun createFilterBar(): JPanel {
        val filterPanel = JPanel(BorderLayout(8, 0))
        filterPanel.background = bgSecondary
        filterPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor),
            EmptyBorder(8, 16, 8, 16)
        )

        // File type checkboxes (scrollable if many)
        val typesPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        typesPanel.isOpaque = false

        val allButton = JButton("All")
        allButton.font = Font("Segoe UI", Font.PLAIN, 11)
        allButton.isFocusPainted = false
        allButton.addActionListener {
            tableModel.enableAllFileTypes()
            refreshFilterCheckboxes(typesPanel)
        }
        typesPanel.add(allButton)

        val noneButton = JButton("None")
        noneButton.font = Font("Segoe UI", Font.PLAIN, 11)
        noneButton.isFocusPainted = false
        noneButton.addActionListener {
            tableModel.disableAllFileTypes()
            refreshFilterCheckboxes(typesPanel)
        }
        typesPanel.add(noneButton)

        typesPanel.add(JSeparator(SwingConstants.VERTICAL).apply { preferredSize = Dimension(1, 20) })

        for (ft in FileType.entries) {
            if (ft == FileType.OTHER) continue
            val cb = JCheckBox(ft.label, true)
            cb.font = Font("Segoe UI", Font.PLAIN, 11)
            cb.foreground = JBColor(ft.lightColor, ft.darkColor)
            cb.isOpaque = false
            cb.putClientProperty("fileType", ft)
            cb.addActionListener {
                tableModel.toggleFileType(ft, cb.isSelected)
                updateSummary()
            }
            typesPanel.add(cb)
        }

        val typesScroll = JBScrollPane(typesPanel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)
        typesScroll.border = BorderFactory.createEmptyBorder()
        typesScroll.isOpaque = false
        typesScroll.preferredSize = Dimension(0, 32)

        // Live filter text field
        val liveFilter = JTextField(15)
        liveFilter.font = Font("JetBrains Mono", Font.PLAIN, 12)
        liveFilter.toolTipText = "Filter results by file name or content..."
        liveFilter.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1, true),
            EmptyBorder(4, 8, 4, 8)
        )
        liveFilter.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent) {
                tableModel.setFilterText(liveFilter.text)
                updateSummary()
            }
        })

        val filterRight = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        filterRight.isOpaque = false
        filterRight.add(JLabel("Filter:").apply {
            font = Font("Segoe UI", Font.PLAIN, 12)
            foreground = textSecondary
        })
        filterRight.add(liveFilter)

        filterPanel.add(typesScroll, BorderLayout.CENTER)
        filterPanel.add(filterRight, BorderLayout.EAST)

        return filterPanel
    }

    private fun refreshFilterCheckboxes(typesPanel: JPanel) {
        for (comp in typesPanel.components) {
            if (comp is JCheckBox) {
                val ft = comp.getClientProperty("fileType") as? FileType ?: continue
                comp.isSelected = tableModel.isFileTypeEnabled(ft)
            }
        }
        updateSummary()
    }

    // ──────────────────────────────────────────────────────────────
    // Footer: Summary Label
    // ──────────────────────────────────────────────────────────────

    private fun createFooterPanel(): JPanel {
        val footer = JPanel(BorderLayout())
        footer.background = bgSecondary
        footer.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor),
            EmptyBorder(8, 16, 8, 16)
        )

        summaryLabel.font = Font("Segoe UI", Font.ITALIC, 12)
        summaryLabel.foreground = textSecondary

        val typeBadges = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        typeBadges.isOpaque = false

        footer.add(summaryLabel, BorderLayout.WEST)
        footer.add(typeBadges, BorderLayout.EAST)

        return footer
    }

    // ──────────────────────────────────────────────────────────────
    // Table Setup & Renderers
    // ──────────────────────────────────────────────────────────────

    private fun setupTable() {
        table.fillsViewportHeight = true
        table.rowHeight = 32
        table.setShowGrid(false)
        table.intercellSpacing = Dimension(0, 0)
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        table.font = Font("JetBrains Mono", Font.PLAIN, 13)
        table.background = bgPrimary

        // Column widths
        table.columnModel.getColumn(0).preferredWidth = 80   // Type
        table.columnModel.getColumn(0).maxWidth = 100
        table.columnModel.getColumn(1).preferredWidth = 200  // File
        table.columnModel.getColumn(2).preferredWidth = 60   // Line
        table.columnModel.getColumn(2).maxWidth = 80
        table.columnModel.getColumn(3).preferredWidth = 500  // Match Context

        // Custom renderers
        table.columnModel.getColumn(0).cellRenderer = FileTypeBadgeRenderer()
        table.columnModel.getColumn(1).cellRenderer = StripedRenderer(Font("JetBrains Mono", Font.PLAIN, 13))
        table.columnModel.getColumn(2).cellRenderer = LineNumberRenderer()
        table.columnModel.getColumn(3).cellRenderer = MatchContextRenderer()

        // Table header styling
        table.tableHeader.font = Font("Segoe UI", Font.BOLD, 12)
        table.tableHeader.background = bgSecondary
        table.tableHeader.foreground = textPrimary

        // Double-click to navigate
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    navigateToSelected()
                }
            }

            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val row = table.rowAtPoint(e.point)
                    if (row >= 0) {
                        table.setRowSelectionInterval(row, row)
                        showContextMenu(e)
                    }
                }
            }
        })
    }

    private fun setupKeyboardShortcut() {
        val enterAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                navigateToSelected()
            }
        }
        table.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "navigate")
        table.actionMap.put("navigate", enterAction)
    }

    // ──────────────────────────────────────────────────────────────
    // Cell Renderers
    // ──────────────────────────────────────────────────────────────

    /**
     * Renders a colored badge for the file type (e.g. [KT] in purple, [JAVA] in orange).
     */
    private inner class FileTypeBadgeRenderer : JPanel(BorderLayout()), TableCellRenderer {
        private val label = JLabel()

        init {
            isOpaque = true
            border = EmptyBorder(4, 8, 4, 8)
            add(label, BorderLayout.CENTER)
        }

        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean,
            hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val fileType = value as? FileType ?: FileType.OTHER
            label.text = fileType.label
            label.font = Font("Segoe UI", Font.BOLD, 11)
            label.foreground = JBColor(fileType.lightColor, fileType.darkColor)
            label.horizontalAlignment = SwingConstants.CENTER

            background = if (isSelected) {
                table.selectionBackground
            } else if (row % 2 == 1) {
                stripeColor
            } else {
                bgPrimary
            }

            return this
        }
    }

    /**
     * Renderer for line numbers with monospace font and muted color.
     */
    private inner class LineNumberRenderer : DefaultTableCellRenderer() {
        init {
            horizontalAlignment = CENTER
            font = Font("JetBrains Mono", Font.PLAIN, 12)
        }

        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean,
            hasFocus: Boolean, row: Int, column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            foreground = if (isSelected) table.selectionForeground else accentColor
            background = if (isSelected) table.selectionBackground
            else if (row % 2 == 1) stripeColor else bgPrimary
            border = EmptyBorder(4, 8, 4, 8)
            return this
        }
    }

    /**
     * Renderer with alternating row stripes.
     */
    private inner class StripedRenderer(private val customFont: Font) : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean,
            hasFocus: Boolean, row: Int, column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            font = customFont
            foreground = if (isSelected) table.selectionForeground else textPrimary
            background = if (isSelected) table.selectionBackground
            else if (row % 2 == 1) stripeColor else bgPrimary
            border = EmptyBorder(4, 8, 4, 8)
            return this
        }
    }

    /**
     * Renders the match context with the search key highlighted.
     */
    private inner class MatchContextRenderer : JPanel(BorderLayout()), TableCellRenderer {
        private val label = JLabel()

        init {
            isOpaque = true
            border = EmptyBorder(4, 8, 4, 8)
            add(label, BorderLayout.CENTER)
        }

        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean,
            hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val text = (value as? String)?.trim() ?: ""
            val result = tableModel.getResultAt(row)

            if (result != null && result.matchStartIndex >= 0 && result.matchEndIndex <= text.length) {
                // Show HTML with highlighted match
                val before = escapeHtml(text.substring(0, result.matchStartIndex))
                val match = escapeHtml(text.substring(result.matchStartIndex, result.matchEndIndex))
                val after = escapeHtml(text.substring(result.matchEndIndex))
                val highlightColor = if (isSelected) "#FFFFFF" else "#FF6B00"
                val highlightBg = if (isSelected) "#3060A0" else "#FFF3E0"
                label.text = "<html><span style='font-family:JetBrains Mono;font-size:11px;'>" +
                        "$before<span style='color:$highlightColor;background-color:$highlightBg;font-weight:bold;'>" +
                        "$match</span>$after</span></html>"
            } else {
                label.text = "<html><span style='font-family:JetBrains Mono;font-size:11px;'>" +
                        "${escapeHtml(text)}</span></html>"
            }

            background = if (isSelected) {
                table.selectionBackground
            } else if (row % 2 == 1) {
                stripeColor
            } else {
                bgPrimary
            }

            return this
        }

        private fun escapeHtml(s: String): String {
            return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Search Execution
    // ──────────────────────────────────────────────────────────────

    fun runSearch() {
        val key = searchField.text.trim()
        if (key.isEmpty()) {
            summaryLabel.text = "⚠ Please enter a search key"
            return
        }

        summaryLabel.text = "🔍 Searching for \"$key\"..."

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Searching for \"$key\"...", true) {
            var results: List<SearchResult> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Scanning project files..."
                results = ProjectScanner.scan(
                    project,
                    key,
                    caseSensitiveCheck.isSelected,
                    regexCheck.isSelected,
                    wholeWordCheck.isSelected
                )
            }

            override fun onSuccess() {
                tableModel.setData(results)
                updateSummary()
                updateStatsPanel()
            }

            override fun onThrowable(error: Throwable) {
                summaryLabel.text = "❌ Search failed: ${error.message}"
            }
        })
    }

    fun focusSearchField() {
        searchField.requestFocusInWindow()
        searchField.selectAll()
    }

    // ──────────────────────────────────────────────────────────────
    // Navigation
    // ──────────────────────────────────────────────────────────────

    private fun navigateToSelected() {
        val row = table.selectedRow
        if (row < 0) return
        val result = tableModel.getResultAt(row) ?: return
        navigateTo(result)
    }

    private fun navigateTo(result: SearchResult) {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(result.filePath) ?: return
        ApplicationManager.getApplication().invokeLater {
            val descriptor = OpenFileDescriptor(project, virtualFile, result.lineNumber - 1, 0)
            FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Context Menu
    // ──────────────────────────────────────────────────────────────

    private fun showContextMenu(e: MouseEvent) {
        val row = table.selectedRow
        if (row < 0) return
        val result = tableModel.getResultAt(row) ?: return

        val menu = JPopupMenu()

        val navigateItem = JMenuItem("🔗 Navigate to Line")
        navigateItem.font = Font("Segoe UI", Font.PLAIN, 13)
        navigateItem.addActionListener { navigateTo(result) }
        menu.add(navigateItem)

        menu.addSeparator()

        val copyLineItem = JMenuItem("📋 Copy Line Text")
        copyLineItem.font = Font("Segoe UI", Font.PLAIN, 13)
        copyLineItem.addActionListener { copyToClipboard(result.lineText) }
        menu.add(copyLineItem)

        val copyPathItem = JMenuItem("📂 Copy File Path")
        copyPathItem.font = Font("Segoe UI", Font.PLAIN, 13)
        copyPathItem.addActionListener { copyToClipboard(result.filePath) }
        menu.add(copyPathItem)

        val copyRefItem = JMenuItem("📌 Copy Reference (file:line)")
        copyRefItem.font = Font("Segoe UI", Font.PLAIN, 13)
        copyRefItem.addActionListener { copyToClipboard("${result.filePath}:${result.lineNumber}") }
        menu.add(copyRefItem)

        menu.show(table, e.x, e.y)
    }

    private fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    // ──────────────────────────────────────────────────────────────
    // Stats Panel
    // ──────────────────────────────────────────────────────────────

    private fun toggleStats() {
        statsVisible = !statsVisible
        statsPanel.isVisible = statsVisible
        if (statsVisible) updateStatsPanel()
    }

    private fun updateStatsPanel() {
        statsPanel.removeAll()

        val typeCounts = tableModel.getFileTypeCounts()
        if (typeCounts.isEmpty()) {
            statsPanel.add(JLabel("No results to analyze").apply {
                font = Font("Segoe UI", Font.ITALIC, 13)
                foreground = textSecondary
                alignmentX = LEFT_ALIGNMENT
            })
        } else {
            // File type breakdown
            val typeTitle = JLabel("📊 Results by File Type")
            typeTitle.font = Font("Segoe UI", Font.BOLD, 13)
            typeTitle.foreground = textPrimary
            typeTitle.alignmentX = LEFT_ALIGNMENT
            statsPanel.add(typeTitle)
            statsPanel.add(Box.createVerticalStrut(8))

            val maxCount = typeCounts.values.maxOrNull() ?: 1
            for ((ft, count) in typeCounts) {
                val row = JPanel(BorderLayout(8, 0))
                row.isOpaque = false
                row.maximumSize = Dimension(Int.MAX_VALUE, 24)
                row.alignmentX = LEFT_ALIGNMENT

                val label = JLabel("${ft.label} ($count)")
                label.font = Font("Segoe UI", Font.PLAIN, 12)
                label.foreground = JBColor(ft.lightColor, ft.darkColor)
                label.preferredSize = Dimension(140, 20)
                row.add(label, BorderLayout.WEST)

                val bar = FileTypeBar(ft, count.toFloat() / maxCount)
                row.add(bar, BorderLayout.CENTER)

                statsPanel.add(row)
                statsPanel.add(Box.createVerticalStrut(3))
            }

            // Top files
            statsPanel.add(Box.createVerticalStrut(12))
            val fileTitle = JLabel("📁 Top Files by Match Count")
            fileTitle.font = Font("Segoe UI", Font.BOLD, 13)
            fileTitle.foreground = textPrimary
            fileTitle.alignmentX = LEFT_ALIGNMENT
            statsPanel.add(fileTitle)
            statsPanel.add(Box.createVerticalStrut(6))

            for ((fileName, count) in tableModel.getTopFileStats(5)) {
                val label = JLabel("  $fileName — $count matches")
                label.font = Font("JetBrains Mono", Font.PLAIN, 12)
                label.foreground = textSecondary
                label.alignmentX = LEFT_ALIGNMENT
                statsPanel.add(label)
            }
        }

        statsPanel.revalidate()
        statsPanel.repaint()
    }

    /**
     * A horizontal bar representing the proportion for a file type in stats.
     */
    private inner class FileTypeBar(private val fileType: FileType, private val ratio: Float) : JPanel() {
        init {
            isOpaque = false
            preferredSize = Dimension(200, 18)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Background track
            g2.color = JBColor(Color(0xE8ECF0), Color(0x3C3F41))
            g2.fillRoundRect(0, 2, width, height - 4, 8, 8)

            // Filled portion
            val fillWidth = (width * ratio).toInt().coerceAtLeast(4)
            g2.color = JBColor(fileType.lightColor, fileType.darkColor)
            g2.fillRoundRect(0, 2, fillWidth, height - 4, 8, 8)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CSV Export
    // ──────────────────────────────────────────────────────────────

    private fun exportCsv() {
        val data = tableModel.getAllFilteredData()
        if (data.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No results to export.", "Export CSV", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val chooser = JFileChooser()
        chooser.selectedFile = File("search_results.csv")
        val result = chooser.showSaveDialog(this)
        if (result != JFileChooser.APPROVE_OPTION) return

        try {
            FileWriter(chooser.selectedFile).use { writer ->
                writer.write("Type,File,Path,Line,Match Context\n")
                for (r in data) {
                    writer.write("${csvEscape(r.fileType.label)},${csvEscape(r.fileName)},${csvEscape(r.filePath)},${r.lineNumber},${csvEscape(r.lineText)}\n")
                }
            }
            JOptionPane.showMessageDialog(this, "Exported ${data.size} results to ${chooser.selectedFile.name}",
                "Export CSV", JOptionPane.INFORMATION_MESSAGE)
        } catch (ex: Exception) {
            JOptionPane.showMessageDialog(this, "Export failed: ${ex.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun csvEscape(s: String): String {
        return if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            "\"${s.replace("\"", "\"\"")}\""
        } else s
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private fun updateSummary() {
        val filtered = tableModel.getFilteredCount()
        val total = tableModel.getTotalCount()
        summaryLabel.text = if (total == 0) {
            "No results found"
        } else if (filtered == total) {
            "✅ Found $total matches"
        } else {
            "🔎 Showing $filtered of $total matches"
        }
    }

    private fun createStyledButton(text: String): JButton {
        val button = JButton(text)
        button.font = Font("Segoe UI", Font.PLAIN, 12)
        button.isFocusPainted = false
        button.isContentAreaFilled = false
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1, true),
            EmptyBorder(6, 14, 6, 14)
        )
        button.foreground = textPrimary
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                button.isContentAreaFilled = true
                button.background = JBColor(Color(0xE8ECF0), Color(0x3C3F41))
            }
            override fun mouseExited(e: MouseEvent) {
                button.isContentAreaFilled = false
            }
        })

        return button
    }

    private fun createAccentButton(text: String): JButton {
        val button = JButton(text)
        button.font = Font("Segoe UI", Font.BOLD, 13)
        button.isFocusPainted = false
        button.foreground = Color.WHITE
        button.background = accentColor
        button.isOpaque = true
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accentColor, 1, true),
            EmptyBorder(8, 20, 8, 20)
        )
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                button.background = accentHover
                button.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(accentHover, 1, true),
                    EmptyBorder(8, 20, 8, 20)
                )
            }
            override fun mouseExited(e: MouseEvent) {
                button.background = accentColor
                button.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(accentColor, 1, true),
                    EmptyBorder(8, 20, 8, 20)
                )
            }
        })

        return button
    }
}
