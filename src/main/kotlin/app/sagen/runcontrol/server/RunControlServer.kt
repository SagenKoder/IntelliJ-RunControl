package app.sagen.runcontrol.server

import com.intellij.openapi.diagnostic.Logger
import app.sagen.runcontrol.settings.RunControlSettings
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder

/**
 * HTTP server for the RunControl API.
 * Binds only to localhost for security.
 */
class RunControlServer {
    private var server: Server? = null
    private val logger = Logger.getInstance(RunControlServer::class.java)

    fun start() {
        val settings = RunControlSettings.getInstance()

        if (!settings.enabled) {
            logger.info("RunControl API is disabled in settings")
            return
        }

        try {
            server = Server().apply {
                // Create connector bound to localhost only
                val connector = ServerConnector(this).apply {
                    host = "127.0.0.1"
                    port = settings.port
                }
                addConnector(connector)

                // Create servlet context
                val context = ServletContextHandler(ServletContextHandler.SESSIONS).apply {
                    contextPath = "/"
                    addServlet(ServletHolder(RunControlApiHandler()), "/*")
                }

                handler = context
            }

            server?.start()
            logger.info("RunControl API started on http://127.0.0.1:${settings.port}")
        } catch (e: Exception) {
            logger.error("Failed to start RunControl API server", e)
        }
    }

    fun stop() {
        try {
            server?.stop()
            logger.info("RunControl API server stopped")
        } catch (e: Exception) {
            logger.error("Error stopping RunControl API server", e)
        } finally {
            server = null
        }
    }

    fun isRunning(): Boolean {
        return server?.isRunning ?: false
    }

    companion object {
        private var instance: RunControlServer? = null

        fun getInstance(): RunControlServer {
            if (instance == null) {
                instance = RunControlServer()
            }
            return instance!!
        }
    }
}
