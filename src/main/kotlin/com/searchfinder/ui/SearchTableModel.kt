package com.searchfinder.ui

import com.searchfinder.model.FileType
import com.searchfinder.model.SearchResult
import javax.swing.table.AbstractTableModel

/**
 * Table model for displaying search results with filtering capabilities.
 * Columns: File Type | File | Line | Match Context
 */
class SearchTableModel : AbstractTableModel() {

    private val columns = arrayOf("Type", "File", "Line", "Match Context")
    private val allData = mutableListOf<SearchResult>()
    private val filteredData = mutableListOf<SearchResult>()

    // Active filters
    private val enabledFileTypes = mutableSetOf<FileType>().apply { addAll(FileType.entries) }
    private var filterText = ""

    override fun getRowCount(): Int = filteredData.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val result = filteredData[rowIndex]
        return when (columnIndex) {
            0 -> result.fileType
            1 -> result.fileName
            2 -> result.lineNumber
            3 -> result.lineText
            else -> ""
        }
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            0 -> FileType::class.java
            2 -> Int::class.javaObjectType
            else -> String::class.java
        }
    }

    fun getResultAt(row: Int): SearchResult? {
        return if (row in filteredData.indices) filteredData[row] else null
    }

    fun setData(data: List<SearchResult>) {
        allData.clear()
        allData.addAll(data)
        applyFilters()
    }

    fun clearData() {
        allData.clear()
        filteredData.clear()
        fireTableDataChanged()
    }

    fun setFilterText(text: String) {
        filterText = text.lowercase()
        applyFilters()
    }

    fun toggleFileType(fileType: FileType, enabled: Boolean) {
        if (enabled) enabledFileTypes.add(fileType) else enabledFileTypes.remove(fileType)
        applyFilters()
    }

    fun isFileTypeEnabled(fileType: FileType): Boolean = fileType in enabledFileTypes

    fun enableAllFileTypes() {
        enabledFileTypes.addAll(FileType.entries)
        applyFilters()
    }

    fun disableAllFileTypes() {
        enabledFileTypes.clear()
        applyFilters()
    }

    private fun applyFilters() {
        filteredData.clear()
        filteredData.addAll(allData.filter { result ->
            val typeMatch = result.fileType in enabledFileTypes
            val textMatch = filterText.isEmpty() ||
                    result.fileName.lowercase().contains(filterText) ||
                    result.lineText.lowercase().contains(filterText) ||
                    result.filePath.lowercase().contains(filterText)
            typeMatch && textMatch
        })
        fireTableDataChanged()
    }

    fun getTotalCount(): Int = allData.size
    fun getFilteredCount(): Int = filteredData.size

    /**
     * Returns a map of FileType -> count for all results (unfiltered).
     */
    fun getFileTypeCounts(): Map<FileType, Int> {
        return allData.groupBy { it.fileType }
            .mapValues { it.value.size }
            .toSortedMap(compareByDescending { allData.count { r -> r.fileType == it } })
    }

    /**
     * Returns top N files by match count.
     */
    fun getTopFileStats(limit: Int = 5): List<Pair<String, Int>> {
        return allData.groupBy { it.fileName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    /**
     * Get all data for CSV export.
     */
    fun getAllFilteredData(): List<SearchResult> = filteredData.toList()
}
