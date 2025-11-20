package app.sagen.runcontrol.service

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Service for accessing logs from running configurations and log files.
 */
@Service(Service.Level.PROJECT)
class LogService(
    private val project: Project,
) {
    data class LogSource(
        val name: String,
        val type: String, // "console", "file", "stdout", "stderr"
        val path: String?, // File path if type="file"
        val size: Long?, // Size in bytes if available
        val lines: Long?, // Line count if available (may be expensive to compute)
    )

    data class LogContent(
        val source: String,
        val totalLines: Long,
        val offset: Long,
        val limit: Long,
        val lines: List<String>,
        val hasMore: Boolean,
    )

    data class SearchResult(
        val source: String,
        val line: Long,
        val content: String,
        val context: List<String>? = null, // Lines before/after for context
    )

    /**
     * Lists all available log sources for a run configuration.
     */
    fun listLogSources(configName: String): List<LogSource> {
        val sources = mutableListOf<LogSource>()
        val tracker = project.service<RunningProcessTracker>()

        // 1. Check for console output from running process
        val process = tracker.getRunningProcess(configName)

        if (process != null) {
            sources.add(
                LogSource(
                    name = "console",
                    type = "console",
                    path = null,
                    size = null,
                    lines = null,
                ),
            )
        }

        // 2. Check for common log file locations
        val projectPath = project.basePath ?: return sources
        val commonLogDirs =
            listOf(
                File(projectPath, "logs"),
                File(projectPath, "log"),
                File(projectPath, "target/logs"),
                File(projectPath, "build/logs"),
                File(projectPath, ".idea/logs"),
                File(System.getProperty("user.home"), ".IntelliJIdea*/system/log"),
            )

        for (logDir in commonLogDirs) {
            if (logDir.exists() && logDir.isDirectory) {
                logDir
                    .listFiles { file ->
                        file.isFile && (file.extension == "log" || file.extension == "txt")
                    }?.forEach { logFile ->
                        sources.add(
                            LogSource(
                                name = logFile.name,
                                type = "file",
                                path = logFile.absolutePath,
                                size = logFile.length(),
                                lines = null, // Computing line count would be expensive
                            ),
                        )
                    }
            }
        }

        return sources
    }

    /**
     * Reads console output from a running configuration.
     * Note: Console access uses reflection as there's no stable public API.
     */
    fun readConsoleOutput(
        configName: String,
        offset: Long = 0,
        limit: Long = 100,
    ): LogContent? {
        val tracker = project.service<RunningProcessTracker>()
        val process = tracker.getRunningProcess(configName) ?: return null

        // Get console from environment (public API)
        val consoleView = process.environment.contentToReuse?.executionConsole as? ConsoleView
            ?: return null

        val text =
            try {
                getConsoleText(consoleView)
            } catch (e: Exception) {
                "Error reading console: ${e.message}"
            }

        val lines = text.lines()
        val totalLines = lines.size.toLong()
        val startIndex = offset.toInt().coerceIn(0, lines.size)
        val endIndex = (offset + limit).toInt().coerceIn(0, lines.size)
        val selectedLines = lines.subList(startIndex, endIndex)

        return LogContent(
            source = "console",
            totalLines = totalLines,
            offset = offset,
            limit = limit,
            lines = selectedLines,
            hasMore = endIndex < lines.size,
        )
    }

    /**
     * Reads log file content with pagination.
     */
    fun readLogFile(
        filePath: String,
        offset: Long = 0,
        limit: Long = 100,
    ): LogContent {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return LogContent(
                source = file.name,
                totalLines = 0,
                offset = 0,
                limit = 0,
                lines = emptyList(),
                hasMore = false,
            )
        }

        val lines = file.readLines()
        val totalLines = lines.size.toLong()
        val startIndex = offset.toInt().coerceIn(0, lines.size)
        val endIndex = (offset + limit).toInt().coerceIn(0, lines.size)
        val selectedLines = lines.subList(startIndex, endIndex)

        return LogContent(
            source = file.name,
            totalLines = totalLines,
            offset = offset,
            limit = limit,
            lines = selectedLines,
            hasMore = endIndex < lines.size,
        )
    }

    /**
     * Searches for a pattern in console output.
     */
    fun searchConsole(
        configName: String,
        query: String,
        caseSensitive: Boolean = false,
        maxResults: Int = 100,
    ): List<SearchResult> {
        val tracker = project.service<RunningProcessTracker>()
        val process = tracker.getRunningProcess(configName) ?: return emptyList()

        val consoleView = process.environment.contentToReuse?.executionConsole as? ConsoleView
            ?: return emptyList()

        val text =
            try {
                getConsoleText(consoleView)
            } catch (e: Exception) {
                return emptyList()
            }

        return searchInText(text, query, "console", caseSensitive, maxResults)
    }

    /**
     * Searches for a pattern in a log file.
     */
    fun searchLogFile(
        filePath: String,
        query: String,
        caseSensitive: Boolean = false,
        maxResults: Int = 100,
    ): List<SearchResult> {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return emptyList()
        }

        val text = file.readText()
        return searchInText(text, query, file.name, caseSensitive, maxResults)
    }

    /**
     * Helper to search in text content.
     */
    private fun searchInText(
        text: String,
        query: String,
        sourceName: String,
        caseSensitive: Boolean,
        maxResults: Int,
    ): List<SearchResult> {
        val lines = text.lines()
        val results = mutableListOf<SearchResult>()
        val searchQuery = if (caseSensitive) query else query.lowercase()

        lines.forEachIndexed { index, line ->
            val searchLine = if (caseSensitive) line else line.lowercase()
            if (searchLine.contains(searchQuery)) {
                results.add(
                    SearchResult(
                        source = sourceName,
                        line = index.toLong(),
                        content = line,
                        context = getContext(lines, index, 2),
                    ),
                )

                if (results.size >= maxResults) {
                    return results
                }
            }
        }

        return results
    }

    /**
     * Gets context lines around a match.
     */
    private fun getContext(
        lines: List<String>,
        index: Int,
        contextSize: Int,
    ): List<String> {
        val start = (index - contextSize).coerceAtLeast(0)
        val end = (index + contextSize + 1).coerceAtMost(lines.size)
        return lines.subList(start, end)
    }

    /**
     * Attempts to extract text from ConsoleView.
     * Different console implementations may store text differently.
     */
    private fun getConsoleText(consoleView: ConsoleView): String =
        try {
            // Try to get editor from console
            val editor =
                (consoleView as? Any)?.let { console ->
                    // Use reflection to access editor if available
                    val editorField =
                        console.javaClass.declaredFields
                            .find { it.name == "editor" || it.type == Editor::class.java }

                    editorField?.let {
                        it.isAccessible = true
                        it.get(console) as? Editor
                    }
                }

            editor?.document?.text ?: "Console content not accessible"
        } catch (e: Exception) {
            "Error accessing console: ${e.message}"
        }

    /**
     * Gets the last N lines from console (tail).
     */
    fun tailConsole(
        configName: String,
        lines: Int = 100,
    ): LogContent? {
        val content = readConsoleOutput(configName, 0, Long.MAX_VALUE) ?: return null
        val totalLines = content.totalLines
        val offset = maxOf(0, totalLines - lines)

        return readConsoleOutput(configName, offset, lines.toLong())
    }

    /**
     * Gets the last N lines from a log file (tail).
     */
    fun tailLogFile(
        filePath: String,
        lines: Int = 100,
    ): LogContent {
        val file = File(filePath)
        if (!file.exists()) {
            return LogContent("", 0, 0, 0, emptyList(), false)
        }

        val allLines = file.readLines()
        val totalLines = allLines.size.toLong()
        val offset = maxOf(0, totalLines - lines)

        return readLogFile(filePath, offset, lines.toLong())
    }
}
