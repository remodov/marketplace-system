package ru.vikulinva.notificationservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.vikulinva.notificationservice.base.PlatformBaseIntegrationTest;
import ru.vikulinva.notificationservice.error.InvalidStatusForRetryException;
import ru.vikulinva.notificationservice.generated.enums.NotificationChannel;
import ru.vikulinva.notificationservice.generated.enums.NotificationStatus;
import ru.vikulinva.notificationservice.generated.tables.pojos.NotificationsPojo;
import ru.vikulinva.notificationservice.repository.NotificationRepository;
import ru.vikulinva.notificationservice.repository.NotificationRepository.Filter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

class NotificationServiceIntegrationTest extends PlatformBaseIntegrationTest {

    @Autowired
    private NotificationService service;

    @Autowired
    private NotificationRepository repository;

    @Test
    @DisplayName("OrderConfirmed → создаются email + push, шаблоны рендерятся")
    void orderConfirmed_createsBothChannels() {
        var customerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        stubContact(customerId, "buyer@example.com", List.of("token-1"), "ru");
        stubMailgunOk("msg-id-1");
        stubFcmOk();
        deterministicUuids();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        String payload = """
            {"customerId":"%s","orderId":"abc-123","total":"1500.00"}
            """.formatted(customerId);

        service.processIncomingEvent(eventId, "OrderConfirmed", customerId, payload);

        var queued = repository.search(Filter.empty(), 0, 50);
        assertThat(queued).hasSize(2);
        assertThat(queued).extracting(NotificationsPojo::getChannel)
            .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.PUSH);
        assertThat(queued).allMatch(n -> n.getStatus() == NotificationStatus.QUEUED);

        // Диспатч переводит в SENT
        int sent = service.dispatchPending(10);
        assertThat(sent).isEqualTo(2);

