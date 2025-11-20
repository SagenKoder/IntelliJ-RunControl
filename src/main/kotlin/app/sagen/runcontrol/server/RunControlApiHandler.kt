package app.sagen.runcontrol.server

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import app.sagen.runcontrol.service.LogService
import app.sagen.runcontrol.service.RunConfigService
import app.sagen.runcontrol.settings.RunControlSettings
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException

/**
 * HTTP API handler for run configuration management.
 * Routes requests and returns JSON responses.
 */
class RunControlApiHandler : HttpServlet() {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        if (!authenticate(req, resp)) return

        try {
            val path = req.pathInfo ?: "/"

            when {
                path == "/projects" -> handleListProjects(resp)
                path == "/run-configs" || path == "/" -> handleListRunConfigs(req, resp)
                path.startsWith("/run-configs/") && path.contains("/logs") -> handleLogsRequest(req, path, resp)
                path.startsWith("/run-configs/") -> handleGetRunConfig(req, path, resp)
                else -> sendError(resp, 404, "Not found")
            }
        } catch (e: Exception) {
            sendError(resp, 500, "Internal server error: ${e.message}")
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        if (!authenticate(req, resp)) return

        try {
            val path = req.pathInfo ?: "/"

            if (path.startsWith("/run-configs/")) {
                handleExecuteAction(req, path, resp)
            } else {
                sendError(resp, 404, "Not found")
            }
        } catch (e: Exception) {
            sendError(resp, 500, "Internal server error: ${e.message}")
        }
    }

    /**
     * Authenticates the request using the X-IntelliJ-Token header.
     */
    private fun authenticate(req: HttpServletRequest, resp: HttpServletResponse): Boolean {
        val settings = RunControlSettings.getInstance()
        val token = req.getHeader("X-IntelliJ-Token")

        if (token == null || token != settings.token) {
            sendError(resp, 401, "Unauthorized - invalid or missing token")
            return false
        }

        return true
    }

    /**
     * GET /run-configs?project=<name>
     * Lists all run configurations.
     */
    private fun handleListRunConfigs(req: HttpServletRequest, resp: HttpServletResponse) {
        val project = getProject(req, resp) ?: return

        val service = project.service<RunConfigService>()
        val configs = service.listRunConfigs()

        sendJson(resp, configs)
    }

    /**
     * GET /run-configs/{name}?project=<name>
     * Gets a specific run configuration.
     */
    private fun handleGetRunConfig(req: HttpServletRequest, path: String, resp: HttpServletResponse) {
        val project = getProject(req, resp) ?: return

        val name = extractConfigName(path)
        if (name == null) {
            sendError(resp, 400, "Invalid configuration name")
            return
        }

        val service = project.service<RunConfigService>()
        val config = service.getRunConfig(name)

        if (config == null) {
            sendError(resp, 404, "Configuration not found: $name")
        } else {
            sendJson(resp, config)
        }
    }

    /**
     * POST /run-configs/{name}/{action}?project=<name>
     * Executes an action on a run configuration.
     */
    private fun handleExecuteAction(req: HttpServletRequest, path: String, resp: HttpServletResponse) {
        val project = getProject(req, resp) ?: return

        val parts = path.split("/").filter { it.isNotEmpty() }
        if (parts.size < 3) {
            sendError(resp, 400, "Invalid request format. Expected: /run-configs/{name}/{action}")
            return
        }

        val name = parts[1]
        val action = parts[2]

        val service = project.service<RunConfigService>()
        val result = service.executeAction(name, action)

        if (result.status == "error") {
            sendJson(resp, result, 400)
        } else {
            sendJson(resp, result)
        }
    }

    /**
     * Gets the project to operate on.
     * Supports ?project=<name> query parameter to select a specific project.
     * If not specified, uses the first open project.
     */
    private fun getProject(req: HttpServletRequest, resp: HttpServletResponse): Project? {
        val projectManager = ProjectManager.getInstance()
        val openProjects = projectManager.openProjects

        if (openProjects.isEmpty()) {
            sendError(resp, 503, "No project is currently open")
            return null
        }

        val projectName = req.getParameter("project")
        if (projectName != null) {
            val project = openProjects.find { it.name == projectName }
            if (project == null) {
                val availableProjects = openProjects.map { it.name }
                sendError(resp, 404, "Project not found: $projectName. Available projects: $availableProjects")
                return null
            }
            return project
        }

        // Default to first project
        return openProjects.firstOrNull()
    }

