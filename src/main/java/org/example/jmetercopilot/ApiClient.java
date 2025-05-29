package org.example.jmetercopilot; // You can adjust the package name

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
// It's generally recommended to use a dedicated JSON library like org.json, Gson, or Jackson.
// For this example, we'll use org.json, assuming it's available in JMeter's classpath or added as a dependency.
import org.json.JSONObject;
import org.json.JSONArray;

public class ApiClient {

    private static final String API_URL = "https://api.lab45.ai/v1.1/skills/completion/query"; // As provided
    private String apiKey; // To be set by the plugin

    // Add fields for configurable parameters with defaults
    private String modelName = "gpt-4o"; // Default model
    private double temperature = 0.3;    // Default temperature
    private int maxOutputTokens = 4096; // Default max tokens

    public ApiClient() {
        // API key and other settings will be loaded/set via setters
    }

    // Setter methods for parameters
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public void setModelName(String modelName) {
        if (modelName != null && !modelName.isEmpty()) {
            this.modelName = modelName;
        }
    }

    public String getModelName() { return modelName; }

    public void setTemperature(double temperature) {
        // Add validation if needed (e.g., temperature between 0.0 and 2.0)
        this.temperature = temperature;
    }

    public double getTemperature() { return temperature; }

    public void setMaxOutputTokens(int maxOutputTokens) {
        // Add validation if needed (e.g., positive value)
        if (maxOutputTokens > 0) {
            this.maxOutputTokens = maxOutputTokens;
        }
    }

    public int getMaxOutputTokens() { return maxOutputTokens; }

    public String sendQuery(String userMessage, String systemMessage) {
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            return "Error: API Key not set.";
        }

        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + this.apiKey); // Ensure apiKey is used
            conn.setDoOutput(true);

            // Construct JSON payload using org.json
            JSONObject rootPayload = new JSONObject();
            JSONArray messagesArray = new JSONArray();

            JSONObject systemMessageJson = new JSONObject();
            systemMessageJson.put("role", "system");
            systemMessageJson.put("content", systemMessage);
            messagesArray.put(systemMessageJson);

            JSONObject userMessageJson = new JSONObject();
            userMessageJson.put("role", "user");
            userMessageJson.put("content", userMessage);
            messagesArray.put(userMessageJson);

            rootPayload.put("messages", messagesArray);
            rootPayload.put("search_provider", "Bing"); // As per your example
            rootPayload.put("stream_response", false);
            
            JSONObject skillParameters = new JSONObject();
            skillParameters.put("max_output_tokens", this.maxOutputTokens); // Use member variable
            skillParameters.put("temperature", this.temperature);       // Use member variable
            skillParameters.put("return_sources", true);
            skillParameters.put("model_name", this.modelName);           // Use member variable
            rootPayload.put("skill_parameters", skillParameters); // This now uses the dynamic values

            String jsonPayloadString = rootPayload.toString();

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayloadString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            InputStream inputStream;

            if (responseCode >= 200 && responseCode < 300) {
                inputStream = conn.getInputStream();
            } else {
                inputStream = conn.getErrorStream();
                // Log or handle error stream content if necessary
                // For now, just return a generic error for non-2xx responses before parsing
                StringBuilder errorResponse = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        errorResponse.append(responseLine.trim());
                    }
                }
                return "Error: HTTP " + responseCode + " - " + errorResponse.toString();
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            // Parse JSON response to get the content
            JSONObject jsonResponse = new JSONObject(response.toString());

            if (jsonResponse.has("data") && jsonResponse.getJSONObject("data").has("content")) {
                return jsonResponse.getJSONObject("data").getString("content");
            } else if (jsonResponse.has("error")) { // Example of how you might check for an error in the JSON response itself
                 return "Error from API: " + jsonResponse.getJSONObject("error").optString("message", response.toString());
            }
            else {
                return "Error: Unexpected response structure. Full response: " + response.toString();
            }

        } catch (Exception e) {
            // e.printStackTrace(); // Good for debugging, but might want a logger in a real plugin
            return "Error connecting to AI: " + e.getMessage();
        }
    }

    // Main method for basic local testing (optional)
    public static void main(String[] args) {
        ApiClient client = new ApiClient();
        // IMPORTANT: Replace "YOUR_API_KEY" with a valid key for testing locally.
        // Do NOT commit actual keys to version control.
        // client.setApiKey("YOUR_API_KEY"); // Set API Key for testing
        // client.setModelName("gpt-3.5-turbo"); // Example: set other params
        // client.setTemperature(0.7);

        if (client.getApiKey() == null || "YOUR_API_KEY".equals(client.getApiKey()) || client.getApiKey().isEmpty()) {
            System.out.println("Please set your API key in ApiClient.main to test.");
            System.out.println("Usage: java -DapiKey=YOUR_ACTUAL_KEY org.example.jmetercopilot.ApiClient");
            String apiKeyFromProperty = System.getProperty("apiKey");
            if(apiKeyFromProperty != null && !apiKeyFromProperty.isEmpty()){
                client.setApiKey(apiKeyFromProperty);
                System.out.println("Using API Key from system property: " + apiKeyFromProperty.substring(0,5) + "...");
            } else {
                return;
            }
        }
        
        System.out.println("Using Model: " + client.getModelName() + ", Temp: " + client.getTemperature() + ", MaxTokens: " + client.getMaxOutputTokens());

        String systemPrompt = "You are a performance tool JMeter expert.";
        String userQuery = "What is correlation in JMeter?";
        
        System.out.println("Sending query: " + userQuery);
        String response = client.sendQuery(userQuery, systemPrompt);
        System.out.println("\nResponse from AI:\n" + response);

        String userQuery2 = "How do I set up a CSV Data Set Config?";
        System.out.println("\nSending query: " + userQuery2);
        String response2 = client.sendQuery(userQuery2, systemPrompt);
        System.out.println("\nResponse from AI:\n" + response2);
    }
}
