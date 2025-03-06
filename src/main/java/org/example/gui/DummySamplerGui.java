package org.example.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;
import org.example.DummySampler;
import org.example.ApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummySamplerGui extends AbstractSamplerGui {

    private static final Logger LOG = LoggerFactory.getLogger(DummySamplerGui.class);
    private final JEditorPane chatArea = new JEditorPane();
    private final JTextField userInput = new JTextField();
    private final JPasswordField apiTokenField = new JPasswordField();
    private final JButton sendButton = new JButton("Send");

    public DummySamplerGui() {
        // Set layout and border for the GUI
        setLayout(new BorderLayout());
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);
        add(createChatPanel(), BorderLayout.CENTER);

        LOG.info("DummySamplerGui initialized");
    }

    // Create the chat panel with input fields and chat display area
    private JPanel createChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout());
    
        // Setup JEditorPane for HTML display
        chatArea.setContentType("text/html");
        chatArea.setEditable(false);
        chatArea.setText("<html><body>" + formatHtmlMessage("Welcome to JMeter AI Assistant!") + "</body></html>");
    
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
    
        JPanel tokenPanel = new JPanel(new BorderLayout());
        tokenPanel.add(new JLabel("AI Token:"), BorderLayout.WEST);
        tokenPanel.add(apiTokenField, BorderLayout.CENTER);
        chatPanel.add(tokenPanel, BorderLayout.NORTH);
    
        // Create a panel for buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton readScriptButton = new JButton("Read Script");
        JButton readLogsButton = new JButton("Read Logs");
        JButton readResultButton = new JButton("Read Result");
    
        // Add buttons to button panel
        buttonPanel.add(readScriptButton);
        buttonPanel.add(readLogsButton);
        buttonPanel.add(readResultButton);
    
        // Create a panel for user input and send button
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(userInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
    
        // Wrap both panels in another panel
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(buttonPanel, BorderLayout.NORTH);
        southPanel.add(inputPanel, BorderLayout.SOUTH);
    
        // Add the combined panel to chatPanel
        chatPanel.add(southPanel, BorderLayout.SOUTH);
    
        // Add action listener to send button
        sendButton.addActionListener(e -> {
            String userText = userInput.getText().trim();
            String apiToken = new String(apiTokenField.getPassword()).trim();
    
            if (userText.isEmpty()) {
                LOG.warn("User attempted to send an empty message.");
                return; // Ignore empty messages
            }
    
            if (apiToken.isEmpty()) {
                LOG.error("No API Key found.");
                appendToChat("<b>AI Error:</b> No API Key Found");
                return;
            }
    
            LOG.info("User message: {}", userText);
    
            // Append user message to chat
            appendToChat("<b>You:</b> " + userText);
            userInput.setText(""); // Clear input field
    
            // Run API call in a separate thread
            new Thread(() -> {
                LOG.info("Sending message to API: {}", userText);
                String aiResponse = ApiClient.sendToAI(userText, apiToken);
    
                SwingUtilities.invokeLater(() -> {
                    String formattedResponse = aiResponse.startsWith("Error")
                            ? formatHtmlMessage("<b>AI Error:</b> " + aiResponse)
                            : formatHtmlResponse(aiResponse);
    
                    LOG.info("Received API response: {}", aiResponse);
                    appendToChat(formattedResponse);
                });
            }).start();
        });
    
        return chatPanel;
    }
    
    // Append new messages to the chat window properly
    private void appendToChat(String message) {
        SwingUtilities.invokeLater(() -> {
            String currentText = chatArea.getText();

            // Ensure the chat content starts correctly with <html>
            if (!currentText.startsWith("<html>")) {
                currentText = "<html><body>" + formatHtmlMessage("Welcome to JMeter AI Assistant!") + "</body></html>";
            }

            // Insert the new message properly before </body>
            int bodyEndIndex = currentText.lastIndexOf("</body>");
            if (bodyEndIndex != -1) {
                currentText = currentText.substring(0, bodyEndIndex) + formatHtmlMessage(message)
                        + currentText.substring(bodyEndIndex);
            } else {
                // Fallback if structure is somehow broken
                currentText = "<html><body>" + formatHtmlMessage("Welcome to JMeter AI Assistant!") +
                        formatHtmlMessage(message) + "</body></html>";
            }

            // Set the updated chat text
            chatArea.setText(currentText);
            LOG.info("Updated chat content: {}", currentText);
        });
    }

    // Convert plain text to formatted HTML
    private String formatHtmlMessage(String message) {
        // Replace common Markdown syntax with HTML
        message = message
                .replaceAll("(?m)^### (.+)$", "<h3>$1</h3>") // H3 Headers
                .replaceAll("(?m)^## (.+)$", "<h2>$1</h2>") // H2 Headers
                .replaceAll("(?m)^# (.+)$", "<h1>$1</h1>") // H1 Headers
                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>") // Bold (**text**)
                .replaceAll("\\*(.*?)\\*", "<i>$1</i>") // Italic (*text*)
                .replaceAll("`([^`]*)`", "<code>$1</code>") // Inline Code
                .replaceAll("```\\s*groovy([\\s\\S]*?)```", "<pre><code>$1</code></pre>") // Groovy Code Blocks
                .replaceAll("```([\\s\\S]*?)```", "<pre><code>$1</code></pre>") // Other Code Blocks
                .replaceAll("(?m)^- (.+)$", "<li>$1</li>") // Bullet Points
                .replaceAll("\n", "<br>"); // Preserve line breaks

        // Wrap bullet points in <ul>
        message = message.replaceAll("(<li>.*?</li>)+", "<ul>$0</ul>");

        // Ensure safe HTML formatting
        return "<div style='font-family: Arial, sans-serif; font-size: 12px; padding: 5px;'>" + message + "</div>";
    }

    // Format AI response from markdown to HTML
    private String formatHtmlResponse(String markdownResponse) {
        // Replace markdown-style formatting with HTML
        String formattedResponse = markdownResponse
                .replace("**", "<b>").replace("**", "</b>") // Bold
                .replace("*", "<i>").replace("*", "</i>") // Italic
                .replace("\n", "<br>"); // Line breaks

        return "<div style='font-family: Arial, sans-serif; font-size: 12px; padding: 5px;'>" + formattedResponse
                + "</div>";
    }

    @Override
    public String getLabelResource() {
        return "Jmeter-AI-Assistant";
    }

    @Override
    public String getStaticLabel() {
        return getLabelResource();
    }

    @Override
    public TestElement createTestElement() {
        DummySampler dummySampler = new DummySampler();
        configureTestElement(dummySampler);
        return dummySampler;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        super.configureTestElement(element);
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
    }
}
