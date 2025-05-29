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
import org.apache.jmeter.testelement.TestElement;
// Note: Using the specific ThreadGroup class from org.apache.jmeter.threads
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.apache.jmeter.config.CSVDataSet;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.timers.UniformRandomTimer;
import org.apache.jmeter.samplers.Sampler; // To identify generic samplers
import org.apache.jmeter.extractor.RegexExtractor;


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
                    if (lowerInputText.startsWith("tune thread group")) {
                        return handleTuneThreadGroupCommand(inputText);
                    } else if (lowerInputText.startsWith("add csv data set") || lowerInputText.startsWith("add csv")) {
                        return handleAddCsvDataSetCommand(inputText);
                    } else if (lowerInputText.startsWith("add uniform random timer") || lowerInputText.startsWith("add urt")) {
                        return handleAddUniformRandomTimerCommand(inputText);
                    } else if (lowerInputText.startsWith("add regex extractor") || lowerInputText.startsWith("add regex")) {
                        return handleAddRegexExtractorCommand(inputText);
                    }
                    // Add more command routing else ifs here
                    
                    // Fallback to general AI processing for other edit commands
                    String editSystemPrompt = SYSTEM_PROMPT + // Fallback to general AI processing
                        "\n\n--- JMETER EDIT MODE CONTEXT ---" +
                        "\nThe user has issued a command to modify the JMeter test plan." +
                        "\nYour primary goal is to help the user achieve this by:" +
                        "\n1. Understanding the command and identifying the target JMeter elements and desired changes." +
                        "\n2. If the command is clear and safe, provide the precise JMX snippet for the new/modified element(s) or specific instructions on what to change. Ensure the JMX is well-formed and complete for the element in question." +
                        "\n3. If the command implies a destructive action (e.g., deleting elements, overwriting significant configurations), clearly state what will be changed and explicitly ask the user for confirmation (e.g., 'This will delete X. Proceed? Reply Yes/No.')." +
                        "\n4. If the command is ambiguous or lacks detail, ask clarifying questions." +
                        "\n5. When providing JMX, ensure it's enclosed in appropriate markers like ```jmx ... ``` or clearly identifiable as JMX code." +
                        "\nUser's command: '" + inputText + "'"; // Include user's command for context to AI
                    return apiClient.sendQuery(inputText, editSystemPrompt);
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
}
