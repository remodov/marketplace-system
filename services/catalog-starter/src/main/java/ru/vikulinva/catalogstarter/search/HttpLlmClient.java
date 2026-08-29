package ru.vikulinva.catalogstarter.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Провайдер по протоколу chat completions. Включается только когда задан адрес:
 * без ключа и адреса сервис должен работать, просто без разбора фразы.
 */
@Component
@ConditionalOnProperty("search.llm.base-url")
public class HttpLlmClient implements LlmClient {

    private final RestClient client;
    private final String model;

    public HttpLlmClient(RestClient.Builder builder,
                         @Value("${search.llm.base-url}") String baseUrl,
                         @Value("${search.llm.api-key:}") String apiKey,
                         @Value("${search.llm.model:gpt-4o-mini}") String model) {
        this.client = builder
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
                setReadTimeout((int) Duration.ofSeconds(5).toMillis());
            }})
            .build();
        this.model = model;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String prompt) {
        Map<String, Object> response = client.post()
            .uri("/chat/completions")
            .body(Map.of(
                "model", model,
                "temperature", 0,
                "messages", java.util.List.of(Map.of("role", "user", "content", prompt))))
            .retrieve()
            .body(Map.class);

        var choices = (java.util.List<Map<String, Object>>) response.get("choices");
        var message = (Map<String, Object>) choices.get(0).get("message");
        return String.valueOf(message.get("content"));
    }
}
