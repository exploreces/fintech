package com.finex.fini.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AiModelIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(AiModelIntegrationService.class);

    @Value("${openrouter.api.key}")
    private String apiKey;

    @Value("${openrouter.api.url}")
    private String apiUrl;

    @Value("${openrouter.api.model}")
    private String model;

    @Value("${openrouter.api.timeout:130}")
    private int apiTimeoutSeconds;

    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Autowired
    public AiModelIntegrationService(ObjectMapper objectMapper, WebClient webClient) {
        this.objectMapper = objectMapper;
        this.webClient = webClient;
    }

    public String analyzeTransactions(String transactions) {
        log.info("Starting AI transaction analysis with model: {}", model);

        // Simplified prompt focusing only on vendor, amount, and category
        String prompt =
                "Financial assistant: Analyze GPay PDF text below.\n" +
                        "Extract only: vendor, amount, and categorize each transaction.\n" +
                        "Categories: food, shopping, bills, tofriends, miscellaneous.\n" +
                        "Return JSON format only. Do not include date or time information.\n\n" +
                        "Text: " + transactions;

        // Build message JSON
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(userMsg));

        try {
            // Use reactive WebClient for faster response
            return webClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header("HTTP-Referer", "http://localhost")
                    .header("X-Title", "FinEx Backend")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.equals(HttpStatus.TOO_MANY_REQUESTS),
                            response -> {
                                log.error("RATE LIMITED by OpenRouter!");
                                return Mono.error(new RuntimeException("Rate limited. Try again later."));
                            })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(apiTimeoutSeconds))
                    .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(2))
                            .filter(e -> !(e instanceof java.util.concurrent.TimeoutException)))
                    .map(responseBody -> {
                        try {
                            // Parse JSON response
                            JsonNode parsed = objectMapper.readTree(responseBody);
                            JsonNode choices = parsed.get("choices");

                            if (choices != null && !choices.isEmpty()) {
                                JsonNode messageNode = choices.get(0).get("message");
                                if (messageNode != null && messageNode.get("content") != null) {
                                    return messageNode.get("content").asText();
                                }
                            }
                            return responseBody;
                        } catch (Exception e) {
                            log.error("Failed to parse response", e);
                            return responseBody;
                        }
                    })
                    .block(Duration.ofSeconds(apiTimeoutSeconds + 5)); // Add buffer time

        } catch (Exception e) {
            log.error("OpenRouter API call failed", e);
            return "{ \"error\": \"" + e.getMessage() + "\" }";
        }
    }

    // Async method for non-blocking calls
    public CompletableFuture<String> analyzeTransactionsAsync(String transactions) {
        log.info("Starting async AI analysis with model: {}", model);

        String prompt = "Financial assistant: Analyze GPay PDF text below.\n" +
                "Extract only: vendor, amount, and categorize each transaction.\n" +
                "Categories: food, shopping, bills, tofriends, miscellaneous.\n" +
                "Return JSON format only. Do not include date or time information.\n\n" +
                "Text: " + transactions;

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(userMsg));

        return webClient.post()
                .uri(apiUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("HTTP-Referer", "http://localhost")
                .header("X-Title", "FinEx Backend")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(apiTimeoutSeconds))
                .map(responseBody -> {
                    try {
                        JsonNode parsed = objectMapper.readTree(responseBody);
                        JsonNode choices = parsed.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            JsonNode messageNode = choices.get(0).get("message");
                            if (messageNode != null && messageNode.get("content") != null) {
                                return messageNode.get("content").asText();
                            }
                        }
                        return responseBody;
                    } catch (Exception e) {
                        return responseBody;
                    }
                })
                .toFuture();
    }
}