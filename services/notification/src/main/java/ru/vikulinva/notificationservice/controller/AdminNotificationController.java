package ru.vikulinva.notificationservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vikulinva.notificationservice.controller.dto.NotificationDetailDto;
import ru.vikulinva.notificationservice.controller.dto.NotificationListDto;
import ru.vikulinva.notificationservice.controller.dto.NotificationSummaryDto;
import ru.vikulinva.notificationservice.controller.dto.RestMapper;
import ru.vikulinva.notificationservice.error.NotificationNotFoundException;
import ru.vikulinva.notificationservice.generated.enums.NotificationChannel;
import ru.vikulinva.notificationservice.generated.enums.NotificationStatus;
import ru.vikulinva.notificationservice.repository.DeliveryAttemptRepository;
import ru.vikulinva.notificationservice.repository.NotificationRepository;
import ru.vikulinva.notificationservice.repository.NotificationRepository.Filter;
import ru.vikulinva.notificationservice.service.NotificationService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Админский REST для оператора. role:support-operator (на профиле {@code local}
 * security disabled через {@code LocalSecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class AdminNotificationController {

    private final NotificationRepository repo;
    private final DeliveryAttemptRepository attempts;
    private final NotificationService service;
    private final RestMapper mapper;

    public AdminNotificationController(NotificationRepository repo,
                                          DeliveryAttemptRepository attempts,
                                          NotificationService service,
                                          RestMapper mapper) {
        this.repo = repo;
        this.attempts = attempts;
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("hasRole('support-operator') or hasRole('admin')")
    public ResponseEntity<NotificationListDto> list(
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) NotificationStatus status,
        @RequestParam(required = false) NotificationChannel channel,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) OffsetDateTime from,
        @RequestParam(required = false) OffsetDateTime to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        if (size < 1 || size > 200) {
            size = 50;
        }
        Filter f = new Filter(userId, status, channel, eventType, from, to);
        List<NotificationSummaryDto> items = repo.search(f, page, size).stream()
            .map(mapper::toSummary)
            .toList();
        long total = repo.count(f);
        return ResponseEntity.ok(new NotificationListDto(items, total, page, size, (long) (page + 1) * size < total));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('support-operator') or hasRole('admin')")
    public ResponseEntity<NotificationDetailDto> get(@PathVariable UUID id) {
        var n = repo.findById(id).orElseThrow(() -> new NotificationNotFoundException(id));
        var atts = attempts.findByNotificationId(id);
        return ResponseEntity.ok(mapper.toDetail(n, atts));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('support-operator') or hasRole('admin')")
    public ResponseEntity<NotificationSummaryDto> retry(@PathVariable UUID id) {
        var n = service.retry(id);
        return ResponseEntity.ok(mapper.toSummary(n));
    }

    @PostMapping("/{id}/abandon")
    @PreAuthorize("hasRole('support-operator') or hasRole('admin')")
    public ResponseEntity<Void> abandon(@PathVariable UUID id) {
        service.abandon(id);
        return ResponseEntity.noContent().build();
    }
}
