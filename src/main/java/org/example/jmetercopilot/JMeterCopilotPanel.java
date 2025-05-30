package org.example.jmetercopilot; // Adjust package name as needed

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// JMeter API imports
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode; // Added for @this
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.JMeterProperty; // Added for @this
import org.apache.jmeter.testelement.property.PropertyIterator; // Added for @this
// Note: Using the specific ThreadGroup class from org.apache.jmeter.threads
import org.apache.jmeter.threads.ThreadGroup; 
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.apache.jmeter.config.CSVDataSet;
import org.apache.jmeter.testelement.TestPlan; // For TestPlan class
import org.apache.jmeter.timers.Timer;          // For timers
import org.apache.jmeter.timers.UniformRandomTimer;
import org.apache.jmeter.samplers.Sampler;    // For identifying samplers (interface)
import org.apache.jmeter.extractor.RegexExtractor;
import java.util.Arrays;
import java.util.ArrayList; // Add for args list
import java.util.List;      // Add for args list
import java.util.Map; // For @lint (not used in this impl)
import java.util.HashMap; // For @lint (not used in this impl)
import org.apache.jmeter.control.Controller; // For identifying controllers
import org.apache.jmeter.config.ConfigElement; // For config elements
import org.apache.jmeter.assertions.Assertion;  // For assertions
import org.apache.jmeter.visualizers.Visualizer; // For listeners/visualizers
import org.apache.jmeter.processor.PreProcessor; // For pre-processors
import org.apache.jmeter.processor.PostProcessor; // For post-processors
import org.apache.jmeter.threads.AbstractThreadGroup; // Base for Thread Groups
import org.apache.jmeter.control.TransactionController; // Added for @wrap
import org.apache.jmeter.util.JSR223TestElement; // Common base class for JSR223 elements
import org.apache.jmeter.testbeans.TestBean;    // Many JSR223 elements are TestBeans
// regex.Matcher and regex.Pattern are already imported


public class JMeterCopilotPanel extends JPanel implements ActionListener {

    // UI Components
    private JButton askButton;
    private JButton editButton;
    private JTextArea inputArea;
    private JEditorPane responseArea; // JEditorPane can render HTML/Markdown (basic)
    private JButton sendButton; // Or "Execute" button
    private JTextField apiKeyField; // For API Key input
    private JButton setApiKeyButton;

    // API Client
    private ApiClient apiClient;

    // State
    private enum Mode { ASK, EDIT }
    private Mode currentMode = Mode.ASK;

    // System prompt for the AI
    private static final String SYSTEM_PROMPT = "You are an expert assistant for Apache JMeter, embedded as a plugin named JMeter Copilot. Your role is to provide intelligent support for performance engineers for real-time script authoring, debugging, and enhancement. Your outputs should be in valid JMX-compatible structure where applicable, or user-friendly markdown/code if outside of JMeter context. Always aim to be helpful and precise.";

    private transient List<ProposedNameChange> proposedNameChanges = null; // transient as it's UI state
    private String lastAiScriptResponse = null; // To store AI's script output for @jsr223_apply_script


    // Helper class for storing proposed changes for @lint
    private static class ProposedNameChange {
        TestElement element;
        String oldName;
        String newName;

        ProposedNameChange(TestElement element, String oldName, String newName) {
            this.element = element;
            this.oldName = oldName;
            this.newName = newName;
        }
    }