    /**
     * GET /projects
     * Lists all open projects.
     */
    private fun handleListProjects(resp: HttpServletResponse) {
        val projectManager = ProjectManager.getInstance()
        val projects = projectManager.openProjects.map { project ->
            mapOf(
                "name" to project.name,
                "path" to (project.basePath ?: "unknown"),
                "isDefault" to (project == projectManager.openProjects.firstOrNull())
            )
        }
        sendJson(resp, projects)
    }

    /**
     * Extracts the configuration name from a path like /run-configs/{name}.
     */
    private fun extractConfigName(path: String): String? {
        val parts = path.split("/").filter { it.isNotEmpty() }
        return if (parts.size >= 2) parts[1] else null
    }

    /**
     * Sends a JSON response.
     */
    private fun sendJson(resp: HttpServletResponse, data: Any, statusCode: Int = 200) {
        resp.contentType = "application/json"
        resp.characterEncoding = "UTF-8"
        resp.status = statusCode

        try {
            resp.writer.write(gson.toJson(data))
        } catch (e: IOException) {
            // Failed to write response - log but don't throw
            e.printStackTrace()
        }
    }

    /**
     * Sends an error response.
     */
    private fun sendError(resp: HttpServletResponse, statusCode: Int, message: String) {
        val error = mapOf(
            "status" to "error",
            "message" to message
        )
        sendJson(resp, error, statusCode)
    }

    /**
     * Handles log-related requests.
     * Routes:
     * - GET /run-configs/{name}/logs - List available log sources
     * - GET /run-configs/{name}/logs/{source} - Read log content with pagination
     * - GET /run-configs/{name}/logs/{source}/search - Search in logs
     * - GET /run-configs/{name}/logs/{source}/tail - Get last N lines
     */
    private fun handleLogsRequest(req: HttpServletRequest, path: String, resp: HttpServletResponse) {
        val project = getProject(req, resp) ?: return
        val logService = project.service<LogService>()

        val parts = path.split("/").filter { it.isNotEmpty() }
        // parts: [run-configs, {name}, logs, ...]

        if (parts.size < 3) {
            sendError(resp, 400, "Invalid logs request path")
            return
        }

        val configName = parts[1]

        when {
            // GET /run-configs/{name}/logs - List log sources
            parts.size == 3 && parts[2] == "logs" -> {
                val sources = logService.listLogSources(configName)
                sendJson(resp, sources)
            }

            // GET /run-configs/{name}/logs/{source}/tail
            parts.size == 5 && parts[4] == "tail" -> {
                val sourceName = parts[3]
                val lines = req.getParameter("lines")?.toIntOrNull() ?: 100

                val content = if (sourceName == "console") {
                    logService.tailConsole(configName, lines)
                } else {
                    // Find file path from sources
                    val sources = logService.listLogSources(configName)
                    val source = sources.find { it.name == sourceName }
                    source?.path?.let { logService.tailLogFile(it, lines) }
                }

                if (content != null) {
                    sendJson(resp, content)
                } else {
                    sendError(resp, 404, "Log source not found: $sourceName")
                }
            }

            // GET /run-configs/{name}/logs/{source}/search
            parts.size == 5 && parts[4] == "search" -> {
                val sourceName = parts[3]
                val query = req.getParameter("q") ?: req.getParameter("query")
                val caseSensitive = req.getParameter("caseSensitive")?.toBoolean() ?: false
                val maxResults = req.getParameter("maxResults")?.toIntOrNull() ?: 100

                if (query == null) {
                    sendError(resp, 400, "Missing query parameter: q or query")
                    return
                }

                val results = if (sourceName == "console") {
                    logService.searchConsole(configName, query, caseSensitive, maxResults)
                } else {
                    val sources = logService.listLogSources(configName)
                    val source = sources.find { it.name == sourceName }
                    source?.path?.let {
                        logService.searchLogFile(it, query, caseSensitive, maxResults)
                    } ?: emptyList()
                }

                sendJson(resp, mapOf(
                    "query" to query,
                    "caseSensitive" to caseSensitive,
                    "results" to results
                ))
            }

            // GET /run-configs/{name}/logs/{source} - Read with pagination
            parts.size == 4 -> {
                val sourceName = parts[3]
                val offset = req.getParameter("offset")?.toLongOrNull() ?: 0
                val limit = req.getParameter("limit")?.toLongOrNull() ?: 100

                val content = if (sourceName == "console") {
                    logService.readConsoleOutput(configName, offset, limit)
                } else {
                    val sources = logService.listLogSources(configName)
                    val source = sources.find { it.name == sourceName }
                    source?.path?.let { logService.readLogFile(it, offset, limit) }
                }

                if (content != null) {
                    sendJson(resp, content)
                } else {
                    sendError(resp, 404, "Log source not found: $sourceName")
                }
            }

            else -> sendError(resp, 404, "Invalid logs request path")
        }
    }
}
