package ru.vikulinva.notificationservice.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.vikulinva.notificationservice.base.PlatformBaseIntegrationTest;
import ru.vikulinva.notificationservice.repository.NotificationRepository;
import ru.vikulinva.notificationservice.repository.NotificationRepository.Filter;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

class OrderEventConsumerTest extends PlatformBaseIntegrationTest {

    @Autowired
    private OrderEventConsumer consumer;

    @Autowired
    private NotificationRepository repository;

    @Test
    @DisplayName("OrderConfirmed-record → создаются notifications для customer'а")
    void orderConfirmed_processed() {
        var customerId = UUID.randomUUID();
        stubContact(customerId);
        deterministicUuids();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        var record = makeRecord("OrderConfirmed",
            "{\"customerId\":\"%s\",\"orderId\":\"abc\"}".formatted(customerId));
        consumer.onMessage(record);

        assertThat(repository.search(Filter.empty(), 0, 50)).hasSize(1); // EMAIL only, нет push-токена в стабе
    }

    @Test
    @DisplayName("DisputeOpened адресуется sellerId, а не customerId")
    void disputeOpened_targetsSeller() {
        var sellerId = UUID.randomUUID();
        stubContactWithPush(sellerId);
        deterministicUuids();
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        var record = makeRecord("DisputeOpened",
            "{\"customerId\":\"%s\",\"sellerId\":\"%s\",\"orderId\":\"x\"}"
                .formatted(UUID.randomUUID(), sellerId));
        consumer.onMessage(record);

        var rows = repository.search(Filter.empty(), 0, 50);
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0).getUserId()).isEqualTo(sellerId);
    }

    @Test
    @DisplayName("record без обязательных headers — игнорируется")
    void missing_headers_ignored() {
        var record = new ConsumerRecord<String, String>("any", 0, 0, "k", "{}");
        // нет event-id, нет event-type
        consumer.onMessage(record);

        assertThat(repository.search(Filter.empty(), 0, 50)).isEmpty();
    }

    private ConsumerRecord<String, String> makeRecord(String eventType, String payload) {
        var record = new ConsumerRecord<>("any", 0, 0L, "key", payload);
        record.headers().add("event-id", UUID.randomUUID().toString().getBytes());
        record.headers().add("event-type", eventType.getBytes());
        return record;
    }

    private void stubContact(UUID userId) {
        customerBff.stubFor(get(urlPathEqualTo("/api/v1/users/" + userId + "/contact"))
            .willReturn(okJson("""
                {"userId":"%s","email":"u@example.com","pushTokens":[],"locale":"ru"}
                """.formatted(userId))));
    }

    private void stubContactWithPush(UUID userId) {
        customerBff.stubFor(get(urlPathEqualTo("/api/v1/users/" + userId + "/contact"))
            .willReturn(okJson("""
                {"userId":"%s","email":"u@example.com","pushTokens":["t-1"],"locale":"ru"}
                """.formatted(userId))));
    }

    private void deterministicUuids() {
        AtomicInteger counter = new AtomicInteger();
        given(uuidGenerator.generate()).willAnswer(inv ->
            UUID.nameUUIDFromBytes(("c-" + counter.getAndIncrement()).getBytes()));
    }
}