    public JMeterCopilotPanel(ApiClient client) {
        this.apiClient = client;
        initComponents();
        // Add a simple border for padding around the whole panel
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); 
        layoutComponents();
        updatePlaceholderText();
        // Consider requesting focus for inputArea, but might need to be handled by parent frame activation
        // SwingUtilities.invokeLater(() -> inputArea.requestFocusInWindow()); 
    }

    private void initComponents() {
        // Initialize buttons
        askButton = new JButton("Ask");
        editButton = new JButton("Edit");
        sendButton = new JButton("Send"); // Text will change based on mode

        // Initialize text areas
        inputArea = new JTextArea(10, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);

        responseArea = new JEditorPane();
        responseArea.setContentType("text/html"); // Set content type once here
        responseArea.setEditable(false);
        // For more complex markdown, a dedicated library might be needed.
        // For now, we'll assume simple HTML for responses or plain text.

        // API Key components
        apiKeyField = new JTextField(20);
        setApiKeyButton = new JButton("Set API Key");

        // Add action listeners
        askButton.addActionListener(this);
        editButton.addActionListener(this);
        sendButton.addActionListener(this);
        setApiKeyButton.addActionListener(this);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(5, 5));

        // Top panel for mode buttons and API key
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(askButton);
        topPanel.add(editButton);
        
        JPanel apiKeyPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        apiKeyPanel.add(new JLabel("API Key:"));
        apiKeyPanel.add(apiKeyField);
        apiKeyPanel.add(setApiKeyButton);
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(topPanel, BorderLayout.WEST);
        headerPanel.add(apiKeyPanel, BorderLayout.EAST);
        
        add(headerPanel, BorderLayout.NORTH);

        // Center panel for input and response
        JPanel centerPanel = new JPanel(new BorderLayout(5,5));
        centerPanel.add(new JScrollPane(inputArea), BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(responseArea), BorderLayout.CENTER);
        
        add(centerPanel, BorderLayout.CENTER);

        // Bottom panel for the send/execute button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(sendButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void updatePlaceholderText() {
        if (currentMode == Mode.ASK) {
            inputArea.setToolTipText("Type your JMeter question here...");
            // Consider using a library or a custom painter for actual placeholder text if needed
            // For simplicity, ToolTipText is used here.
            sendButton.setText("Send Query");
            askButton.setEnabled(false);
            editButton.setEnabled(true);
        } else { // EDIT Mode
            inputArea.setToolTipText("Enter a command to modify your script...");
            sendButton.setText("Execute Command");
            askButton.setEnabled(true);
            editButton.setEnabled(false);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();

        if (source == askButton) {
            currentMode = Mode.ASK;
            updatePlaceholderText();
            responseArea.setText("Switched to ASK mode."); // Clear previous response
        } else if (source == editButton) {
            currentMode = Mode.EDIT;
            updatePlaceholderText();
            responseArea.setText("Switched to EDIT mode."); // Clear previous response
        } else if (source == sendButton) {
            processInput();
        } else if (source == setApiKeyButton) {
            String key = apiKeyField.getText().trim();
            if (!key.isEmpty()) {
                apiClient.setApiKey(key);
                responseArea.setText("API Key set successfully.");
                // Optionally, you might want to verify the key with a simple API call here
            } else {
                responseArea.setText("Please enter an API Key.");
            }
        }
    }

    private void processInput() {
        String inputText = inputArea.getText().trim();
        if (inputText.isEmpty()) {
            responseArea.setText("Please enter a query or command.");
            return;
        }

        if (apiClient.getApiKey() == null || apiClient.getApiKey().isEmpty()) {
            responseArea.setText("API Key not set. Please set the API Key first.");
            return;
        }
        
        responseArea.setText("Processing..."); // Show loading state

        // Use SwingWorker for background tasks to avoid freezing the UI
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                if (currentMode == Mode.ASK) {
                    return apiClient.sendQuery(inputText, SYSTEM_PROMPT);
                } else { // EDIT Mode
                    String lowerInputText = inputText.toLowerCase();
                    if (inputText.startsWith("@")) {
                        // It's an @command
                        return handleAtCommand(inputText);
                    } else { // Not an @command, treat as general query/instruction
                        if (currentMode == Mode.ASK) {
                            return apiClient.sendQuery(inputText, SYSTEM_PROMPT);
                        } else { // EDIT Mode - general instruction, not a specific @command
                            String editSystemPrompt = SYSTEM_PROMPT +
                                "\n\n--- JMETER EDIT MODE CONTEXT ---" +
                                "\nThe user has provided a general instruction to modify the JMeter test plan." +
                                "\nUnderstand the instruction and if it's clear and safe, provide the precise JMX snippet for new/modified element(s) or specific steps." +
                                "\nIf destructive or ambiguous, ask for clarification or confirmation." +
                                "\nUser's instruction: '" + inputText + "'";
                            return apiClient.sendQuery(inputText, editSystemPrompt);
                        }
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    // Ensure content type is HTML, especially if it was changed for an error message.
                    responseArea.setContentType("text/html"); 
                    
                    // Basic HTML formatting
                    String htmlResult = result.replace("&", "&amp;")
                                              .replace("<", "&lt;")
                                              .replace(">", "&gt;")
                                              .replace("\"", "&quot;") // Handle quotes for attributes if ever needed
                                              .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;") // Preserve tabs
                                              .replace("\n", "<br>"); // Handle newlines

                    responseArea.setText("<html><body style='font-family:Monospace; font-size:10pt;'>" 
                                        + htmlResult
                                        + "</body></html>");
                    inputArea.setText(""); // Clear input area
                } catch (Exception ex) {
                    // ex.printStackTrace(); // For debugging
                    responseArea.setContentType("text/plain"); // For plain text error messages
                    responseArea.setText("Error processing request: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // Method to get the panel (e.g., to add to a frame or dialog)
    public static JPanel createPanel(ApiClient client) {
        return new JMeterCopilotPanel(client);
    }

    /*
    private String handleTuneThreadGroupCommand(String command) {
        // Example command: "Tune Thread Group 'TG Main Users' to 100 threads, 30s ramp-up, 600s duration."
        // This regex is an example and might need refinement.
        // This regex is an example and might need refinement.
        Pattern pattern = Pattern.compile(
            "Tune Thread Group '([^']*)' to (\\d+) threads, (\\d+)s ramp-up, (\\d+)s duration\\.?$", 
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(command);

        if (!matcher.find()) {
            return "Error: Could not parse Thread Group tuning command. " +
                   "Expected format: \"Tune Thread Group '[NAME]' to [N] threads, [N]s ramp-up, [N]s duration.\"";
        }

        String tgName = matcher.group(1);
        int numThreads;
        int rampUpTime;
        int duration;

        try {
            numThreads = Integer.parseInt(matcher.group(2));
            rampUpTime = Integer.parseInt(matcher.group(3));
            duration = Integer.parseInt(matcher.group(4));
        } catch (NumberFormatException e) {
            return "Error: Invalid number format in command parameters.";
        }

        if (tgName.isEmpty()) {
            return "Error: Thread Group name cannot be empty.";
        }

        GuiPackage guiPackage = JMeterTreeUtils.getGuiPackage();
        if (guiPackage == null) {
            return "Error: JMeter GUI components not available (GuiPackage is null). Cannot modify test plan.";
        }

        // Find the ThreadGroup TestElement
        org.apache.jmeter.threads.ThreadGroup threadGroupElement = 
            JMeterTreeUtils.findElementByName(tgName, org.apache.jmeter.threads.ThreadGroup.class);

        if (threadGroupElement == null) {
            return "Error: Thread Group '" + tgName + "' not found in the test plan.";
        }

        // Modify the ThreadGroup properties
        threadGroupElement.setNumThreads(numThreads);
        threadGroupElement.setRampUp(rampUpTime);
        
        threadGroupElement.setScheduler(true);
        threadGroupElement.setDuration(duration); // Duration in seconds

        HashTree currentTestPlanTree = JMeterTreeUtils.getCurrentTestPlanTree();
        if (currentTestPlanTree == null) {
             return "Error: Could not access current test plan tree. Properties partially set.";
        }
        HashTree tgSubTree = JMeterTreeUtils.findElementNodeInTree(currentTestPlanTree, threadGroupElement);

        if (tgSubTree != null) {
            boolean lcFound = false;
            for (Object subElementKey : tgSubTree.keySet()) {
                if (subElementKey instanceof LoopController) {
                    LoopController lc = (LoopController) subElementKey;
                    lc.setLoops(-1); 
                    // lc.setContinueForever(true); // setContinueForever(true) is equivalent to setLoops(-1) for LoopController
                                                 // but setLoops(-1) is the more common way for infinite with scheduler.
                                                 // LoopController.setContinueForever is actually used by ResultAction.START_NEXT_LOOP_ON_ERROR
                    lcFound = true;
                    break; 
                }
            }
            if (!lcFound) {
                 return "Error: Could not find LoopController for Thread Group '" + tgName + "'. Properties partially set.";
            }
        } else {
             return "Error: Could not find sub-tree for Thread Group '" + tgName + "' to locate LoopController. Properties partially set.";
        }
        
        JMeterUtils.runSafe(false, () -> {
            // Mark the element as dirty so JMeter knows it has changed
            // This can sometimes help, but modifying properties directly often suffices if GUI update is handled.
            // threadGroupElement.setProperty(TestElement.GUI_CLASS, threadGroupElement.getPropertyAsString(TestElement.GUI_CLASS));
            // threadGroupElement.setProperty(TestElement.TEST_CLASS, threadGroupElement.getPropertyAsString(TestElement.TEST_CLASS));
            
            org.apache.jmeter.gui.JMeterGUIComponent tgGui = guiPackage.getGui(threadGroupElement);
            if (tgGui != null) {
                tgGui.configure(threadGroupElement); 
                tgGui.modificationPerformed(); 
            }
            // JMeterTreeUtils.notifyTreeStructureChanged(); // This might be too broad if only properties changed.
                                                          // More specific nodeChanged events are better.
                                                          // For now, relying on modificationPerformed and repaint.
            guiPackage.getMainFrame().repaint(); 
        });

        return "Success: Thread Group '" + tgName + "' updated. " +
               "Threads: " + numThreads + ", Ramp-up: " + rampUpTime + "s, Duration: " + duration + "s.";
    }

    private String handleAddCsvDataSetCommand(String command) {
        // Example: "Add CSV Data Set named 'UserCreds' file 'data.csv' vars 'user,pass' under TG 'MainTG'"
        // Simpler: "Add CSV 'users.csv' (vars: user,pass) as 'User Data'" (target Test Plan)
        Pattern pattern = Pattern.compile(
            "Add CSV Data Set named '([^']*)' file '([^']*)' vars '([^']*)'(?: under (TG|Thread Group|Test Plan) '([^']*)')?$",
            Pattern.CASE_INSENSITIVE
        );
        Pattern simplerPattern = Pattern.compile(
            "Add CSV '([^']*)' \\(vars: ([^)]+)\\)(?: as '([^']*)')?(?: under (TG|Thread Group|Test Plan) '([^']*)')?$",
            Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(command);
        boolean isSimplerFormat = false;
        if (!matcher.find()) {
            matcher = simplerPattern.matcher(command);
            if (matcher.find()) {
                isSimplerFormat = true;
            } else {
                return "Error: Could not parse CSV Data Set command. Expected formats:\n" +
                       "1. \"Add CSV Data Set named '[NAME]' file '[FILEPATH]' vars '[VARS]' (under [TG|Thread Group|Test Plan] '[PARENT_NAME]')\"\n" +
                       "2. \"Add CSV '[FILEPATH]' (vars: [VARS]) (as '[NAME]') (under [TG|Thread Group|Test Plan] '[PARENT_NAME]')\"";
            }
        }

        String csvElementName, filePath, variableNames;
        String parentType = null;
        String parentName = null;

        if (isSimplerFormat) {
            filePath = matcher.group(1);
            variableNames = matcher.group(2);
            csvElementName = (matcher.group(3) != null && !matcher.group(3).isEmpty()) ? matcher.group(3) : "CSV Data for " + filePath;
            if (matcher.groupCount() >= 5 && matcher.group(4) != null && matcher.group(5) != null) { // Optional parent part
                parentType = matcher.group(4);
                parentName = matcher.group(5);
            }
        } else {
            csvElementName = matcher.group(1);
            filePath = matcher.group(2);
            variableNames = matcher.group(3);
             if (matcher.groupCount() >= 5 && matcher.group(4) != null && matcher.group(5) != null) { // Optional parent part
                parentType = matcher.group(4);
                parentName = matcher.group(5);
            }
        }

        if (filePath.isEmpty() || variableNames.isEmpty()) {
            return "Error: File path and variable names cannot be empty for CSV Data Set.";
        }

        GuiPackage guiPackage = JMeterTreeUtils.getGuiPackage();
        if (guiPackage == null) {
            return "Error: GuiPackage not available.";
        }

        CSVDataSet csvDataSet = new CSVDataSet();
        csvDataSet.setName(csvElementName);
        csvDataSet.setProperty(TestElement.GUI_CLASS, "TestBeanGUI"); // Standard for CSVDataSet
        csvDataSet.setProperty(TestElement.TEST_CLASS, CSVDataSet.class.getName());
        csvDataSet.setProperty("filename", filePath);
        csvDataSet.setProperty("variableNames", variableNames);
        // Set some sensible defaults
        csvDataSet.setProperty("delimiter", ",");
        csvDataSet.setProperty("recycle", true); // Loop on EOF
        csvDataSet.setProperty("stopThread", false); // Don't stop thread on EOF
        csvDataSet.setProperty("shareMode", "shareMode.all"); // Share among all threads
        csvDataSet.setProperty("ignoreFirstLine", false); // Assume no header by default
        csvDataSet.setEnabled(true);


        HashTree currentTestPlanTree = JMeterTreeUtils.getCurrentTestPlanTree();
        if (currentTestPlanTree == null) {
            return "Error: Could not get current test plan tree.";
        }
        
        HashTree parentNodeTreeToAddTo;
        TestElement targetParentElement = null;

        if (parentName != null && !parentName.isEmpty() && parentType != null) {
            if (parentType.equalsIgnoreCase("Test Plan")) {
                 targetParentElement = JMeterTreeUtils.findFirstElementOfType(TestPlan.class);
            } else { // Thread Group
                 targetParentElement = JMeterTreeUtils.findElementByName(parentName, org.apache.jmeter.threads.ThreadGroup.class);
            }
            if (targetParentElement == null) {
                return "Error: Specified parent '" + parentName + "' of type '" + parentType + "' not found.";
            }
            parentNodeTreeToAddTo = JMeterTreeUtils.findElementNodeInTree(currentTestPlanTree, targetParentElement);
            if (parentNodeTreeToAddTo == null) {
                 return "Error: Could not find tree node for parent element '" + parentName + "'.";
            }
        } else {
            // Default: add to Test Plan root
            targetParentElement = JMeterTreeUtils.findFirstElementOfType(TestPlan.class);
             if (targetParentElement == null) { // Should virtually never happen if a test plan is loaded
                return "Error: Could not find Test Plan root to add CSV Data Set.";
            }
            // Get the HashTree for the TestPlan itself to add the CSVDataSet directly under it
            // The TestPlan object IS the key for its own HashTree in the overall structure returned by getTestPlan()
            parentNodeTreeToAddTo = currentTestPlanTree.get(targetParentElement); 
             if (parentNodeTreeToAddTo == null) { // Also should be very unlikely
                 return "Error: Could not find tree node FOR Test Plan root.";
            }
        }

        // Add the CSVDataSet to the target parent node's HashTree
        parentNodeTreeToAddTo.add(csvDataSet);

        JMeterUtils.runSafe(false, () -> {
            // Notify JMeter about the change to the parent node's subtree.
            // This should make JMeter's GUI update and show the new element.
            // GuiPackage.getInstance().getTreeModel().nodesWereInserted(parentNode, new int[]{parentNode.getIndex(newNode)}); // More complex
            JMeterTreeUtils.notifyTreeStructureChanged(); // General refresh
        });

        return "Success: CSV Data Set '" + csvElementName + "' added for file '" + filePath + "'.";
    }

    private String handleAddUniformRandomTimerCommand(String command) {
        // Example: "Add Uniform Random Timer from 1000 to 3000 ms to Sampler 'HTTP Request Home'"
        // Alt:    "Add URT 1-3s to 'Login Sampler' as 'User Think Time'"
        Pattern pattern = Pattern.compile(
            "Add (?:Uniform Random Timer|URT) (?:from )?(\\d+)(?:ms|s)?(?: to |-)(\\d+)(?:ms|s)? to (?:Sampler )?'([^']*)'(?: as '([^']*)')?$",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(command);

        if (!matcher.find()) {
            return "Error: Could not parse Uniform Random Timer command. Expected format:\n" +
                   "\"Add URT [min](ms|s) to/- [max](ms|s) to (Sampler) '[SAMPLER_NAME]' (as '[TIMER_NAME]')\"";
        }

        String minDelayStr = matcher.group(1);
        String maxDelayStr = matcher.group(2);
        String samplerName = matcher.group(3);
        String timerName = (matcher.groupCount() >= 4 && matcher.group(4) != null && !matcher.group(4).isEmpty()) ? matcher.group(4) : "Uniform Random Timer";
        
        // Determine if values are in seconds or milliseconds
        boolean minInSeconds = command.toLowerCase().matches(".*" + minDelayStr + "\\s*s.*");
        boolean maxInSeconds = command.toLowerCase().matches(".*" + maxDelayStr + "\\s*s.*");


        long minDelayMs, maxDelayMs;
        try {
            minDelayMs = Long.parseLong(minDelayStr) * (minInSeconds ? 1000 : 1);
            maxDelayMs = Long.parseLong(maxDelayStr) * (maxInSeconds ? 1000 : 1);
        } catch (NumberFormatException e) {
            return "Error: Invalid number format for delay values.";
        }

        if (minDelayMs < 0 || maxDelayMs < 0) {
            return "Error: Delay values cannot be negative.";
        }
        if (minDelayMs > maxDelayMs) {
            return "Error: Minimum delay cannot be greater than maximum delay.";
        }

        GuiPackage guiPackage = JMeterTreeUtils.getGuiPackage();
        if (guiPackage == null) {
            return "Error: GuiPackage not available.";
        }

        TestElement targetSampler = JMeterTreeUtils.findElementByName(samplerName, TestElement.class); 

        if (targetSampler == null) {
            return "Error: Sampler '" + samplerName + "' not found in the test plan.";
        }
        // Illustrative check (optional, can be removed if causing issues or too restrictive)
        // if (!(targetSampler instanceof org.apache.jmeter.samplers.AbstractSampler) && 
        //     !(targetSampler instanceof org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy) &&
        //     !(targetSampler.getClass().getName().endsWith("Sampler")) ) {
        //      // return "Warning: Element '" + samplerName + "' found, but it might not be a Sampler. Timer added anyway.";
        // }


        UniformRandomTimer urt = new UniformRandomTimer();
        urt.setName(timerName);
        urt.setProperty(TestElement.GUI_CLASS, "UniformRandomTimerGui");
        urt.setProperty(TestElement.TEST_CLASS, UniformRandomTimer.class.getName());
        urt.setEnabled(true);

        urt.setProperty("RandomTimer.range", Double.toString(maxDelayMs - minDelayMs));
        urt.setProperty("ConstantTimer.delay", Double.toString(minDelayMs));


        HashTree timerNode = JMeterTreeUtils.addElementAsChild(targetSampler, urt);
        
        if (timerNode == null) {
            return "Error: Could not add Uniform Random Timer as a child to '" + samplerName + "'. Parent node not found in tree.";
        }

        JMeterUtils.runSafe(false, JMeterTreeUtils::notifyTreeStructureChanged);

        return "Success: Uniform Random Timer '" + timerName + "' added to Sampler '" + samplerName + 
               "' with delay " + minDelayMs + "ms to " + maxDelayMs + "ms.";
    }

    private String handleAddRegexExtractorCommand(String command) {
        // Example: "Add Regex Extractor to 'SamplerName' (as 'ExtractorName') for var 'RefName' with regex 'REGEX_HERE' template '$1$' match 1 default 'DEF_VAL'"
        // Simplified: "Add Regex on 'SamplerName' var 'RefName' regex 'REGEX_HERE'" (implies defaults for other fields)
        Pattern pattern = Pattern.compile(
            "Add Regex(?: Extractor)? to '([^']*)'(?: as '([^']*)')? for var '([^']*)' with regex '([^']*)'(?: template '([^']*)')?(?: match ([-+]?\\d+|R))?(?: default '([^']*)')?$",
            Pattern.CASE_INSENSITIVE
        );
        // Simpler pattern without optional fields explicitly named
        Pattern simplerPattern = Pattern.compile(
            "Add Regex on '([^']*)' var '([^']*)' regex '([^']*)'$",
            Pattern.CASE_INSENSITIVE
        );


        Matcher matcher = pattern.matcher(command);
        boolean usingDefaults = false;
        if (!matcher.find()) {
            matcher = simplerPattern.matcher(command);
            if (matcher.find()) {
                usingDefaults = true;
            } else {
                return "Error: Could not parse Regex Extractor command. Expected format:\n" +
                       "\"Add Regex Extractor to '[SAMPLER_NAME]' (as '[EXTR_NAME]') for var '[REF_NAME]' with regex '[REGEX]' (template '[TEMPLATE]') (match [MATCH_NUM|R]) (default '[DEFAULT_VAL]')\"\n" +
                       "Or simpler: \"Add Regex on '[SAMPLER_NAME]' var '[REF_NAME]' regex '[REGEX]'\"";
            }
        }

        String samplerName = matcher.group(1);
        String extractorName, refName, regex, template, matchNumStr, defaultVal;

        if (usingDefaults) {
            // samplerName is group(1)
            refName = matcher.group(2);
            regex = matcher.group(3);
            extractorName = "Regex Extractor for " + refName;
            template = "$1$"; // Default template
            matchNumStr = "1"; // Default match number
            defaultVal = "NOT_FOUND_" + refName; // Default value
        } else {
            // samplerName is group(1)
            extractorName = (matcher.group(2) != null && !matcher.group(2).isEmpty()) ? matcher.group(2) : "Regex Extractor for " + matcher.group(3);
            refName = matcher.group(3);
            regex = matcher.group(4);
            template = (matcher.group(5) != null) ? matcher.group(5) : "$1$";
            matchNumStr = (matcher.group(6) != null) ? matcher.group(6) : "1";
            defaultVal = (matcher.group(7) != null) ? matcher.group(7) : "NOT_FOUND";
        }

        if (samplerName.isEmpty() || refName.isEmpty() || regex.isEmpty()) {
            return "Error: Sampler name, reference name, and regex cannot be empty.";
        }

        GuiPackage guiPackage = JMeterTreeUtils.getGuiPackage();
        if (guiPackage == null) {
            return "Error: GuiPackage not available.";
        }

        TestElement targetSampler = JMeterTreeUtils.findElementByName(samplerName, TestElement.class);
        if (targetSampler == null) {
            return "Error: Sampler '" + samplerName + "' not found.";
        }

        RegexExtractor regexExtractor = new RegexExtractor();
        regexExtractor.setName(extractorName);
        regexExtractor.setProperty(TestElement.GUI_CLASS, "RegexExtractorGui");
        regexExtractor.setProperty(TestElement.TEST_CLASS, RegexExtractor.class.getName());
        regexExtractor.setEnabled(true);

        regexExtractor.setRefName(refName);
        regexExtractor.setRegex(regex);
        regexExtractor.setTemplate(template);
        
        if (matchNumStr.equalsIgnoreCase("R")) {
            regexExtractor.setMatchNumber(0); // 0 means random in RegexExtractor
        } else {
            try {
                regexExtractor.setMatchNumber(Integer.parseInt(matchNumStr));
            } catch (NumberFormatException e) {
                return "Error: Invalid match number '" + matchNumStr + "'. Must be an integer or 'R'.";
            }
        }
        regexExtractor.setDefaultValue(defaultVal);
        // regexExtractor.setScopeAll(); // Example: To apply to main sample and sub-samples. Default is "main"
        // regexExtractor.setUseField(RegexExtractor.USE_BODY); // Default is body

        HashTree extractorNode = JMeterTreeUtils.addElementAsChild(targetSampler, regexExtractor);
        if (extractorNode == null) {
            return "Error: Could not add Regex Extractor as a child to '" + samplerName + "'.";
        }

        JMeterUtils.runSafe(false, JMeterTreeUtils::notifyTreeStructureChanged);

        return "Success: Regular Expression Extractor '" + extractorName + "' added to Sampler '" + samplerName + "'.";
    }
    */

    // Refined method to handle @commands with better argument parsing
    private String handleAtCommand(String rawCommand) {
        // Trim the raw command first
        String trimmedCommand = rawCommand.trim();
        
        // Regex to separate the @command from the rest of the arguments string
        Pattern commandPattern = Pattern.compile("^(@[^\\s]+)\\s*(.*)$");
        Matcher commandMatcher = commandPattern.matcher(trimmedCommand);

        String commandName;
        String argsString = "";
        
        if (trimmedCommand.matches("^@[^\\s]+$")) { // Just @command, no arguments
            commandName = trimmedCommand.toLowerCase();
        } else if (commandMatcher.find()) {
            commandName = commandMatcher.group(1).toLowerCase(); // e.g., "@this"
            argsString = commandMatcher.group(2); // The rest of the string after the command
        } else {
            return "Error: Invalid @command format. Command should start with @.";
        }

        List<String> argsList = new ArrayList<>();
        if (!argsString.isEmpty()) {
            // Regex to match arguments: either a sequence of non-whitespace characters,
            // or a sequence of characters enclosed in double quotes.
            Pattern argsPattern = Pattern.compile("\"([^\"]*)\"|\\S+");
            Matcher argsMatcher = argsPattern.matcher(argsString);
            while (argsMatcher.find()) {
                if (argsMatcher.group(1) != null) { // Quoted argument
                    argsList.add(argsMatcher.group(1));
                } else { // Unquoted argument
                    argsList.add(argsMatcher.group());
                }
            }
        }
        String[] args = argsList.toArray(new String[0]);

        // Basic intellisense/help display if only "@" or "@co" etc. is typed
        // This is triggered on "Execute" for now.
        if (commandName.equals("@") && args.length == 0 && trimmedCommand.length() == 1) {
             return getAvailableAtCommandsHint();
        }
        // Could also check if commandName starts with "@" and is very short, then provide hints
        // e.g. if (commandName.startsWith("@") && commandName.length() < 4 && args.length == 0) { ... }


        // Dispatch to specific @command handlers
        switch (commandName) {
            case "@this":
                return handleThisCommand(); // Expects no args usually
            case "@code":
                return handleCodeCommand(args);
            case "@usage":
                return handleUsageCommand(args); // Expects component name
            case "@lint":
                return handleLintCommand(args); // May take optional scope args
            case "@optimize":
                return handleOptimizeCommand(args); // May take optional scope args
            case "@wrap":
                return handleWrapCommand(args); // Expects TC name and sampler names
            case "@jsr223_refactor":
                return handleJsr223RefactorCommand(args);
            case "@jsr223_suggest_function":
                return handleJsr223SuggestFunctionCommand(args);
            case "@jsr223_apply_script":
                return handleJsr223ApplyScriptCommand(); // No args for this one
            case "@help": // New @help command
                return getAvailableAtCommandsHint();
            default:
                // If the command is like "@somet" and not a full command, show suggestions
                if (trimmedCommand.endsWith(" ") || trimmedCommand.equals(commandName)) { // User might be pausing
                    String suggestions = getAvailableAtCommandsHint(commandName);
                    if (!suggestions.startsWith("Available")) { // Found specific suggestions
                        return "Did you mean? \n" + suggestions;
                    }
                }
                return "Error: Unknown command '" + commandName + "'. Type @help for available commands.";
        }
    }

    private String getAvailableAtCommandsHint() {
        return getAvailableAtCommandsHint(""); // Get all commands
    }

    private String getAvailableAtCommandsHint(String prefixFilter) {
        StringBuilder hint = new StringBuilder("Available @commands:\n");
        List<String> commands = Arrays.asList(
                "@this - Get info about the selected JMeter element.",
                "@code [optional: instruction] - Extract code from last AI response or ask AI to generate code.",
                "@usage <JMeter Component Name> - Get usage examples for a component.",
                "@lint [optional: scope] - Analyze and suggest improvements for test plan structure/naming.",
                "@optimize [optional: scope] - Get optimization recommendations for selected element or scope.",
                "@wrap <TransactionName> <SamplerName1> [SamplerName2...] - Wrap samplers in a Transaction Controller.",
                "@jsr223_refactor [optional: instructions] - Asks AI to refactor script of selected JSR223 element.",
                "@jsr223_suggest_function <description> - Asks AI to suggest a JSR223 utility function.",
                "@jsr223_apply_script - Applies the script from last AI refactor/suggestion to selected JSR223 element.",
                "@help - Shows this help message."
        );
        int count = 0;
        for (String cmdDesc : commands) {
            if (prefixFilter.isEmpty() || cmdDesc.toLowerCase().startsWith(prefixFilter.toLowerCase())) {
                 hint.append("- ").append(cmdDesc).append("\n");
                 count++;
            }
        }
        if (count == 0 && !prefixFilter.isEmpty()) {
            return "No commands found matching '"+prefixFilter+"'. Type @help for all commands.";
        }
        return hint.toString();
    }
    
    // Placeholder for @this command handler (to be implemented in Step 3)
    private String handleThisCommand() {
        GuiPackage guiPackage = JMeterTreeUtils.getGuiPackage();
        if (guiPackage == null) {
            return "Error: GuiPackage not available. Cannot access current selection.";
        }

        JMeterTreeNode selectedNode = guiPackage.getTreeListener().getCurrentNode();
        // Note: getCurrentNode() can return null if the tree itself is selected or no node.
        // It can also return nodes that are not TestElement nodes (e.g. non-configured elements or non-GUI elements).
        // For actual TestPlan elements, selectedNode.getUserObject() should be a TestElement.
        
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof TestElement)) {
            return "Error: No valid JMeter element selected in the tree, or selection is not a TestElement.";
        }

        TestElement selectedElement = (TestElement) selectedNode.getUserObject();

        StringBuilder info = new StringBuilder("<html><body>");
        info.append("<h3>Selected Element Information:</h3>");
        info.append("<table border='1' style='font-family:Monospace; font-size:10pt;'>");
        info.append("<tr><td><b>Property</b></td><td><b>Value</b></td></tr>");

        appendProperty(info, "Name", selectedElement.getName());
        appendProperty(info, "Class", selectedElement.getClass().getName());
        appendProperty(info, "Enabled", Boolean.toString(selectedElement.isEnabled()));
        
        // Iterate over element properties for more details
        info.append("<tr><td colspan='2'><b>All Properties:</b></td></tr>");
        PropertyIterator iter = selectedElement.propertyIterator();
        while (iter.hasNext()) {
            JMeterProperty prop = iter.next();
            appendProperty(info, prop.getName(), prop.getStringValue());
        }
        
        info.append("</table></body></html>");
        return info.toString();
    }

    private void appendProperty(StringBuilder builder, String name, String value) {
        builder.append("<tr><td>").append(htmlEscape(name)).append("</td><td>")
               .append(htmlEscape(value == null ? "null" : value))
               .append("</td></tr>");
    }

    // Helper to escape HTML characters for display
    private String htmlEscape(String input) {
        if (input == null) return "null";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }

    // Placeholder for @code command handler
    private String handleCodeCommand(String[] args) {
        if (args.length == 0) {
            return "Error: `@code` command requires an instruction. Example: `@code generate JSR223 script to log 'hello'`";
        }
        String instruction = String.join(" ", args);

        String codeGenPrompt = "Generate a code snippet based on the following instruction. " +
                               "The code should be relevant to JMeter (e.g., JSR223 script, JMX snippet, Java). " +
                               "Instruction: '" + instruction + "'. " +
                               "Ensure the code is enclosed in appropriate Markdown code blocks (e.g., ```groovy ... ``` or ```xml ... ```).";
        
        return apiClient.sendQuery(codeGenPrompt, SYSTEM_PROMPT + 
            "\nThe user is asking for code generation related to JMeter.");
    }

    // Placeholder for @usage command handler
    private String handleUsageCommand(String[] args) {
        if (args.length == 0) {
            return "Error: `@usage` command requires a JMeter component name. Example: `@usage ThreadGroup`";
        }
        String componentName = String.join(" ", args); // Join args if component name has spaces

        // Prepare a prompt for the AI
        String usagePrompt = "Provide usage examples, common configurations, and best practices for the JMeter component: '" +
                             componentName + 
                             "'. Explain its purpose and key properties clearly. Format the response in Markdown.";
        
        return apiClient.sendQuery(usagePrompt, SYSTEM_PROMPT + 
            "\nThe user is asking for usage information about a JMeter component.");
    }
    
    // Placeholder for @lint command handler
    private String handleLintCommand(String[] args) {
        if (args.length > 0 && "apply_changes".equalsIgnoreCase(args[0])) {
            if (proposedNameChanges == null || proposedNameChanges.isEmpty()) {
                return "No pending naming changes to apply. Run `@lint` first to scan.";
            }
            return applyProposedNameChanges();
        }
        if (args.length > 0 && "cancel".equalsIgnoreCase(args[0])) {
            proposedNameChanges = null;
            return "Naming changes cancelled.";
        }

        // Scan for naming convention issues
        proposedNameChanges = new ArrayList<>();
        // Ensure JMeterTreeUtils.findAllElementsOfType is available and works as expected.
        // It should return all TestElements, which we then filter.
        List<TestElement> allElements = JMeterTreeUtils.findAllElementsOfType(TestElement.class); 
        if (allElements.isEmpty()) {
            return "No elements found in the test plan to lint.";
        }

        int changesSuggested = 0;
        StringBuilder suggestionsHtml = new StringBuilder("<html><body><h3>Proposed Naming Convention Changes:</h3>");
        suggestionsHtml.append("<p>Review the changes below. To apply, type: <code>@lint apply_changes</code>. To cancel, type: <code>@lint cancel</code>.</p>");
        suggestionsHtml.append("<table border='1' style='font-family:Monospace; font-size:10pt;'><tr><th>Current Name</th><th>Proposed New Name</th><th>Type</th></tr>");

        for (TestElement element : allElements) {
            // Skip the TestPlan root itself, unnamed elements, or elements that are not enabled (optional, consider if needed)
            if (element instanceof TestPlan || element.getName() == null || element.getName().isEmpty() /*|| !element.isEnabled()*/ ) {
                continue;
            }
            String currentName = element.getName();
            String typePrefix = getPrefixForElementType(element);
            
            // More robust base name cleaning: remove known prefixes case-insensitively
            String baseName = currentName;
            if (!typePrefix.isEmpty()) { // Attempt to remove current prefix if it matches the new one
                if (currentName.toLowerCase().startsWith(typePrefix.toLowerCase())) {
                    baseName = currentName.substring(typePrefix.length());
                }
            }
            // Also remove other common prefixes to avoid duplication like CTRL_Sampler_MySampler
            baseName = baseName.replaceAll("^(?i)(HTTP|JDBC|TCP|Debug|JSR223|BSF|CSV|FTP|LDAP|MAIL|MONGO|OS|SMTP|DNS|GRAPHITE|JAVA|REGEX|XPATH|JSON|CSS|MD5|SMIME|BEAN_SHELL|CONSTANT|COUNTER|RANDOM|USER_DEFINED|HTML_PARAMETER|RESULT_STATUS_ACTION|DURATION|RESPONSE_TIME|SIZE|COMPARE|JSR223_PRE|JSR223_POST|JDBC_PRE|JDBC_POST|BSF_PRE|BSF_POST|DEBUG_POST|EXTRACT_TEXT|REGEX_EXTRACTOR|JSON_EXTRACTOR|XPATH_EXTRACTOR|CSS_SELECTOR_EXTRACTOR|BOUNDARY_EXTRACTOR|RESULT_SAVER|VIEW_RESULTS_TREE|SUMMARY_REPORT|AGGREGATE_REPORT|TABLE_VISUALIZER|GRAPH_VISUALIZER|SPLINE_VISUALIZER|ASSERTION_RESPONSE|ASSERTION_JSON|ASSERTION_XML|ASSERTION_XPATH|ASSERTION_HTML|CONSTANT_TIMER|UNIFORM_RANDOM_TIMER|GAUSSIAN_RANDOM_TIMER|POISSON_RANDOM_TIMER|BSF_TIMER|JSR223_TIMER|BEAN_SHELL_TIMER|SYNC_TIMER|LOOP_CONTROLLER|IF_CONTROLLER|WHILE_CONTROLLER|FOREACH_CONTROLLER|INCLUDE_CONTROLLER|MODULE_CONTROLLER|RANDOM_CONTROLLER|RANDOM_ORDER_CONTROLLER|SWITCH_CONTROLLER|THROUGHPUT_CONTROLLER|TRANSACTION_CONTROLLER|INTERLEAVE_CONTROLLER|ONCE_ONLY_CONTROLLER|RUNTIME_CONTROLLER|CRITICAL_SECTION_CONTROLLER|THREAD_GROUP|SETUP_THREAD_GROUP|TEARDOWN_THREAD_GROUP|CONFIG_TEST_ELEMENT|CSV_DATA_SET|HTTP_AUTHORIZATION_MANAGER|HTTP_CACHE_MANAGER|HTTP_COOKIE_MANAGER|HTTP_HEADER_MANAGER|HTTP_REQUEST_DEFAULTS|DNS_CACHE_MANAGER|FTP_REQUEST_DEFAULTS|JDBC_CONNECTION_CONFIGURATION|KEYSTORE_CONFIGURATION|LOGIN_CONFIG|LDAP_EXTENDED_REQUEST_DEFAULTS|LDAP_REQUEST_DEFAULTS|TCP_SAMPLER_CONFIG|USER_DEFINED_VARIABLES)_", "");
            baseName = baseName.trim();


            String newName = typePrefix + baseName;

            if (!currentName.equals(newName) && !typePrefix.isEmpty() && !baseName.isEmpty()) {
                proposedNameChanges.add(new ProposedNameChange(element, currentName, newName));
                suggestionsHtml.append("<tr><td>").append(htmlEscape(currentName)).append("</td><td>")
                               .append(htmlEscape(newName)).append("</td><td>")
                               .append(element.getClass().getSimpleName()).append("</td></tr>");
                changesSuggested++;
            }
        }

        if (changesSuggested == 0) {
            proposedNameChanges = null; // Clear if no changes
            return "No naming convention changes suggested. Your test plan looks well-named according to basic conventions!";
        }

        suggestionsHtml.append("</table></body></html>");
        return suggestionsHtml.toString();
    }

    private String getPrefixForElementType(TestElement element) {
        // More specific types should come first
        if (element instanceof org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy) return "HTTP_";
        if (element instanceof org.apache.jmeter.protocol.java.sampler.JavaSampler) return "JAVA_";
        if (element instanceof org.apache.jmeter.protocol.jdbc.sampler.JDBCSampler) return "JDBC_";
        if (element instanceof org.apache.jmeter.protocol.ftp.sampler.FTPSampler) return "FTP_";
        // Add other specific samplers here

        if (element instanceof AbstractThreadGroup) return "TG_";
        if (element instanceof org.apache.jmeter.control.LoopController) return "LoopCTRL_";
        if (element instanceof org.apache.jmeter.control.IfController) return "IfCTRL_";
        if (element instanceof org.apache.jmeter.control.TransactionController) return "TransCTRL_";
        // Add other specific controllers

        if (element instanceof Sampler) return "Sampler_"; 
        if (element instanceof Controller) return "CTRL_"; 
        
        if (element instanceof org.apache.jmeter.config.CsvDataSet) return "CSV_"; // Corrected class name
        if (element instanceof org.apache.jmeter.protocol.http.control.HeaderManager) return "HTTP_HeaderManager_";
        if (element instanceof org.apache.jmeter.protocol.http.control.CookieManager) return "HTTP_CookieManager_";
        if (element instanceof org.apache.jmeter.protocol.http.control.CacheManager) return "HTTP_CacheManager_";
        if (element instanceof ConfigElement) return "Config_";
        
        if (element instanceof org.apache.jmeter.timers.UniformRandomTimer) return "URT_";
        if (element instanceof org.apache.jmeter.timers.ConstantTimer) return "ConstTimer_";
        if (element instanceof Timer) return "Timer_";
        
        if (element instanceof org.apache.jmeter.assertions.ResponseAssertion) return "Assert_Response_";
        if (element instanceof org.apache.jmeter.assertions.JSONPathAssertion) return "Assert_JSON_"; // Corrected class name if it exists
        if (element instanceof Assertion) return "Assert_";
        
        if (element instanceof org.apache.jmeter.extractor.RegexExtractor) return "RegexEx_";
        if (element instanceof org.apache.jmeter.extractor.json.jsonpath.JSONPathExtractor) return "JSONEx_";
        if (element instanceof PostProcessor) return "PostProc_";
        if (element instanceof PreProcessor) return "PreProc_";
        
        if (element instanceof org.apache.jmeter.visualizers.ViewResultsFullVisualizer) return "ViewResultsTree_";
        // Corrected how VRT might be identified if it's a ResultCollector
        if (element instanceof org.apache.jmeter.reporters.ResultCollector && 
            "org.apache.jmeter.visualizers.ViewResultsFullVisualizer".equals(element.getPropertyAsString(TestElement.GUI_CLASS))) {
            return "ViewResultsTree_"; 
        }
        if (element instanceof org.apache.jmeter.visualizers.SummaryReport) return "SummaryReport_";
        if (element instanceof Visualizer) return "Listener_";
        
        return ""; 
    }

    private String applyProposedNameChanges() {
        if (proposedNameChanges == null || proposedNameChanges.isEmpty()) {
            return "No changes to apply.";
        }

        int appliedCount = 0;
        GuiPackage guiPackage = JMeterTreeUtils.getGuiPackage();

        for (ProposedNameChange change : proposedNameChanges) {
            change.element.setName(change.newName);
            if (guiPackage != null) {
                // Notify the specific GUI component if possible
                org.apache.jmeter.gui.JMeterGUIComponent elementGui = guiPackage.getGui(change.element);
                if (elementGui != null) {
                    elementGui.configure(change.element); // Re-read config from TestElement
                    elementGui.modificationPerformed();   // Notify GUI of change
                }
            }
            appliedCount++;
        }
        
        final int finalAppliedCount = appliedCount;
        // General notification to refresh the tree
        JMeterUtils.runSafe(false, JMeterTreeUtils::notifyTreeStructureChanged);
        
        proposedNameChanges = null; // Clear after applying
        return "Successfully applied " + finalAppliedCount + " naming changes.";
    }

    // Placeholder for @optimize command handler
    private String handleOptimizeCommand(String[] args) {
        GuiPackage guiPackage = JMeterTreeUtils.getGuiPackage();
        if (guiPackage == null) {
            return "Error: GuiPackage not available. Cannot access current selection.";
        }

        JMeterTreeNode selectedNode = guiPackage.getTreeListener().getCurrentNode();
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof TestElement)) {
            return "Error: No valid JMeter element selected. Please select an element in the tree to optimize.";
        }

        TestElement selectedElement = (TestElement) selectedNode.getUserObject();
        String elementName = selectedElement.getName();
        String elementType = selectedElement.getClass().getSimpleName();

        // Gather properties of the selected element to send to the AI
        StringBuilder propertiesDetail = new StringBuilder();
        propertiesDetail.append("Element Name: '").append(elementName).append("'.\n");
        propertiesDetail.append("Element Type: '").append(elementType).append("'.\n");
        propertiesDetail.append("Enabled: ").append(selectedElement.isEnabled()).append(".\n");
        propertiesDetail.append("Properties:\n");

        PropertyIterator iter = selectedElement.propertyIterator();
        int propCount = 0;
        while (iter.hasNext()) {
            JMeterProperty prop = iter.next();
            // Avoid sending overly verbose or internal-looking properties if possible,
            // but for a general approach, sending most is fine.
            // We can filter out some common "non-user-configurable" ones if needed.
            if (!prop.getName().equals(TestElement.GUI_CLASS) && 
                !prop.getName().equals(TestElement.TEST_CLASS) &&
                !prop.getName().equals(TestElement.COMMENTS)) { // Example filter
                propertiesDetail.append("- ").append(prop.getName()).append(": '")
                                .append(prop.getStringValue()).append("'.\n");
                propCount++;
            }
        }
        if (propCount == 0) {
             propertiesDetail.append("(No significant user-configurable properties found or all are default).\n");
        }


        // Construct the prompt for the AI
        String optimizePrompt = "I have a JMeter element with the following details:\n" +
                                propertiesDetail.toString() +
                                "\nPlease provide optimization suggestions, performance best practices, " +
                                "or checks for common pitfalls related to this specific JMeter element type and its current configuration. " +
                                "Focus on actionable advice. Format the response in Markdown with clear bullet points.";

        // The SYSTEM_PROMPT already establishes expertise. We add context about the task.
        return apiClient.sendQuery(optimizePrompt, SYSTEM_PROMPT +
            "\n\nThe user is asking for optimization advice for a specific JMeter element based on its properties.");
    }

    // Placeholder for @wrap command handler
    private String handleWrapCommand(String[] args) {
        if (args.length < 2) {
            return "Error: `@wrap` command requires a Transaction Controller name and at least one Sampler name.\n" +
                   "Example: `@wrap \"Login Transaction\" Sampler1 Sampler2`";
        }

        String transactionControllerName = args[0];
        List<String> samplerNamesToWrap = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            samplerNamesToWrap.add(args[i]);
        }

        if (transactionControllerName.isEmpty()) {
            return "Error: Transaction Controller name cannot be empty.";
        }
        if (samplerNamesToWrap.isEmpty()) {
            return "Error: At least one Sampler name must be provided to wrap.";
        }

        GuiPackage guiPackage = JMeterTreeUtils.getGuiPackage();
        if (guiPackage == null) {
            return "Error: GuiPackage not available.";
        }

        HashTree testPlanTree = JMeterTreeUtils.getCurrentTestPlanTree();
        if (testPlanTree == null) {
            return "Error: Could not access the current test plan tree.";
        }

        List<TestElement> samplers = new ArrayList<>();
        for (String samplerName : samplerNamesToWrap) {
            TestElement sampler = JMeterTreeUtils.findElementByName(samplerName, TestElement.class);
            if (sampler == null) {
                return "Error: Sampler '" + samplerName + "' not found.";
            }
            samplers.add(sampler);
        }
        
        if (samplers.isEmpty()) { 
            return "Error: No samplers specified or found.";
        }

        HashTree commonParentTree = JMeterTreeUtils.findCommonParentTreeForElements(testPlanTree, samplers);

        if (commonParentTree == null) {
            return "Error: Could not find a common parent for all specified samplers, or samplers are not siblings under the same direct parent. Wrapping aborted.";
        }
        
        TransactionController tc = new TransactionController();
        tc.setName(transactionControllerName);
        tc.setProperty(TestElement.GUI_CLASS, "TransactionControllerGui");
        tc.setProperty(TestElement.TEST_CLASS, TransactionController.class.getName());
        tc.setEnabled(true);
        tc.setProperty(TransactionController.GENERATE_PARENT_SAMPLE, true); 
        tc.setProperty(TransactionController.INCLUDE_TIMERS, false); 

        // Add the new Transaction Controller to the common parent tree.
        // The returned tcSubTree is the HashTree *associated with* the TC (i.e., where its children will go)
        HashTree tcSubTree = commonParentTree.add(tc); 

        // Move the samplers: remove from commonParentTree, add to tcSubTree
        for (TestElement samplerToMove : samplers) {
            if (commonParentTree.containsKey(samplerToMove)) {
                 commonParentTree.remove(samplerToMove); 
            } else {
                // This case implies the sampler was not a direct child of commonParentTree,
                // which should have been caught by findCommonParentTreeForElements.
                // Or, it might be nested deeper. For simplicity, try a general remove.
                JMeterTreeUtils.removeElementCorrected(samplerToMove); 
            }
            tcSubTree.add(samplerToMove); 
        }

        JMeterUtils.runSafe(false, JMeterTreeUtils::notifyTreeStructureChanged);

        return "Success: Samplers wrapped in Transaction Controller '" + transactionControllerName + "'.";
    }

    private TestElement getCurrentSelectedTestElement() {
        GuiPackage guiPackage = JMeterTreeUtils.getGuiPackage();
        if (guiPackage == null) {
            // Avoid direct UI update from non-EDT thread if called from doInBackground
            // Return null and let the caller handle messaging or use SwingUtilities.invokeLater
            return null; 
        }
        JMeterTreeNode selectedNode = guiPackage.getTreeListener().getCurrentNode();
        if (selectedNode != null && selectedNode.getUserObject() instanceof TestElement) {
            return (TestElement) selectedNode.getUserObject();
        }
        return null;
    }

    private boolean isJsr223Element(TestElement element) {
        if (element == null) return false;
        if (element instanceof JSR223TestElement) return true;
        if (element instanceof TestBean) {
            PropertyIterator iter = element.propertyIterator();
            while (iter.hasNext()) {
                if (JSR223TestElement.SCRIPT.equals(iter.next().getName())) {
                    return true;
                }
            }
        }
        return false; 
    }
    
    private String getScriptFromJsr223Element(TestElement element) {
        if (isJsr223Element(element)) {
            return element.getPropertyAsString(JSR223TestElement.SCRIPT);
        }
        return null;
    }

    private String extractScriptFromMarkdown(String markdown, String language) {
        if (markdown == null) return null;
        String langPatternPart = (language != null && !language.isEmpty()) ? Pattern.quote(language.toLowerCase()) + "|" : "";
        
        Pattern pattern = Pattern.compile("```(?:groovy|java|javascript|" + langPatternPart + ")?\\s*\n(.*?)\n```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            return matcher.group(1).trim(); 
        }
        pattern = Pattern.compile("```\s*\n(.*?)\n```", Pattern.DOTALL);
        matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String handleJsr223RefactorCommand(String[] args) {
        TestElement selectedElement = getCurrentSelectedTestElement();
        if (selectedElement == null || !isJsr223Element(selectedElement)) {
            return "Error: Please select a JSR223-based element (Sampler, Assertion, Timer, etc.) first.";
        }

        String script = getScriptFromJsr223Element(selectedElement);
        if (script == null || script.isEmpty()) {
            if (script != null && script.trim().isEmpty()){
                 return "The selected JSR223 element's script is empty or contains only whitespace. Nothing to refactor.";
            }
            return "Error: The selected JSR223 element has no script content to refactor, or it could not be read.";
        }
        
        String language = selectedElement.getPropertyAsString(JSR223TestElement.LANGUAGE);
        if (language == null || language.isEmpty()) language = "groovy";

        String refactorInstructions = "Refactor the following " + language + 
                                      " script for clarity, efficiency, and JMeter best practices. " +
                                      "Preserve existing functionality and variables referenced by JMeter (e.g., vars, props, log, OUT, prev, sampler, Label, FileName, Parameters, args).";
        if (args.length > 0) {
            refactorInstructions += "\nUser's specific refactoring instructions: " + String.join(" ", args);
        }
        
        String prompt = refactorInstructions + "\n\nScript to refactor:\n```" + language + "\n" +
                        script + "\n```" +
                        "\nEnsure the response contains only the refactored script within a single Markdown code block using the same language specified ("+language+"). Do not add any explanatory text outside the code block unless it's a comment within the script itself.";
        
        lastAiScriptResponse = null; 
        String aiResponse = apiClient.sendQuery(prompt, SYSTEM_PROMPT + "\nThe user wants to refactor a JSR223 script.");
        lastAiScriptResponse = aiResponse; 
        
        return aiResponse + "\n\nTip: If the response contains a valid script, you can use `@jsr223_apply_script` to update the selected element.";
    }

    private String handleJsr223SuggestFunctionCommand(String[] args) {
        TestElement selectedElement = getCurrentSelectedTestElement();
        String language = "groovy"; 
        if (selectedElement != null && isJsr223Element(selectedElement)) {
            String currentLanguage = selectedElement.getPropertyAsString(JSR223TestElement.LANGUAGE);
            if (currentLanguage != null && !currentLanguage.isEmpty()) {
                language = currentLanguage;
            }
        }

        if (args.length == 0) {
            return "Error: Please describe the function you need. Example: `@jsr223_suggest_function generate a random number between X and Y`";
        }
        String functionDescription = String.join(" ", args);
        String prompt = "Suggest a " + language + " utility function for use in a JMeter JSR223 element. " +
                        "The function should accomplish the following: '" + functionDescription + "'. " +
                        "Provide only the function code within a Markdown code block using the language '"+language+"'. " +
                        "Include necessary imports if any, inside the function or script block. Add brief Javadoc-style comments explaining parameters and return value.";
        
        lastAiScriptResponse = null; 
        String aiResponse = apiClient.sendQuery(prompt, SYSTEM_PROMPT + "\nThe user needs a utility function for a JSR223 script.");
        lastAiScriptResponse = aiResponse; 
        
        return aiResponse + "\n\nTip: You can copy the function. If the AI provides a full script meant to replace the current one, `@jsr223_apply_script` might be usable.";
    }
    
    private String handleJsr223ApplyScriptCommand() {
        if (lastAiScriptResponse == null || lastAiScriptResponse.isEmpty() || lastAiScriptResponse.startsWith("Error:")) {
            return "Error: No valid AI-generated script found from the last relevant command (e.g. @jsr223_refactor).";
        }

        TestElement selectedElement = getCurrentSelectedTestElement();
        if (selectedElement == null || !isJsr223Element(selectedElement)) {
            return "Error: Please select a JSR223-based element to apply the script to.";
        }
        
        String language = selectedElement.getPropertyAsString(JSR223TestElement.LANGUAGE);
        if (language == null || language.isEmpty()) language = "groovy";

        String scriptToApply = extractScriptFromMarkdown(lastAiScriptResponse, language);

        if (scriptToApply == null) {
            return "Error: Could not extract a valid script from the last AI response. Ensure the AI returned the script in a markdown code block (e.g., ```groovy ... ```). Last response was:\n" + htmlEscape(lastAiScriptResponse);
        }

        selectedElement.setProperty(JSR223TestElement.SCRIPT, scriptToApply);

        GuiPackage guiPackage = JMeterTreeUtils.getGuiPackage();
        if (guiPackage != null) {
            org.apache.jmeter.gui.JMeterGUIComponent elementGui = guiPackage.getGui(selectedElement);
            if (elementGui != null) {
                elementGui.configure(selectedElement); 
                elementGui.modificationPerformed();   
            }
            JMeterTreeUtils.notifyTreeStructureChanged(); 
        }
        lastAiScriptResponse = null; 
        return "Success: Script applied to element '" + selectedElement.getName() + "'.";
    }

    // Remove or comment out the old command handlers like:
    // private String handleTuneThreadGroupCommand(String command) { ... }
    // private String handleAddCsvDataSetCommand(String command) { ... }
    // private String handleAddUniformRandomTimerCommand(String command) { ... }
    // private String handleAddRegexExtractorCommand(String command) { ... }
    // These will be replaced by functionality invoked via @commands or more general AI interaction.
}