        var dispatched = repository.search(Filter.empty(), 0, 50);
        assertThat(dispatched).allMatch(n -> n.getStatus() == NotificationStatus.SENT);
    }

    @Test
    @DisplayName("Идемпотентность: повторное событие не создаёт дубль")
    void duplicateEvent_skipped() {
        var customerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        stubContact(customerId, "buyer@example.com", List.of("t1"), "ru");
        deterministicUuids();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        String payload = """
            {"customerId":"%s","orderId":"abc","total":"100"}
            """.formatted(customerId);

        service.processIncomingEvent(eventId, "OrderPaid", customerId, payload);
        service.processIncomingEvent(eventId, "OrderPaid", customerId, payload);

        var rows = repository.search(Filter.empty(), 0, 50);
        assertThat(rows).hasSize(2); // EMAIL + PUSH, дубля нет
    }

    @Test
    @DisplayName("Mailgun 503 → TRANSIENT_ERROR; после 3 попыток FAILED")
    void mailgunTransient_failsAfterMaxAttempts() {
        var customerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        stubContact(customerId, "buyer@example.com", List.of(), "ru");
        mailgun.stubFor(post(urlMatching("/v3/.*/messages"))
            .willReturn(aResponse().withStatus(503)));
        deterministicUuids();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        service.processIncomingEvent(eventId, "OrderConfirmed", customerId,
            "{\"customerId\":\"%s\",\"orderId\":\"x\"}".formatted(customerId));

        // Три попытки последовательно — каждая закидывает запись в QUEUED
        service.dispatchPending(10);
        service.dispatchPending(10);
        service.dispatchPending(10);

        var failed = repository.search(
            new Filter(null, NotificationStatus.FAILED, null, null, null, null), 0, 10);
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0).getLastError()).contains("Max retries");
    }

    @Test
    @DisplayName("Mailgun 4xx → сразу FAILED без ретраев")
    void mailgunPermanent_failsImmediately() {
        var customerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        stubContact(customerId, "bad-address", List.of(), "ru");
        mailgun.stubFor(post(urlMatching("/v3/.*/messages"))
            .willReturn(aResponse().withStatus(400).withBody("invalid recipient")));
        deterministicUuids();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        service.processIncomingEvent(eventId, "OrderConfirmed", customerId,
            "{\"customerId\":\"%s\",\"orderId\":\"x\"}".formatted(customerId));

        service.dispatchPending(10);

        var emails = repository.search(
            new Filter(null, NotificationStatus.FAILED, NotificationChannel.EMAIL, null, null, null), 0, 10);
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0).getLastError()).contains("400");
    }

    @Test
    @DisplayName("retry на FAILED → переводит в QUEUED")
    void retry_movesToQueued() {
        var customerId = UUID.randomUUID();
        stubContact(customerId, "bad-address", List.of(), "ru");
        mailgun.stubFor(post(urlMatching("/v3/.*/messages"))
            .willReturn(aResponse().withStatus(400)));
        deterministicUuids();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        service.processIncomingEvent(UUID.randomUUID(), "OrderConfirmed", customerId,
            "{\"customerId\":\"%s\"}".formatted(customerId));
        service.dispatchPending(10);

        var failed = repository.search(
            new Filter(null, NotificationStatus.FAILED, null, null, null, null), 0, 10);
        assertThat(failed).hasSize(1);

        service.retry(failed.get(0).getId());

        var requeued = repository.findById(failed.get(0).getId()).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(requeued.getLastError()).isNull();
    }

    @Test
    @DisplayName("retry на не-FAILED → InvalidStatusForRetryException")
    void retry_nonFailed_throws() {
        var customerId = UUID.randomUUID();
        stubContact(customerId, "buyer@example.com", List.of(), "ru");
        stubMailgunOk("ext-1");
        deterministicUuids();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        service.processIncomingEvent(UUID.randomUUID(), "OrderConfirmed", customerId,
            "{\"customerId\":\"%s\"}".formatted(customerId));
        service.dispatchPending(10);

        var sent = repository.search(
            new Filter(null, NotificationStatus.SENT, null, null, null, null), 0, 10);
        assertThat(sent).hasSize(1);

        assertThatThrownBy(() -> service.retry(sent.get(0).getId()))
            .isInstanceOf(InvalidStatusForRetryException.class);
    }

    @Test
    @DisplayName("markDelivered/markBounced меняют статус по external_id")
    void webhookCallbacks_updateStatus() {
        var customerId = UUID.randomUUID();
        stubContact(customerId, "buyer@example.com", List.of(), "ru");
        stubMailgunOk("msg-99");
        deterministicUuids();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        service.processIncomingEvent(UUID.randomUUID(), "OrderConfirmed", customerId,
            "{\"customerId\":\"%s\"}".formatted(customerId));
        service.dispatchPending(10);

        var deliveredAt = Instant.parse("2026-04-01T10:01:00Z").atOffset(java.time.ZoneOffset.UTC);
        service.markDelivered("msg-99", deliveredAt);
        var delivered = repository.findByExternalId("msg-99").orElseThrow();
        assertThat(delivered.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
        assertThat(delivered.getDeliveredAt()).isEqualTo(deliveredAt);

        // markBounced работает только с SENT — на DELIVERED не сработает
        service.markBounced("msg-99", "should not apply");
        var stillDelivered = repository.findByExternalId("msg-99").orElseThrow();
        assertThat(stillDelivered.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    @DisplayName("ReservationFailed-style событие неизвестного типа — игнорируется")
    void unknownEventType_ignored() {
        var customerId = UUID.randomUUID();
        stubContact(customerId, "buyer@example.com", List.of(), "ru");
        deterministicUuids();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        service.processIncomingEvent(UUID.randomUUID(), "SomethingElse", customerId,
            "{\"customerId\":\"%s\"}".formatted(customerId));

        assertThat(repository.search(Filter.empty(), 0, 10)).isEmpty();
    }

    @Test
    @DisplayName("purgeOlderThan удаляет уведомления старше threshold")
    void purgeOlderThan_removesOldRecords() {
        var customerId = UUID.randomUUID();
        stubContact(customerId, "buyer@example.com", List.of(), "ru");
        deterministicUuids();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-01-01T00:00:00Z"));

        service.processIncomingEvent(UUID.randomUUID(), "OrderConfirmed", customerId,
            "{\"customerId\":\"%s\"}".formatted(customerId));

        // delete everything older than 2026-04-01 — старая запись пропадёт
        int removed = service.purgeOlderThan(Instant.parse("2026-04-01T00:00:00Z"));
        assertThat(removed).isPositive();
        assertThat(repository.search(Filter.empty(), 0, 10)).isEmpty();
    }

    private void stubContact(UUID userId, String email, List<String> tokens, String locale) {
        String tokensJson = tokens.stream()
            .map(t -> "\"" + t + "\"")
            .reduce((a, b) -> a + "," + b)
            .map(s -> "[" + s + "]")
            .orElse("[]");
        customerBff.stubFor(get(urlPathEqualTo("/api/v1/users/" + userId + "/contact"))
            .willReturn(okJson("""
                {"userId":"%s","email":"%s","pushTokens":%s,"locale":"%s"}
                """.formatted(userId, email, tokensJson, locale))));
    }

    private void stubMailgunOk(String externalId) {
        mailgun.stubFor(post(urlMatching("/v3/.*/messages"))
            .willReturn(okJson("{\"id\":\"%s\",\"message\":\"Queued.\"}".formatted(externalId))));
    }

    private void stubFcmOk() {
        fcm.stubFor(post(urlMatching("/v1/projects/.*/messages:send"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));
    }

    private void deterministicUuids() {
        AtomicInteger counter = new AtomicInteger();
        given(uuidGenerator.generate()).willAnswer(inv ->
            UUID.nameUUIDFromBytes(("test-" + counter.getAndIncrement()).getBytes()));
    }
}
