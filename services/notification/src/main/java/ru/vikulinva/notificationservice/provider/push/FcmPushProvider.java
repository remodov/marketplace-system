package ru.vikulinva.notificationservice.provider.push;

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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Firebase Cloud Messaging — упрощённая реализация через REST. Не
 * используем Firebase Admin SDK, чтобы не тащить netty/google-auth-jar.
 */
@Component
public class FcmPushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(FcmPushProvider.class);

    private final RestTemplate restTemplate;
    private final String project;

    public FcmPushProvider(@Qualifier("fcmRestTemplate") RestTemplate restTemplate,
                             @Value("${notificationservice.fcm.project:demo-project}") String project) {
        this.restTemplate = restTemplate;
        this.project = project;
    }

    @Override
    public Result send(String token, String title, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of(
            "message", Map.of(
                "token", token,
                "notification", Map.of("title", title, "body", body)));

        try {
            restTemplate.exchange(
                "/v1/projects/{project}/messages:send",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Void.class,
                project);
            log.debug("FCM accepted push for token {}", maskToken(token));
            return Result.ok();
        } catch (HttpClientErrorException ex) {
            return Result.permanentError("FCM 4xx: " + ex.getStatusCode());
        } catch (HttpServerErrorException ex) {
            return Result.transientError("FCM 5xx: " + ex.getStatusCode());
        } catch (ResourceAccessException ex) {
            return Result.transientError("FCM network error: " + ex.getMessage());
        }
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 8) return "****";
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    @Configuration
    static class Cfg {

        @Bean("fcmRestTemplate")
        RestTemplate fcmRestTemplate(RestTemplateBuilder builder,
                                       @Value("${notificationservice.fcm.base-url:https://fcm.googleapis.com}") String baseUrl) {
            return builder
                .rootUri(baseUrl)
                .connectTimeout(Duration.ofMillis(1000))
                .readTimeout(Duration.ofMillis(3000))
                .build();
        }
    }
}
