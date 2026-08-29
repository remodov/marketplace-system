package ru.vikulinva.notificationservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jooq.JSONB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.notificationservice.channel.ChannelRules;
import ru.vikulinva.notificationservice.domain.UserContact;
import ru.vikulinva.notificationservice.error.InvalidStatusForRetryException;
import ru.vikulinva.notificationservice.error.NotificationNotFoundException;
import ru.vikulinva.notificationservice.generated.enums.DeliveryAttemptResult;
import ru.vikulinva.notificationservice.generated.enums.NotificationChannel;
import ru.vikulinva.notificationservice.generated.enums.NotificationStatus;
import ru.vikulinva.notificationservice.generated.tables.pojos.DeliveryAttemptsPojo;
import ru.vikulinva.notificationservice.generated.tables.pojos.NotificationsPojo;
import ru.vikulinva.notificationservice.generated.tables.pojos.TemplatesPojo;
import ru.vikulinva.notificationservice.provider.contact.ContactClient;
import ru.vikulinva.notificationservice.provider.email.EmailProvider;
import ru.vikulinva.notificationservice.provider.push.PushProvider;
import ru.vikulinva.notificationservice.repository.DeliveryAttemptRepository;
import ru.vikulinva.notificationservice.repository.NotificationRepository;
import ru.vikulinva.notificationservice.repository.ProcessedEventsRepository;
import ru.vikulinva.notificationservice.repository.TemplateRepository;
import ru.vikulinva.notificationservice.template.TemplateRenderer;
import ru.vikulinva.notificationservice.time.DateTimeService;
import ru.vikulinva.notificationservice.time.UuidGenerator;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Центральный сервис: принимает входящее событие, создаёт строки в
 * {@code notifications} по таблице каналов, отправляет через провайдеров,
 * обновляет статусы. Работает напрямую с jOOQ-сгенерёнными POJO.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    public static final int MAX_ATTEMPTS = 3;

    private final NotificationRepository notifications;
    private final TemplateRepository templates;
    private final DeliveryAttemptRepository attempts;
    private final ProcessedEventsRepository processed;
    private final ChannelRules channelRules;
    private final TemplateRenderer renderer;
    private final ContactClient contactClient;
    private final EmailProvider emailProvider;
    private final PushProvider pushProvider;
    private final ObjectMapper objectMapper;
    private final DateTimeService time;
    private final UuidGenerator uuid;
    private final Counter templateMissingCounter;
    private final Counter failedCounter;

    public NotificationService(NotificationRepository notifications,
                                 TemplateRepository templates,
                                 DeliveryAttemptRepository attempts,
                                 ProcessedEventsRepository processed,
                                 ChannelRules channelRules,
                                 TemplateRenderer renderer,
                                 ContactClient contactClient,
                                 EmailProvider emailProvider,
                                 PushProvider pushProvider,
                                 ObjectMapper objectMapper,
                                 DateTimeService time,
                                 UuidGenerator uuid,
                                 MeterRegistry meterRegistry) {
        this.notifications = Objects.requireNonNull(notifications);
        this.templates = Objects.requireNonNull(templates);
        this.attempts = Objects.requireNonNull(attempts);
        this.processed = Objects.requireNonNull(processed);
        this.channelRules = Objects.requireNonNull(channelRules);
        this.renderer = Objects.requireNonNull(renderer);
        this.contactClient = Objects.requireNonNull(contactClient);
        this.emailProvider = Objects.requireNonNull(emailProvider);
        this.pushProvider = Objects.requireNonNull(pushProvider);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.time = Objects.requireNonNull(time);
        this.uuid = Objects.requireNonNull(uuid);
        this.templateMissingCounter = meterRegistry.counter("notification_template_missing_total");
        this.failedCounter = meterRegistry.counter("notification_failed_total");
    }

    @Transactional
    public void processIncomingEvent(UUID eventId, String eventType, UUID userId, String payload) {
        OffsetDateTime now = nowOffset();
        if (!processed.markProcessed(eventId, eventType, now)) {
            log.debug("{} #{} already processed, skipping", eventType, eventId);
            return;
        }

        List<NotificationChannel> channels = channelRules.channelsFor(eventType);
        if (channels.isEmpty()) {
            log.debug("No channels configured for {} — skipping", eventType);
            return;
        }

        Optional<UserContact> contact = contactClient.findContact(userId);
        if (contact.isEmpty()) {
            log.warn("No contact for user {} on event {}", userId, eventId);
            return;
        }
        UserContact uc = contact.get();
        Map<String, String> templateVars = extractTemplateVars(payload, uc);

        for (NotificationChannel channel : channels) {
            createNotification(eventId, eventType, channel, uc, payload, templateVars, now);
        }
    }

    private void createNotification(UUID eventId, String eventType, NotificationChannel channel,
                                       UserContact contact, String sourcePayload,
                                       Map<String, String> templateVars,
                                       OffsetDateTime createdAt) {
        String templateKey = channelRules.templateKey(eventType, channel);
        Optional<TemplatesPojo> tpl = templates.find(templateKey, contact.locale());
        if (tpl.isEmpty()) {
            templateMissingCounter.increment();
            log.warn("BR-N3: template {} not found for locale {}, skipping", templateKey, contact.locale());
            return;
        }

        if (channel == NotificationChannel.EMAIL) {
            if (contact.email() == null || contact.email().isBlank()) {
                log.debug("No email for user {} — skipping EMAIL channel", contact.userId());
                return;
            }
            notifications.insert(buildNotification(eventId, eventType, channel, contact.userId(), contact.email(),
                templateKey, contact.locale(), sourcePayload, templateVars, createdAt));
        } else if (channel == NotificationChannel.PUSH) {
            for (String token : contact.pushTokens()) {
                notifications.insert(buildNotification(eventId, eventType, channel, contact.userId(), token,
                    templateKey, contact.locale(), sourcePayload, templateVars, createdAt));
            }
        }
    }

    private NotificationsPojo buildNotification(UUID eventId, String eventType, NotificationChannel channel,
                                                   UUID userId, String contact, String templateKey, String locale,
                                                   String sourcePayload, Map<String, String> vars,
                                                   OffsetDateTime createdAt) {
        NotificationsPojo n = new NotificationsPojo();
        n.setId(uuid.generate());
        n.setEventId(eventId);
        n.setEventType(eventType);
        n.setUserId(userId);
        n.setChannel(channel);
        n.setContact(contact);
        n.setTemplateKey(templateKey);
        n.setLocale(locale);
        n.setStatus(NotificationStatus.QUEUED);
        n.setSourceEventPayload(JSONB.valueOf(sourcePayload));
        n.setTemplateVariables(JSONB.valueOf(serialize(vars)));
        n.setCreatedAt(createdAt);
        return n;
    }

    public int dispatchPending(int batchSize) {
        List<NotificationsPojo> queued = notifications.findPending(batchSize);
        int sent = 0;
        for (NotificationsPojo n : queued) {
            try {
                if (dispatchOne(n)) {
                    sent++;
                }
            } catch (Exception e) {
                log.warn("Unexpected error dispatching {}: {}", n.getId(), e.getMessage(), e);
            }
        }
        return sent;
    }

    @Transactional
    public boolean dispatchOne(NotificationsPojo n) {
        TemplatesPojo template = templates.find(n.getTemplateKey(), n.getLocale())
            .orElseThrow(() -> new IllegalStateException("Template gone for " + n.getTemplateKey()));
        Map<String, String> vars = deserialize(n.getTemplateVariables());
        TemplateRenderer.Rendered rendered = renderer.render(template, vars);

        int attemptNumber = attempts.countAttempts(n.getId()) + 1;
        OffsetDateTime now = nowOffset();
        DeliveryAttemptResult result;
        String responseSnippet = null;
        String externalId = null;
        boolean ok = false;
        boolean permanentError = false;

        switch (n.getChannel()) {
            case EMAIL -> {
                EmailProvider.Result r = emailProvider.send(n.getContact(), rendered.subject(), rendered.body());
                if (r instanceof EmailProvider.Result.Ok okR) {
                    result = DeliveryAttemptResult.OK;
                    externalId = okR.externalId();
                    ok = true;
                } else if (r instanceof EmailProvider.Result.PermanentError pe) {
                    result = DeliveryAttemptResult.PERMANENT_ERROR;
                    responseSnippet = pe.reason();
                    permanentError = true;
                } else {
                    EmailProvider.Result.TransientError te = (EmailProvider.Result.TransientError) r;
                    result = DeliveryAttemptResult.TRANSIENT_ERROR;
                    responseSnippet = te.reason();
                }
            }
            case PUSH -> {
                PushProvider.Result r = pushProvider.send(n.getContact(), rendered.subject(), rendered.body());
                if (r == PushProvider.Result.Ok.INSTANCE) {
                    result = DeliveryAttemptResult.OK;
                    ok = true;
                } else if (r instanceof PushProvider.Result.PermanentError pe) {
                    result = DeliveryAttemptResult.PERMANENT_ERROR;
                    responseSnippet = pe.reason();
                    permanentError = true;
                } else {
                    PushProvider.Result.TransientError te = (PushProvider.Result.TransientError) r;
                    result = DeliveryAttemptResult.TRANSIENT_ERROR;
                    responseSnippet = te.reason();
                }
            }
            default -> throw new IllegalStateException("Unsupported channel: " + n.getChannel());
        }

        DeliveryAttemptsPojo attempt = new DeliveryAttemptsPojo();
        attempt.setId(uuid.generate());
        attempt.setNotificationId(n.getId());
        attempt.setAttemptNumber(attemptNumber);
        attempt.setResult(result);
        attempt.setResponseSnippet(responseSnippet);
        attempt.setAttemptedAt(now);
        attempts.insert(attempt);

        if (ok) {
            notifications.updateStatus(n.getId(), NotificationStatus.SENT, now, externalId, null);
            return true;
        }
        if (permanentError) {
            notifications.updateStatus(n.getId(), NotificationStatus.FAILED, null, null, responseSnippet);
            failedCounter.increment();
            return false;
        }
        if (attemptNumber >= MAX_ATTEMPTS) {
            notifications.updateStatus(n.getId(), NotificationStatus.FAILED, null, null,
                "Max retries reached: " + responseSnippet);
            failedCounter.increment();
        } else {
            notifications.updateStatus(n.getId(), NotificationStatus.QUEUED, null, null, responseSnippet);
        }
        return false;
    }

    @Transactional
    public NotificationsPojo retry(UUID notificationId) {
        NotificationsPojo n = notifications.findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (n.getStatus() != NotificationStatus.FAILED) {
            throw new InvalidStatusForRetryException(n.getStatus());
        }
        notifications.updateStatus(n.getId(), NotificationStatus.QUEUED, null, null, null);
        n.setStatus(NotificationStatus.QUEUED);
        n.setLastError(null);
        return n;
    }

    @Transactional
    public void abandon(UUID notificationId) {
        NotificationsPojo n = notifications.findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (n.getStatus() != NotificationStatus.FAILED) {
            throw new InvalidStatusForRetryException(n.getStatus());
        }
        notifications.updateStatus(n.getId(), NotificationStatus.FAILED, null, null,
            "Abandoned by operator at " + nowOffset());
    }

    @Transactional
    public void markDelivered(String externalId, OffsetDateTime deliveredAt) {
        notifications.findByExternalId(externalId)
            .ifPresent(n -> notifications.markDelivered(n.getId(), deliveredAt));
    }

    @Transactional
    public void markBounced(String externalId, String reason) {
        notifications.findByExternalId(externalId)
            .ifPresent(n -> notifications.markBounced(n.getId(), reason));
    }

    private Map<String, String> extractTemplateVars(String payload, UserContact contact) {
        Map<String, String> vars = new HashMap<>();
        vars.put("customer_name", contact.email() == null ? "" : contact.email().split("@")[0]);
        try {
            JsonNode root = objectMapper.readTree(payload);
            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getValue().isValueNode()) {
                    vars.put(camelToSnake(e.getKey()), e.getValue().asText());
                }
            }
        } catch (JsonProcessingException ex) {
            log.warn("Cannot parse source event payload: {}", ex.getMessage());
        }
        return vars;
    }

    private String serialize(Map<String, String> vars) {
        try {
            return objectMapper.writeValueAsString(vars);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, String> deserialize(JSONB json) {
        if (json == null || json.data() == null || json.data().isBlank()) return Map.of();
        try {
            JsonNode node = objectMapper.readTree(json.data());
            Map<String, String> out = new HashMap<>();
            node.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
            return out;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (!sb.isEmpty()) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public int purgeOlderThan(Instant threshold) {
        return notifications.deleteOlderThan(toOffset(threshold));
    }

    private OffsetDateTime nowOffset() {
        return toOffset(time.now());
    }

    private static OffsetDateTime toOffset(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }
}
