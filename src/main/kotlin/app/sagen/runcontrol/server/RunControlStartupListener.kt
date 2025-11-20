package app.sagen.runcontrol.server

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.diagnostic.Logger

/**
 * Starts the HTTP server when IntelliJ IDEA starts.
 */
class RunControlStartupListener : AppLifecycleListener {

    private val logger = Logger.getInstance(RunControlStartupListener::class.java)

    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        logger.info("Starting RunControl HTTP server...")
        try {
            RunControlServer.getInstance().start()
        } catch (e: Exception) {
            logger.error("Failed to start RunControl server on startup", e)
        }
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        logger.info("Stopping RunControl HTTP server...")
        try {
            RunControlServer.getInstance().stop()
        } catch (e: Exception) {
            logger.error("Failed to stop RunControl server on shutdown", e)
        }
    }
}
