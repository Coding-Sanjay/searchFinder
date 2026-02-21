package com.searchfinder.model

import java.awt.Color

/**
 * Represents a single search match found in the project.
 */
data class SearchResult(
    val filePath: String,
    val fileName: String,
    val lineNumber: Int,
    val lineText: String,
    val matchStartIndex: Int,
    val matchEndIndex: Int,
    val fileType: FileType
)

/**
 * Enum representing supported file types with display labels and UI colors.
 */
enum class FileType(
    val label: String,
    val extensions: List<String>,
    val lightColor: Color,
    val darkColor: Color
) {
    KOTLIN("Kotlin", listOf("kt", "kts"), Color(0x7F52FF), Color(0xA87FFF)),
    JAVA("Java", listOf("java"), Color(0xE76F00), Color(0xF09040)),
    XML("XML", listOf("xml"), Color(0x4CAF50), Color(0x66BB6A)),
    JSON("JSON", listOf("json"), Color(0x2196F3), Color(0x42A5F5)),
    PROPERTIES("Properties", listOf("properties"), Color(0x795548), Color(0x8D6E63)),
    GRADLE("Gradle", listOf("gradle"), Color(0x02303A), Color(0x3D9970)),
    YAML("YAML", listOf("yml", "yaml"), Color(0xCB171E), Color(0xEF5350)),
    TOML("TOML", listOf("toml"), Color(0x9C27B0), Color(0xAB47BC)),
    DART("Dart", listOf("dart"), Color(0x00B4AB), Color(0x40C4BC)),
    SWIFT("Swift", listOf("swift"), Color(0xF05138), Color(0xF27A63)),
    HTML("HTML", listOf("html", "htm"), Color(0xE44D26), Color(0xF06529)),
    CSS("CSS", listOf("css", "scss", "sass", "less"), Color(0x1572B6), Color(0x33A0E0)),
    JS("JavaScript", listOf("js", "jsx", "ts", "tsx"), Color(0xF7DF1E), Color(0xF7DF1E)),
    SQL("SQL", listOf("sql"), Color(0x336791), Color(0x5A9BD5)),
    MARKDOWN("Markdown", listOf("md"), Color(0x607D8B), Color(0x78909C)),
    TEXT("Text", listOf("txt", "log", "csv"), Color(0x9E9E9E), Color(0xBDBDBD)),
    OTHER("Other", listOf(), Color(0x757575), Color(0x9E9E9E));

    companion object {
        fun fromExtension(extension: String): FileType {
            val ext = extension.lowercase()
            return entries.firstOrNull { it.extensions.contains(ext) } ?: OTHER
        }
    }
}
