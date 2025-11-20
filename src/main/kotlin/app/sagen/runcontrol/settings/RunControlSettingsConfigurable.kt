package app.sagen.runcontrol.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class RunControlSettingsConfigurable : Configurable {
    private var enabledCheckBox: JBCheckBox? = null
    private var portField: JBTextField? = null
    private var tokenField: JBTextField? = null
    private var regenerateButton: JButton? = null

    override fun getDisplayName(): String = "RunControl"

    override fun createComponent(): JComponent {
        val settings = RunControlSettings.getInstance()

        enabledCheckBox = JBCheckBox("Enable HTTP API", settings.enabled)
        portField = JBTextField(settings.port.toString(), 10)
        tokenField =
            JBTextField(settings.token, 40).apply {
                isEditable = false
            }
        regenerateButton =
            JButton("Regenerate Token").apply {
                addActionListener {
                    val newToken = settings.regenerateToken()
                    tokenField?.text = newToken
                    Messages.showInfoMessage(
                        "New token generated. Remember to save settings!",
                        "Token Regenerated",
                    )
                }
            }

        val tokenPanel =
            JPanel(BorderLayout()).apply {
                add(tokenField!!, BorderLayout.CENTER)
                add(regenerateButton!!, BorderLayout.EAST)
            }

        return FormBuilder
            .createFormBuilder()
            .addComponent(enabledCheckBox!!)
            .addSeparator()
            .addLabeledComponent(JBLabel("Port:"), portField!!, 1, false)
            .addLabeledComponent(JBLabel("API Token:"), tokenPanel, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .addTooltip("The API will be available at http://127.0.0.1:<port>/")
            .addTooltip("Use the token in the X-IntelliJ-Token header for authentication")
            .addTooltip("API requires restart after port change")
            .panel
    }

    override fun isModified(): Boolean {
        val settings = RunControlSettings.getInstance()
        return enabledCheckBox?.isSelected != settings.enabled ||
            portField?.text?.toIntOrNull() != settings.port ||
            tokenField?.text != settings.token
    }

    override fun apply() {
        val settings = RunControlSettings.getInstance()
        val oldPort = settings.port

        settings.enabled = enabledCheckBox?.isSelected ?: true
        settings.port = portField?.text?.toIntOrNull() ?: 17777
        settings.token = tokenField?.text ?: ""

        if (oldPort != settings.port) {
            Messages.showWarningDialog(
                "Port changed. Please restart IntelliJ IDEA for the change to take effect.",
                "Restart Required",
            )
        }
    }

    override fun reset() {
        val settings = RunControlSettings.getInstance()
        enabledCheckBox?.isSelected = settings.enabled
        portField?.text = settings.port.toString()
        tokenField?.text = settings.token
    }
}
