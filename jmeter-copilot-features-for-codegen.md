# JMeter Copilot Plugin: Feature Summary for Code Generation

**Plugin Name:** JMeter Copilot

**Objective:** To create an intelligent JMeter plugin that assists performance engineers in real-time script authoring, debugging, and enhancement by integrating with a Generative AI.

## I. Core UI Design (JMeter Plugin - Java Swing)

*   **Toolbar Integration:**
    *   An icon (e.g., 🤖 or 🚀, standard JMeter icon size) added to the JMeter main toolbar (e.g., using `org.apache.jmeter.gui.plugin.ToolbarPlugin`).
    *   **Tooltip:** "JMeter Copilot".
    *   **Action:** Single click toggles the visibility of a right-side docked panel. The panel should be a `javax.swing.JComponent`.

*   **Side Panel (e.g., `javax.swing.JPanel` with appropriate layout manager):**
    *   Resizable, docked to the right of the JMeter window. Standard JMeter plugin panel integration.
    *   **Header:** A `JLabel` displaying "JMeter Copilot".
    *   **Mode Selection:**
        *   Two prominent `javax.swing.JButton` instances at the top: "[A] Ask" and "[E] Edit".
        *   Clear visual indication of the active mode (e.g., button appearance change, background color change of input area).
    *   **Input Area:**
        *   A multi-line `javax.swing.JTextArea` (wrapped in a `JScrollPane`) for user queries/commands.
        *   Placeholder text (e.g., using a utility class like `TextComponentUIEnhancer.setDefault엷GrayPlaceholder`) should adapt to the selected mode (e.g., "Ask a question..." or "Enter a command to modify script...").
        *   A `javax.swing.JButton` ("Send" or "Execute", text changes with mode) to submit input.
    *   **Response Viewer:**
        *   A display area below the input, occupying remaining panel space (e.g., `javax.swing.JTextPane` or `javax.swing.JEditorPane` wrapped in a `JScrollPane`).
        *   **Rendering Capabilities:**
            *   Formatted Markdown (requires a Java Markdown library like `flexmark-java` to convert Markdown to HTML, then display in `JEditorPane` with `setContentType("text/html")`).
            *   Syntax-highlighted code snippets (JMX, Groovy, Java). This might involve using HTML `<pre><code>` tags with CSS for basic formatting, or integrating a more sophisticated Java syntax highlighting library if feasible.
            *   Interactive elements:
                *   "Copy to Clipboard" buttons (`JButton`) next to code/JMX snippets.
                *   "Apply Changes" / "Cancel" buttons (`JButton`) for proposed changes in "Edit" mode, displayed conditionally.

## II. Backend GenAI Integration

*   **Communication Client (Java):**
    *   A dedicated Java class (e.g., `GenAIClient`) to handle communication with the GenAI service endpoint.
    *   Use standard Java HTTP client libraries (e.g., `java.net.http.HttpClient` or Apache HttpClient).
    *   Method to send HTTP POST requests with JSON payloads.
    *   Payload structure: `{"model": "gpt-3.5-turbo", "messages": [{"role": "system", "content": "You are a performance tool JMeter expert..."}, {"role": "user", "content": "User's query..."}]}` (example).
    *   Parse JSON responses (e.g., using `org.json` or `Gson/Jackson`) to extract AI-generated content (e.g., `response.choices[0].message.content`).
*   **API Key Management:**
    *   A `JTextField` or `JPasswordField` in the Copilot side panel (perhaps under a "Settings" cog icon/button) for users to input their GenAI API access token.
    *   Store the key securely, potentially using JMeter properties (`JMeterUtils.setProperty`, `JMeterUtils.getProperty`) or Java Preferences API if persistence across JMeter sessions is desired (less secure for sensitive tokens).
    *   Use the token in the `Authorization: Bearer <token>` HTTP header for API requests.
*   **GenAI Capabilities Assumed by Plugin Logic:**
    *   Understanding of JMeter concepts, terminology, element properties, and best practices.
    *   Ability to generate valid JMX snippets for common JMeter elements.
    *   Ability to generate code examples (Groovy for JSR223 elements, Java for plugin development if meta-programming is asked).
    *   Ability to provide explanatory text in Markdown format.
    *   Ability to interpret natural language commands for script modification and provide structured output if prompted (e.g., JSON describing JMX changes).

## III. "Ask" Mode Functionality

*   **Purpose:** Answer user questions, clarify doubts, explain JMeter concepts, provide scripting examples.
*   **Workflow:**
    1.  User selects "Ask" mode. UI updates (input placeholder, button text).
    2.  User types a question into the `JTextArea` and clicks "Send".
    3.  Plugin constructs the appropriate JSON payload (including system prompt and user query).
    4.  Plugin calls the `GenAIClient` to send the query to the GenAI backend.
    5.  On response, extract content (Markdown, code, JMX).
    6.  Render the formatted response in the response viewer `JTextPane/JEditorPane`.
*   **Example Queries (as detailed in design document Section 4.C):**
    *   "What is correlation in JMeter and why is it important?"
    *   "How can I test a login API that uses CSRF tokens?" (expects JMX/code examples).
    *   "Why is my response time increasing after 10 users?"

## IV. "Edit" Mode Functionality

