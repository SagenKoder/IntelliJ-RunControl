package app.sagen.runcontrol.service

import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project

/**
 * Resolves the current status of run configurations using IntelliJ's ExecutionManager.
 *
 * Status values:
 * - idle: No running process
 * - running: Process is running (normal execution)
 * - debugging: Process is running in debug mode
 * - stopping: Process is terminating
 * - finished: Process has terminated
 */
object RunConfigStatusResolver {

    fun getStatus(configName: String, project: Project): String {
        val executionManager = ExecutionManager.getInstance(project)

        // Get all running descriptors
        val runningDescriptors = executionManager.getRunningDescriptors { true }

        // Find descriptor matching the configuration name
        val descriptor = runningDescriptors.find {
            it.displayName == configName
        } ?: return "idle"

        // Get the process handler
        val processHandler = descriptor.processHandler ?: return "idle"

        // Check process state
        // Note: We check the attached console's content for debug indicators
        return when {
            processHandler.isProcessTerminating -> "stopping"
            processHandler.isProcessTerminated -> "finished"
            descriptor.displayName.contains("Debug", ignoreCase = true) -> "debugging"
            else -> "running"
        }
    }

    /**
     * Gets the descriptor for a running configuration.
     * Used for restart operations.
     */
    fun getRunningDescriptor(configName: String, project: Project) =
        ExecutionManager.getInstance(project)
            .getRunningDescriptors { true }
            .find { it.displayName == configName }

    /**
     * Checks if a configuration is currently running.
     */
    fun isRunning(configName: String, project: Project): Boolean {
        val status = getStatus(configName, project)
        return status == "running" || status == "debugging"
    }
}
