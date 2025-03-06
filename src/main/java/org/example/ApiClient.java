package org.example;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class ApiClient {
    private static final String API_URL = "https://api.lab45.ai/v1.1/skills/completion/query";

    // Method to send a message to the AI and get a response
    public static String sendToAI(String userMessage, String accessToken) {
        try {
            // Create URL object
            URL url = new URL(API_URL);
            // Open connection
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            // Set request method to POST
            conn.setRequestMethod("POST");
            // Set request headers
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setDoOutput(true);

            // JSON payload to send to the AI
            String jsonPayload = "{" +
                    "\"messages\": [" +
                    "    {\"role\": \"system\", \"content\": \"You are a performance tool Jmeter expert\"}," +
                    "    {\"role\": \"user\", \"content\": \"" + userMessage + "\"}" +
                    "]," +
                    "\"search_provider\": \"Bing\"," +
                    "\"stream_response\": false," +
                    "\"skill_parameters\": {" +
                    "    \"max_output_tokens\": 4096," +
                    "    \"temperature\": 0.3," +
                    "    \"return_sources\": true," +
                    "    \"model_name\": \"gpt-4o\"" +
                    "}" +
                    "}";

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Get response code
            int responseCode = conn.getResponseCode();
            // Get input stream based on response code
            InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream()
                    : conn.getErrorStream();

            // Read response
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(response.toString());
            return jsonResponse.getJSONObject("data").getString("content");

        } catch (Exception e) {
            e.printStackTrace();
            return "Error connecting to AI";
        }
    }
}
