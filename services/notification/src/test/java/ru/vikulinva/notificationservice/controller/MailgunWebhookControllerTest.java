package ru.vikulinva.notificationservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.vikulinva.notificationservice.base.PlatformBaseIntegrationTest;
import ru.vikulinva.notificationservice.generated.enums.NotificationStatus;
import ru.vikulinva.notificationservice.repository.NotificationRepository;
import ru.vikulinva.notificationservice.service.NotificationService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MailgunWebhookControllerTest extends PlatformBaseIntegrationTest {

    private static final String SIGNING_KEY = "it-signing-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService service;

    @Autowired
    private NotificationRepository repository;

    @Test
    @DisplayName("delivered webhook с валидной подписью переводит SENT → DELIVERED")
    void delivered_webhook_marksDelivered() throws Exception {
        var customerId = UUID.randomUUID();
        var externalId = "msg-200";
        seedSentNotification(customerId, externalId);

        Instant now = Instant.parse("2026-04-01T10:05:00Z");
        given(dateTimeService.now()).willReturn(now);

        long ts = now.getEpochSecond();
        String token = "tok123";
        String signature = sign(ts + token, SIGNING_KEY);

        String body = """
            {"signature":{"timestamp":"%d","token":"%s","signature":"%s"},
             "event-data":{"event":"delivered","id":"%s"}}
            """.formatted(ts, token, signature, externalId);

        mockMvc.perform(post("/webhooks/email-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        var n = repository.findByExternalId(externalId).orElseThrow();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    @DisplayName("невалидная подпись → 401 WEBHOOK_SIGNATURE_INVALID")
    void invalid_signature_returns401() throws Exception {
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:05:00Z"));
        long ts = Instant.parse("2026-04-01T10:05:00Z").getEpochSecond();

        String body = """
            {"signature":{"timestamp":"%d","token":"x","signature":"deadbeef"},
             "event-data":{"event":"delivered","id":"any"}}
            """.formatted(ts);

        mockMvc.perform(post("/webhooks/email-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    @DisplayName("устаревший timestamp (replay) → 401")
    void stale_timestamp_returns401() throws Exception {
        Instant now = Instant.parse("2026-04-01T10:30:00Z");
        Instant signedAt = Instant.parse("2026-04-01T10:00:00Z");
        given(dateTimeService.now()).willReturn(now);

        long ts = signedAt.getEpochSecond();
        String token = "old-token";
        String signature = sign(ts + token, SIGNING_KEY);

        String body = """
            {"signature":{"timestamp":"%d","token":"%s","signature":"%s"},
             "event-data":{"event":"delivered","id":"any"}}
            """.formatted(ts, token, signature);

        mockMvc.perform(post("/webhooks/email-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    private void seedSentNotification(UUID customerId, String externalId) {
        customerBff.stubFor(WireMock.get(urlPathEqualTo("/api/v1/users/" + customerId + "/contact"))
            .willReturn(okJson("""
                {"userId":"%s","email":"buyer@example.com","pushTokens":[],"locale":"ru"}
                """.formatted(customerId))));
        mailgun.stubFor(WireMock.post(urlMatching("/v3/.*/messages"))
            .willReturn(okJson("{\"id\":\"%s\",\"message\":\"Queued.\"}".formatted(externalId))));
        AtomicInteger counter = new AtomicInteger();
        given(uuidGenerator.generate()).willAnswer(inv ->
            UUID.nameUUIDFromBytes(("w-" + counter.getAndIncrement()).getBytes()));
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        service.processIncomingEvent(UUID.randomUUID(), "OrderConfirmed", customerId,
            "{\"customerId\":\"%s\"}".formatted(customerId));
        service.dispatchPending(10);
    }

    private String sign(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