*   **Purpose:** Real-time manipulation of the loaded JMeter test plan (JMX structure).
*   **Core Requirements (Java Implementation using JMeter APIs):**
    *   **JMX Parsing/Access:**
        *   Get current Test Plan: `GuiPackage.getInstance().getTreeModel()` to get the `JMeterTreeModel`.
        *   Get selected nodes: `GuiPackage.getInstance().getCurrentNodes()` to get `JMeterTreeNode[]`.
        *   Traverse `HashTree`: Iterate through the test plan structure obtained from the model.
    *   **JMX Modification:**
        *   Modify `TestElement` properties directly (e.g., `testElement.setProperty("name", "value")`).
        *   Add/remove elements from the `HashTree` using methods from `JMeterTreeModel` and `HashTree`.
        *   After modification, update the GUI: `GuiPackage.getInstance().getMainFrame().getMainPanel().getTree().repaint()` or more specific model update notifications. `JMeterTreeModel.nodeStructureChanged(parentNode)` is key.
    *   **JMX Generation:**
        *   Create new `TestElement` instances (e.g., `new RegexExtractor()`).
        *   Set their properties.
        *   Convert to JMX string for display/GenAI (if needed) using `SaveService.getTreeModel().getPrimarySaveService().getAsString(testElementHashTree)`.
        *   Parse JMX string from GenAI into `TestElement` using `SaveService.loadElement(InputStream)`.
    *   **User Confirmation:**
        *   For significant changes, display a summary in the response viewer.
        *   Show "Apply Changes" and "Cancel" buttons. Only proceed if "Apply" is clicked.
    *   **Context Awareness:** Operations should target specific elements (user-selected, named by user) or apply more broadly as indicated by the command.

*   **Specific "Edit" Capabilities (Implement as distinct actions/methods):**

    1.  **Correlation (Ref: Design Doc Section 6.A):**
        *   Command examples: "Correlate `sessionToken` from `LoginSampler` response."
        *   Action:
            *   Identify target Sampler.
            *   GenAI suggests extractor type (Regex, JSON) and expression.
            *   Plugin creates the extractor `TestElement` (e.g., `RegexExtractor`, `JSONPostProcessor`), configures its properties (`refname`, `regex`/`jsonPathExprs`, `template`, `match_number`, `default`).
            *   Add extractor as a child to the target sampler's `JMeterTreeNode` and its `HashTree`.
            *   Identify subsequent samplers and replace hardcoded values with the new variable (requires careful JMX traversal and modification of `Argument` elements or body strings). Confirmation is vital.

    2.  **CSV Data Set Integration (Ref: Design Doc Section 6.B):**
        *   Command examples: "Add CSV file `data/users.csv` with vars `user,pass` for `LoginSampler`."
        *   Action:
            *   Prompt for file path if not fully specified.
            *   Create `CSVDataSet` TestElement. Configure `filename`, `variableNames`, `delimiter`, `shareMode`, etc.
            *   Determine placement (e.g., child of relevant Thread Group or Test Plan). Add to `HashTree` and GUI.
            *   Update specified sampler fields (e.g., `HTTPArgument` values) to use `${varN}`.

    3.  **Timer/Assertion Addition (Ref: Design Doc Section 7.B for Timers, general for Assertions):**
        *   Command examples: "Add Response Assertion to check for '200' in `SamplerX`." / "Insert URT 1-3s to all HTTP samplers."
        *   Action:
            *   Identify target sampler(s).
            *   Create `ResponseAssertion` or `UniformRandomTimer` TestElement.
            *   Configure its properties (e.g., `Assertion.test_strings`, `Assertion.test_field`, `Assertion.test_type` for assertions; `RandomTimer.range`, `ConstantTimer.delay` for timers).
            *   Add as child to target sampler(s). Idempotency check for timers.

    4.  **Thread Group Tuning (Ref: Design Doc Section 7.A):**
        *   Command examples: "Tune `TG1` for 100 users, 60s ramp, 10m duration."
        *   Action:
            *   Identify target `ThreadGroup` TestElement.
            *   Modify properties: `ThreadGroup.num_threads`, `ThreadGroup.ramp_time`.
            *   If duration is specified, set `ThreadGroup.scheduler=true`, `ThreadGroup.duration`, and configure its child `LoopController` (`loops=-1`, `continue_forever=false`).

    5.  **Test Data Generation/Suggestion (Basic - primarily GenAI):**
        *   Command: "Suggest test data for login: username, password."
        *   Action: Send to GenAI. Display suggestions. Offer "Copy" or "Use in new CSV" (advanced).

    6.  **Element Renaming:**
        *   Command: "Rename sampler `HTTP Request old` to `HTTP Login`."
        *   Action: Get selected/named `TestElement`. Set `testname` property: `element.setName("New Name")` or `element.setProperty(TestElement.NAME, "New Name")`. Notify GUI.

    7.  **Replace Hardcoded Values in Request Bodies (Ref: Design Doc Section 7.C):**
        *   Command: "In `CreateUserSampler` JSON body, replace `“email”: “test@example.com”` with `“email”: “${user_email}”`."
        *   Action:
            *   Identify target sampler and its request body (e.g., first `HTTPArgument` if `POSTAsBody` is true).
            *   Perform string replacement on the `Argument.value`. Be careful with JSON syntax (quotes for strings, no quotes for numeric/boolean variables).
            *   Highly recommend showing a diff/preview before applying.

## V. Outputs from Plugin Operations

*   **JMX Modifications:** Changes are directly applied to JMeter's internal `HashTree` model and reflected in the GUI.
*   **User Feedback (in Response Viewer):**
    *   "Ask" Mode: Markdown text, JMX/code snippets.
    *   "Edit" Mode: Confirmation messages (e.g., "Added CSVDataSet 'users.csv'"), summaries of proposed changes, error messages if actions fail.

This summary should provide a solid foundation for a developer to begin architecting and implementing the JMeter Copilot plugin using Java, leveraging JMeter's APIs for UI and JMX manipulation, and integrating with a GenAI service for its intelligent features. Regular reference to the detailed design document sections (referenced above) will be necessary during development.
