package ru.vikulinva.notificationservice.controller.dto;

import org.assertj.core.api.SoftAssertions;
import org.jooq.JSONB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.vikulinva.notificationservice.generated.enums.DeliveryAttemptResult;
import ru.vikulinva.notificationservice.generated.enums.NotificationChannel;
import ru.vikulinva.notificationservice.generated.enums.NotificationStatus;
import ru.vikulinva.notificationservice.generated.tables.pojos.DeliveryAttemptsPojo;
import ru.vikulinva.notificationservice.generated.tables.pojos.NotificationsPojo;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RestMapperTest {

    private static final UUID NOTIFICATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID FIRST_ATTEMPT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID SECOND_ATTEMPT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static final String EVENT_TYPE = "OrderConfirmed";
    private static final String CONTACT = "buyer@example.test";
    private static final String TEMPLATE_KEY = "order-confirmed-email";
    private static final String LOCALE = "ru-RU";
    private static final String EXTERNAL_ID = "mailgun-msg-42";
    private static final String LAST_ERROR = "smtp timeout on first try";
    private static final String SOURCE_EVENT_PAYLOAD = "{\"orderId\":\"A-1\"}";
    private static final String TEMPLATE_VARIABLES = "{\"total\":\"1990.00\"}";

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 8, 30, 10, 15, 30, 0, ZoneOffset.UTC);
    private static final OffsetDateTime SENT_AT = OffsetDateTime.of(2026, 8, 30, 11, 16, 31, 0, ZoneOffset.UTC);
    private static final OffsetDateTime DELIVERED_AT = OffsetDateTime.of(2026, 8, 30, 12, 17, 32, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FIRST_ATTEMPTED_AT = OffsetDateTime.of(2026, 8, 30, 10, 16, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime SECOND_ATTEMPTED_AT = OffsetDateTime.of(2026, 8, 30, 10, 17, 0, 0, ZoneOffset.UTC);

    private static final String FIRST_RESPONSE_SNIPPET = "503 Service Unavailable";
    private static final String SECOND_RESPONSE_SNIPPET = "250 Message accepted";

    private final RestMapper mapper = new RestMapper();

    @Test
    @DisplayName("toSummary переносит все девять полей, не перепутав три соседних момента времени")
    void toSummaryCarriesEveryField() {
        NotificationSummaryDto summary = mapper.toSummary(fullNotification());

        SoftAssertions.assertSoftly(each -> assertEverySummaryField(each, summary));
    }

    @Test
    @DisplayName("toDetail заполняет вложенный summary — все девять полей, а не null целым блоком")
    void toDetailFillsNestedSummary() {
        NotificationDetailDto detail = mapper.toDetail(fullNotification(), List.of(firstAttempt(), secondAttempt()));

        assertThat(detail.summary()).isNotNull();
        SoftAssertions.assertSoftly(each -> assertEverySummaryField(each, detail.summary()));
    }

    @Test
    @DisplayName("toDetail переносит собственные поля уведомления, включая оба jsonb")
    void toDetailCarriesOwnFields() {
        NotificationDetailDto detail = mapper.toDetail(fullNotification(), List.of(firstAttempt(), secondAttempt()));

        SoftAssertions.assertSoftly(each -> {
            each.assertThat(detail.templateKey()).as("templateKey").isEqualTo(TEMPLATE_KEY);
            each.assertThat(detail.locale()).as("locale").isEqualTo(LOCALE);
            each.assertThat(detail.externalId()).as("externalId").isEqualTo(EXTERNAL_ID);
            each.assertThat(detail.lastError()).as("lastError").isEqualTo(LAST_ERROR);
            each.assertThat(detail.sourceEventPayload()).as("sourceEventPayload").isEqualTo(SOURCE_EVENT_PAYLOAD);
            each.assertThat(detail.templateVariables()).as("templateVariables").isEqualTo(TEMPLATE_VARIABLES);
        });
    }

    @Test
    @DisplayName("toDetail переносит попытки доставки в исходном порядке, каждое поле поимённо")
    void toDetailCarriesEveryAttemptField() {
        NotificationDetailDto detail = mapper.toDetail(fullNotification(), List.of(firstAttempt(), secondAttempt()));

        assertThat(detail.attempts()).hasSize(2);

        SoftAssertions.assertSoftly(each -> {
            NotificationDetailDto.DeliveryAttemptDto first = detail.attempts().get(0);
            each.assertThat(first.id()).as("attempts[0].id").isEqualTo(FIRST_ATTEMPT_ID);
            each.assertThat(first.attemptNumber()).as("attempts[0].attemptNumber").isEqualTo(1);
            each.assertThat(first.result()).as("attempts[0].result").isEqualTo("TRANSIENT_ERROR");
            each.assertThat(first.responseSnippet()).as("attempts[0].responseSnippet").isEqualTo(FIRST_RESPONSE_SNIPPET);
            each.assertThat(first.attemptedAt()).as("attempts[0].attemptedAt").isEqualTo(FIRST_ATTEMPTED_AT);

            NotificationDetailDto.DeliveryAttemptDto second = detail.attempts().get(1);
            each.assertThat(second.id()).as("attempts[1].id").isEqualTo(SECOND_ATTEMPT_ID);
            each.assertThat(second.attemptNumber()).as("attempts[1].attemptNumber").isEqualTo(2);
            each.assertThat(second.result()).as("attempts[1].result").isEqualTo("OK");
            each.assertThat(second.responseSnippet()).as("attempts[1].responseSnippet").isEqualTo(SECOND_RESPONSE_SNIPPET);
            each.assertThat(second.attemptedAt()).as("attempts[1].attemptedAt").isEqualTo(SECOND_ATTEMPTED_AT);
        });
    }

    @Test
    @DisplayName("пустой список попыток даёт пустой список, а не null")
    void toDetailWithoutAttemptsGivesEmptyList() {
        NotificationDetailDto detail = mapper.toDetail(fullNotification(), List.of());

        assertThat(detail.attempts()).isEmpty();
    }

    @Test
    @DisplayName("отсутствующий jsonb превращается в null, а не в строку null")
    void missingJsonbBecomesNull() {
        NotificationsPojo notification = fullNotification();
        notification.setSourceEventPayload(null);
        notification.setTemplateVariables(null);

        NotificationDetailDto detail = mapper.toDetail(notification, List.of());

        assertThat(detail.sourceEventPayload()).isNull();
        assertThat(detail.templateVariables()).isNull();
    }

    @Test
    @DisplayName("попытка без result роняет маппинг NullPointerException — не подставляет null")
    void attemptWithoutResultThrows() {
        DeliveryAttemptsPojo attempt = firstAttempt();
        attempt.setResult(null);

        assertThatNullPointerException()
            .isThrownBy(() -> mapper.toDetail(fullNotification(), List.of(attempt)));
    }

    @Test
    @DisplayName("попытка без attemptNumber роняет маппинг NullPointerException — не подставляет ноль")
    void attemptWithoutAttemptNumberThrows() {
        DeliveryAttemptsPojo attempt = firstAttempt();
        attempt.setAttemptNumber(null);

        assertThatNullPointerException()
            .isThrownBy(() -> mapper.toDetail(fullNotification(), List.of(attempt)));
    }

    private static void assertEverySummaryField(SoftAssertions each, NotificationSummaryDto summary) {
        each.assertThat(summary.id()).as("id").isEqualTo(NOTIFICATION_ID);
        each.assertThat(summary.userId()).as("userId").isEqualTo(USER_ID);
        each.assertThat(summary.eventType()).as("eventType").isEqualTo(EVENT_TYPE);
        each.assertThat(summary.channel()).as("channel").isEqualTo(NotificationChannel.EMAIL);
        each.assertThat(summary.contact()).as("contact").isEqualTo(CONTACT);
        each.assertThat(summary.status()).as("status").isEqualTo(NotificationStatus.SENT);
        each.assertThat(summary.createdAt()).as("createdAt").isEqualTo(CREATED_AT);
        each.assertThat(summary.sentAt()).as("sentAt").isEqualTo(SENT_AT);
        each.assertThat(summary.deliveredAt()).as("deliveredAt").isEqualTo(DELIVERED_AT);
    }

    private static NotificationsPojo fullNotification() {
        NotificationsPojo notification = new NotificationsPojo();
        notification.setId(NOTIFICATION_ID);
        notification.setEventId(EVENT_ID);
        notification.setEventType(EVENT_TYPE);
        notification.setUserId(USER_ID);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setContact(CONTACT);
        notification.setTemplateKey(TEMPLATE_KEY);
        notification.setLocale(LOCALE);
        notification.setStatus(NotificationStatus.SENT);
        notification.setSourceEventPayload(JSONB.valueOf(SOURCE_EVENT_PAYLOAD));
        notification.setTemplateVariables(JSONB.valueOf(TEMPLATE_VARIABLES));
        notification.setExternalId(EXTERNAL_ID);
        notification.setCreatedAt(CREATED_AT);
        notification.setSentAt(SENT_AT);
        notification.setDeliveredAt(DELIVERED_AT);
        notification.setLastError(LAST_ERROR);
        return notification;
    }

    private static DeliveryAttemptsPojo firstAttempt() {
        DeliveryAttemptsPojo attempt = new DeliveryAttemptsPojo();
        attempt.setId(FIRST_ATTEMPT_ID);
        attempt.setNotificationId(NOTIFICATION_ID);
        attempt.setAttemptNumber(1);
        attempt.setResult(DeliveryAttemptResult.TRANSIENT_ERROR);
        attempt.setResponseSnippet(FIRST_RESPONSE_SNIPPET);
        attempt.setAttemptedAt(FIRST_ATTEMPTED_AT);
        return attempt;
    }

    private static DeliveryAttemptsPojo secondAttempt() {
        DeliveryAttemptsPojo attempt = new DeliveryAttemptsPojo();
        attempt.setId(SECOND_ATTEMPT_ID);
        attempt.setNotificationId(NOTIFICATION_ID);
        attempt.setAttemptNumber(2);
        attempt.setResult(DeliveryAttemptResult.OK);
        attempt.setResponseSnippet(SECOND_RESPONSE_SNIPPET);
        attempt.setAttemptedAt(SECOND_ATTEMPTED_AT);
        return attempt;
    }
}
