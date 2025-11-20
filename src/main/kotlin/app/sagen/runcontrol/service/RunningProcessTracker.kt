package app.sagen.runcontrol.service

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks running processes using the public ExecutionListener API.
 * This avoids using internal APIs like getRunningDescriptors().
 */
@Service(Service.Level.PROJECT)
class RunningProcessTracker(
    private val project: Project,
) {
    data class RunningProcess(
        val configName: String,
        val processHandler: ProcessHandler,
        val environment: ExecutionEnvironment,
        val isDebug: Boolean,
        val consoleView: ConsoleView?,
    )

    private val runningProcesses = ConcurrentHashMap<String, RunningProcess>()

    init {
        // Subscribe to execution events
        project.messageBus.connect().subscribe(
            ExecutionManager.EXECUTION_TOPIC,
            object : ExecutionListener {
                override fun processStarted(
                    executorId: String,
                    env: ExecutionEnvironment,
                    handler: ProcessHandler,
                ) {
                    val configName = env.runProfile.name
                    val isDebug = executorId == DefaultDebugExecutor.EXECUTOR_ID

                    runningProcesses[configName] =
                        RunningProcess(
                            configName = configName,
                            processHandler = handler,
                            environment = env,
                            isDebug = isDebug,
                            consoleView = null // Will be set when console is attached
                        )

                    // Remove from map when process terminates
                    handler.addProcessListener(
                        object : com.intellij.execution.process.ProcessAdapter() {
                            override fun processTerminated(event: com.intellij.execution.process.ProcessEvent) {
                                runningProcesses.remove(configName)
                            }
                        },
                    )
                }

                override fun processStartScheduled(executorId: String, env: ExecutionEnvironment) {
                    // Can potentially capture more info here
                }
            },
        )
    }

    /**
     * Gets the status of a configuration.
     */
    fun getStatus(configName: String): String {
        val process = runningProcesses[configName] ?: return "idle"
        val handler = process.processHandler

        return when {
            handler.isProcessTerminating -> "stopping"
            handler.isProcessTerminated -> "finished"
            process.isDebug -> "debugging"
            else -> "running"
        }
    }

    /**
     * Gets the running process for a configuration.
     */
    fun getRunningProcess(configName: String): RunningProcess? = runningProcesses[configName]

    /**
     * Checks if a configuration is running.
     */
    fun isRunning(configName: String): Boolean {
        val status = getStatus(configName)
        return status == "running" || status == "debugging"
    }

    /**
     * Gets all running configuration names.
     */
    fun getRunningConfigNames(): Set<String> = runningProcesses.keys.toSet()
}
