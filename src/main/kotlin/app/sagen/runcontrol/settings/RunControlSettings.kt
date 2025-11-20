package app.sagen.runcontrol.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.UUID

@State(
    name = "app.sagen.runcontrol.settings.RunControlSettings",
    storages = [Storage("RunControlPlugin.xml")],
)
class RunControlSettings : PersistentStateComponent<RunControlSettings> {
    var enabled: Boolean = true
    var port: Int = 17777
    var token: String = ""

    companion object {
        fun getInstance(): RunControlSettings = ApplicationManager.getApplication().getService(RunControlSettings::class.java)
    }

    override fun getState(): RunControlSettings = this

    override fun loadState(state: RunControlSettings) {
        XmlSerializerUtil.copyBean(state, this)

        // Generate token on first load if empty
        if (token.isEmpty()) {
            token = generateSecureToken()
        }
    }

    private fun generateSecureToken(): String = UUID.randomUUID().toString().replace("-", "")

    fun regenerateToken(): String {
        token = generateSecureToken()
        return token
    }
}
