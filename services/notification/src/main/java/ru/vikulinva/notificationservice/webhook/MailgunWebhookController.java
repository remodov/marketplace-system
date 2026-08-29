package ru.vikulinva.notificationservice.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vikulinva.notificationservice.error.WebhookSignatureInvalidException;
import ru.vikulinva.notificationservice.service.NotificationService;
import ru.vikulinva.notificationservice.time.DateTimeService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Webhook от Mailgun на события delivery / bounce. Валидация HMAC по
 * рекомендованному Mailgun алгоритму:
 * {@code HMAC_SHA256(api_key, timestamp + token)}.
 *
 * <p>Защита от replay: timestamp старше 5 минут — отклоняется.
 */
@RestController
@RequestMapping("/webhooks")
public class MailgunWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MailgunWebhookController.class);
    private static final Duration MAX_TIMESTAMP_AGE = Duration.ofMinutes(5);

    private final NotificationService service;
    private final ObjectMapper objectMapper;
    private final DateTimeService time;
    private final String webhookSigningKey;

    public MailgunWebhookController(NotificationService service,
                                       ObjectMapper objectMapper,
                                       DateTimeService time,
                                       @Value("${notificationservice.mailgun.webhook-signing-key:}") String webhookSigningKey) {
        this.service = Objects.requireNonNull(service);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.time = Objects.requireNonNull(time);
        this.webhookSigningKey = webhookSigningKey == null ? "" : webhookSigningKey;
    }

    @PostMapping("/email-events")
    public ResponseEntity<Void> handle(@RequestBody String rawBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new WebhookSignatureInvalidException("Cannot parse webhook body");
        }
        JsonNode signature = root.path("signature");
        String timestamp = signature.path("timestamp").asText("");
        String token = signature.path("token").asText("");
        String signatureHex = signature.path("signature").asText("");
        validate(timestamp, token, signatureHex);

        JsonNode eventData = root.path("event-data");
        String event = eventData.path("event").asText("");
        String externalId = extractExternalId(eventData);
        if (externalId.isEmpty()) {
            log.warn("Mailgun webhook without message-id, ignoring");
            return ResponseEntity.ok().build();
        }

        switch (event) {
            case "delivered" -> service.markDelivered(externalId, time.now().atOffset(java.time.ZoneOffset.UTC));
            case "permanent_failure", "bounced" -> {
                String reason = eventData.path("delivery-status").path("description").asText("bounced");
                service.markBounced(externalId, reason);
            }
            default -> log.debug("Mailgun event '{}' for {} ignored (only delivered/bounced handled)", event, externalId);
        }
        return ResponseEntity.ok().build();
    }

    private void validate(String timestamp, String token, String signatureHex) {
        if (webhookSigningKey.isEmpty()) {
            log.warn("Mailgun webhook signing key is not configured — skipping HMAC check (DEV ONLY)");
            return;
        }
        if (timestamp.isEmpty() || token.isEmpty() || signatureHex.isEmpty()) {
            throw new WebhookSignatureInvalidException("Missing signature fields");
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new WebhookSignatureInvalidException("Invalid timestamp");
        }
        Instant signedAt = Instant.ofEpochSecond(ts);
        if (Duration.between(signedAt, time.now()).abs().compareTo(MAX_TIMESTAMP_AGE) > 0) {
            throw new WebhookSignatureInvalidException("Timestamp out of window (replay protection)");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSigningKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((timestamp + token).getBytes(StandardCharsets.UTF_8));
            String expectedHex = HexFormat.of().formatHex(expected);
            if (!expectedHex.equals(signatureHex)) {
                throw new WebhookSignatureInvalidException("HMAC mismatch");
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC setup failed", e);
        }
    }

    private static String extractExternalId(JsonNode eventData) {
        // Mailgun делает message-id доступным в нескольких местах.
        JsonNode message = eventData.path("message");
        String header = message.path("headers").path("message-id").asText("");
        if (!header.isEmpty()) return header;
        return eventData.path("id").asText("");
    }
}
