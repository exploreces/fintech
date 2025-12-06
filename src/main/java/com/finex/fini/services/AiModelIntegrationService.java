package com.finex.fini.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiModelIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(AiModelIntegrationService.class);

    @Value("${openrouter.api.key}")
    private String apiKey;

    @Value("${openrouter.api.url}")
    private String apiUrl;

    @Value("${openrouter.api.model}")
    private String model;

    public String analyzeTransactions(String transactions) {
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper mapper = new ObjectMapper();

        log.info("Starting AI transaction analysis with model: {}", model);

        // Avoid JSON stringification — keep raw text
        String transactionsText = transactions;
        log.debug("Incoming Transactions Raw Text:\n{}", transactionsText);

        // Prompt
        String prompt =
                "You are a financial assistant.\n" +
                        "Analyze the following extracted text from a GPay PDF and detect all transactions.\n" +
                        "Extract date, time, details, and amount reliably.\n" +
                        "Then categorize them into: food, shopping, bills, tofriends, miscellaneous.\n\n" +
                        "Return ONLY plaintext or JSON (you decide). No restrictions.\n\n" +
                        "Extracted PDF Text:\n" + transactionsText;

        // Build message JSON
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(userMsg));

        String jsonBody;
        try {
            jsonBody = mapper.writeValueAsString(body);
            log.debug("OpenRouter Request Body: {}", jsonBody);
        } catch (Exception e) {
            log.error("Failed to serialize OpenRouter body", e);
            throw new RuntimeException("Body serialization failed", e);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("HTTP-Referer", "http://localhost");
            headers.set("X-Title", "FinEx Backend");

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            log.info("Sending request to OpenRouter API: {}", apiUrl);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(apiUrl, entity, String.class);

            log.info("OpenRouter response status: {}", response.getStatusCode());
            log.debug("Raw Response Body:\n{}", response.getBody());

            if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.error("RATE LIMITED by OpenRouter!");
                return "{ \"error\": \"Rate limited. Try again later.\" }";
            }

            if (response.getBody() == null) {
                log.warn("OpenRouter returned empty body");
                return "";
            }

            // Parse JSON response
            JsonNode parsed = mapper.readTree(response.getBody());
            JsonNode choices = parsed.get("choices");

            if (choices == null || choices.isEmpty()) {
                log.warn("No AI output found in choices");
                return response.getBody();
            }

            JsonNode messageNode = choices.get(0).get("message");
            if (messageNode == null || messageNode.get("content") == null) {
                log.warn("Message content missing from AI response");
                return response.getBody();
            }

            String content = messageNode.get("content").asText();
            log.info("AI transaction analysis successfully extracted");
            log.debug("AI Output:\n{}", content);

            return content;

        } catch (Exception e) {
            log.error("OpenRouter API call failed", e);
            throw new RuntimeException("OpenRouter call failed: " + e.getMessage(), e);
        }
    }
}
