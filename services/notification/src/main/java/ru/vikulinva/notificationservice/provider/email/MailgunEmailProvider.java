package ru.vikulinva.notificationservice.provider.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Mailgun-реализация {@link EmailProvider}. На запуске работает через
 * RestTemplate напрямую (Mailgun Java SDK тащит лишний netty); в боевых
 * условиях рекомендуется заменить на SDK.
 */
@Component
public class MailgunEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(MailgunEmailProvider.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String domain;
    private final String fromAddress;

    public MailgunEmailProvider(@Qualifier("mailgunRestTemplate") RestTemplate restTemplate,
                                  ObjectMapper objectMapper,
                                  @Value("${notificationservice.mailgun.domain:example.com}") String domain,
                                  @Value("${notificationservice.mailgun.from:no-reply@example.com}") String fromAddress) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.domain = domain;
        this.fromAddress = fromAddress;
    }

    @Override
    public Result send(String to, String subject, String htmlBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("from", fromAddress);
        body.add("to", to);
        body.add("subject", subject);
        body.add("html", htmlBody);
        body.add("o:tracking", "yes");

        try {
            String response = restTemplate.exchange(
                "/v3/{domain}/messages",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class,
                domain
            ).getBody();
            String externalId = parseExternalId(response);
            log.debug("Mailgun accepted email for {}: external_id={}", to, externalId);
            return Result.ok(externalId);
        } catch (HttpClientErrorException ex) {
            return Result.permanentError("Mailgun 4xx: " + ex.getStatusCode() + " " + snippet(ex.getResponseBodyAsString()));
        } catch (HttpServerErrorException ex) {
            return Result.transientError("Mailgun 5xx: " + ex.getStatusCode());
        } catch (ResourceAccessException ex) {
            return Result.transientError("Mailgun network error: " + ex.getMessage());
        }
    }

    private String parseExternalId(String responseBody) {
        if (responseBody == null) {
            return "unknown";
        }
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            String id = node.path("id").asText("");
            return id.isEmpty() ? "unknown" : id;
        } catch (Exception e) {
            return "unparseable";
        }
    }

    private static String snippet(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    @Configuration
    static class Cfg {

        @Bean("mailgunRestTemplate")
        RestTemplate mailgunRestTemplate(RestTemplateBuilder builder,
                                           @Value("${notificationservice.mailgun.base-url:https://api.mailgun.net}") String baseUrl,
                                           @Value("${notificationservice.mailgun.api-key:}") String apiKey) {
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(("api:" + apiKey).getBytes(StandardCharsets.UTF_8));
            return builder
                .rootUri(baseUrl)
                .connectTimeout(Duration.ofMillis(1000))
                .readTimeout(Duration.ofMillis(3000))
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth)
                .build();
        }
    }
}
