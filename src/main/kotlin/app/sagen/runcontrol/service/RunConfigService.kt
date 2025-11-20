package app.sagen.runcontrol.service

import com.intellij.execution.ExecutionManager
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Service for managing run configurations using IntelliJ's native APIs.
 */
@Service(Service.Level.PROJECT)
class RunConfigService(
    private val project: Project,
) {
    data class RunConfigInfo(
        val name: String,
        val type: String,
        val actions: List<String>,
        val status: String,
    )

    data class ActionResult(
        val status: String,
        val message: String,
        val state: String,
    )

    /**
     * Lists all run configurations in the project.
     */
    fun listRunConfigs(): List<RunConfigInfo> {
        val runManager = RunManager.getInstance(project)
        val tracker = project.service<RunningProcessTracker>()

        return runManager.allConfigurationsList.map { config ->
            RunConfigInfo(
                name = config.name,
                type = config.type.displayName,
                actions = listOf("run", "debug", "stop", "restart"),
                status = tracker.getStatus(config.name),
            )
        }
    }

    /**
     * Gets a single run configuration by name.
     */
    fun getRunConfig(name: String): RunConfigInfo? {
        val runManager = RunManager.getInstance(project)
        val tracker = project.service<RunningProcessTracker>()
        val config = runManager.allConfigurationsList.find { it.name == name } ?: return null

        return RunConfigInfo(
            name = config.name,
            type = config.type.displayName,
            actions = listOf("run", "debug", "stop", "restart"),
            status = tracker.getStatus(config.name),
        )
    }

    /**
     * Executes an action on a run configuration using native IntelliJ APIs.
     */
    fun executeAction(
        name: String,
        action: String,
    ): ActionResult =
        when (action.lowercase()) {
            "run" -> runConfiguration(name)
            "debug" -> debugConfiguration(name)
            "stop" -> stopConfiguration(name)
            "restart" -> restartConfiguration(name)
            else ->
                ActionResult(
                    status = "error",
                    message = "Unknown action: $action",
                    state = "error",
                )
        }

    /**
     * Runs a configuration using DefaultRunExecutor.
     */
    private fun runConfiguration(name: String): ActionResult {
        val runManager = RunManager.getInstance(project)
        val config =
            runManager.allConfigurationsList.find { it.name == name }
                ?: return ActionResult("error", "Configuration not found: $name", "error")

        val runnerSettings =
            runManager.findSettings(config)
                ?: return ActionResult("error", "Configuration settings not found", "error")

        try {
            ProgramRunnerUtil.executeConfiguration(
                runnerSettings,
                DefaultRunExecutor.getRunExecutorInstance(),
            )
            return ActionResult(
                status = "ok",
                message = "Executed action 'run' on $name",
                state = "running",
            )
        } catch (e: Exception) {
            return ActionResult(
                status = "error",
                message = "Failed to run configuration: ${e.message}",
                state = "error",
            )
        }
    }

    /**
     * Debugs a configuration using DefaultDebugExecutor.
     */
    private fun debugConfiguration(name: String): ActionResult {
        val runManager = RunManager.getInstance(project)
        val config =
            runManager.allConfigurationsList.find { it.name == name }
                ?: return ActionResult("error", "Configuration not found: $name", "error")

        val runnerSettings =
            runManager.findSettings(config)
                ?: return ActionResult("error", "Configuration settings not found", "error")

        try {
            ProgramRunnerUtil.executeConfiguration(
                runnerSettings,
                DefaultDebugExecutor.getDebugExecutorInstance(),
            )
            return ActionResult(
                status = "ok",
                message = "Executed action 'debug' on $name",
                state = "debugging",
            )
        } catch (e: Exception) {
            return ActionResult(
                status = "error",
                message = "Failed to debug configuration: ${e.message}",
                state = "error",
            )
        }
    }

    /**
     * Stops a running configuration by destroying its process.
     */
    private fun stopConfiguration(name: String): ActionResult {
        val tracker = project.service<RunningProcessTracker>()
        val process = tracker.getRunningProcess(name)

        if (process == null) {
            return ActionResult(
                status = "ok",
                message = "Configuration is not running: $name",
                state = "idle",
            )
        }

        val processHandler = process.processHandler
        if (processHandler.isProcessTerminated) {
            return ActionResult(
                status = "ok",
                message = "Configuration already stopped: $name",
                state = "idle",
            )
        }

        try {
            processHandler.destroyProcess()
            return ActionResult(
                status = "ok",
                message = "Executed action 'stop' on $name",
                state = "stopping",
            )
        } catch (e: Exception) {
            return ActionResult(
                status = "error",
                message = "Failed to stop configuration: ${e.message}",
                state = "error",
            )
        }
    }

    /**
     * Restarts a configuration by stopping and then starting it again.
     * For complex restarts with "Update" dialogs, the user should use the IDE UI.
     */
    private fun restartConfiguration(name: String): ActionResult {
        val tracker = project.service<RunningProcessTracker>()
        val process = tracker.getRunningProcess(name)

        if (process == null) {
            // Configuration is not running, just start it
            return runConfiguration(name)
        }

        try {
            // Stop the current process
            val processHandler = process.processHandler
            if (!processHandler.isProcessTerminated) {
                processHandler.destroyProcess()

                // Wait a bit for process to terminate, then restart
                Thread.sleep(500)
            }

            // Now start it again
            return runConfiguration(name)
        } catch (e: Exception) {
            return ActionResult(
                status = "error",
                message = "Failed to restart configuration: ${e.message}",
                state = "error",
            )
        }
    }
}
