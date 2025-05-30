package org.example.jmetercopilot;

import org.apache.jmeter.gui.action.Command;
import org.apache.jmeter.util.JMeterUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.HashSet;
import java.util.Set;

public class CopilotAction implements Command {
    private static final Set<String> commands = new HashSet<>();
    private static JFrame copilotFrame; // Keep track of the frame
    private static ApiClient apiClientInstance; // Singleton ApiClient

    static {
        commands.add("show_copilot_panel");
    }

    @Override
    public Set<String> getActionNames() {
        return commands;
    }

    @Override
    public void doAction(ActionEvent e) {
        if (e.getActionCommand().equals("show_copilot_panel")) {
            if (apiClientInstance == null) {
                apiClientInstance = new ApiClient();
                // Try to load API key from JMeter properties if previously saved
                String savedApiKey = JMeterUtils.getProperty("jmeter_copilot.api_key");
                if (savedApiKey != null && !savedApiKey.isEmpty()) {
                    apiClientInstance.setApiKey(savedApiKey);
                }
            }

            if (copilotFrame == null || !copilotFrame.isVisible()) {
                copilotFrame = new JFrame("JMeter Copilot");
                JMeterCopilotPanel panel = new JMeterCopilotPanel(apiClientInstance);
                if (apiClientInstance.getApiKey() != null && !apiClientInstance.getApiKey().isEmpty()){
                    // This direct field access is not ideal, consider a method in JMeterCopilotPanel
                    // to set the API key field's text if needed for pre-fill.
                    // panel.apiKeyField.setText(apiClientInstance.getApiKey()); 
                }
                copilotFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Dispose on close to allow reopening
                copilotFrame.getContentPane().add(panel);
                copilotFrame.pack();
                copilotFrame.setLocationRelativeTo(null); // Center on screen
                copilotFrame.setVisible(true);
            } else {
                copilotFrame.toFront(); // Bring to front if already visible
            }
        }
    }

    // Method to be called by JMeter to register this action
    // This would typically be done via properties or a custom menu bar class
    // For now, this is illustrative.
    // You would need to add "show_copilot_panel=org.example.jmetercopilot.CopilotAction"
    // to a JMeter properties file (e.g., user.properties or a custom one for the plugin)
    // under a section like "gui.action.classes".
    // Or, more robustly, create a custom MenuBar class.
}
