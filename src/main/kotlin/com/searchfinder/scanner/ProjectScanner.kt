package com.searchfinder.scanner

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.searchfinder.model.FileType
import com.searchfinder.model.SearchResult
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Scans the entire project for occurrences of a search key.
 * Works on all text-based files regardless of naming conventions,
 * so you can find BottomSheet, AlertDialog, Snackbar, etc. even
 * in files named "Helper.kt" or "Utils.java".
 */
object ProjectScanner {

    // File extensions to scan (covers all common Android/web/server project files)
    private val SCANNABLE_EXTENSIONS = setOf(
        "kt", "kts", "java", "xml", "json", "properties", "gradle",
        "yml", "yaml", "toml", "dart", "swift", "html", "htm",
        "css", "scss", "sass", "less", "js", "jsx", "ts", "tsx",
        "sql", "md", "txt", "log", "csv", "cfg", "ini", "conf",
        "sh", "bat", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
        "m", "mm", "plist", "strings", "storyboard", "xib", "pro"
    )

    // Directories to skip entirely
    private val SKIP_DIRS = setOf(
        "build", ".gradle", ".idea", "node_modules", ".git",
        "__pycache__", ".dart_tool", ".pub-cache", "Pods",
        ".svn", ".hg", "dist", "out", "target"
    )

    // Maximum file size to scan (5MB) — skip huge generated files
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024L

    /**
     * Scan the project for all occurrences of [searchKey].
     *
     * @param project          The IntelliJ project
     * @param searchKey        The string to search for
     * @param caseSensitive    Whether the search is case-sensitive
     * @param useRegex         Whether to treat searchKey as a regex pattern
     * @param wholeWord        Whether to match whole words only
     * @return List of all matching SearchResult objects
     */
    fun scan(
        project: Project,
        searchKey: String,
        caseSensitive: Boolean = false,
        useRegex: Boolean = false,
        wholeWord: Boolean = false
    ): List<SearchResult> {
        if (searchKey.isBlank()) return emptyList()

        val results = mutableListOf<SearchResult>()
        val basePath = project.basePath ?: return results

        // Build the regex/pattern for matching
        val pattern = buildPattern(searchKey, caseSensitive, useRegex, wholeWord)

        // Walk all content roots
        val contentRoots = ProjectRootManager.getInstance(project).contentRoots
        if (contentRoots.isEmpty()) {
            // Fallback: use project base directory
            val baseDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath(basePath)
            if (baseDir != null) {
                scanDirectory(baseDir, pattern, results)
            }
        } else {
            for (root in contentRoots) {
                scanDirectory(root, pattern, results)
            }
        }

        return results
    }

    private fun scanDirectory(
        root: VirtualFile,
        pattern: Regex,
        results: MutableList<SearchResult>
    ) {
        VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                // Skip excluded directories
                if (file.isDirectory) {
                    return file.name !in SKIP_DIRS
                }

                // Skip non-text / unsupported files
                val ext = file.extension?.lowercase() ?: return true
                if (ext !in SCANNABLE_EXTENSIONS) return true

                // Skip huge files
                if (file.length > MAX_FILE_SIZE) return true

                // Scan the file
                scanFile(file, ext, pattern, results)
                return true
            }
        })
    }

    private fun scanFile(
        file: VirtualFile,
        extension: String,
        pattern: Regex,
        results: MutableList<SearchResult>
    ) {
        try {
            val fileType = FileType.fromExtension(extension)
            val reader = BufferedReader(InputStreamReader(file.inputStream, file.charset))
            reader.use { br ->
                var lineNumber = 0
                br.forEachLine { line ->
                    lineNumber++
                    val matches = pattern.findAll(line)
                    for (match in matches) {
                        results.add(
                            SearchResult(
                                filePath = file.path,
                                fileName = file.name,
                                lineNumber = lineNumber,
                                lineText = line.trimEnd(),
                                matchStartIndex = match.range.first,
                                matchEndIndex = match.range.last + 1,
                                fileType = fileType
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Skip files that can't be read (binary, locked, etc.)
        }
    }

    private fun buildPattern(
        searchKey: String,
        caseSensitive: Boolean,
        useRegex: Boolean,
        wholeWord: Boolean
    ): Regex {
        val base = if (useRegex) searchKey else Regex.escape(searchKey)
        val withBoundary = if (wholeWord) "\\b$base\\b" else base
        val options = if (caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE)
        return Regex(withBoundary, options)
    }
}
