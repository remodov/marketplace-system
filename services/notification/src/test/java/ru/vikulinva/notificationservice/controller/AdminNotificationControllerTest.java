package ru.vikulinva.notificationservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import ru.vikulinva.notificationservice.base.PlatformBaseIntegrationTest;
import ru.vikulinva.notificationservice.service.NotificationService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminNotificationControllerTest extends PlatformBaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService service;

    private RequestPostProcessor operator() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_support-operator"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications возвращает пустую страницу при пустой БД")
    void list_empty() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").with(operator()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("GET /{id} → 404 в формате ProblemDetails")
    void get_missing_returnsProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/{id}", UUID.randomUUID()).with(operator()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("retry на SENT → 409 INVALID_STATUS_FOR_RETRY")
    void retry_onSent_returnsConflict() throws Exception {
        var customerId = UUID.randomUUID();
        customerBff.stubFor(WireMock.get(urlPathEqualTo("/api/v1/users/" + customerId + "/contact"))
            .willReturn(okJson("""
                {"userId":"%s","email":"buyer@example.com","pushTokens":[],"locale":"ru"}
                """.formatted(customerId))));
        mailgun.stubFor(WireMock.post(urlMatching("/v3/.*/messages"))
            .willReturn(okJson("{\"id\":\"ext-1\",\"message\":\"Queued.\"}")));
        AtomicInteger counter = new AtomicInteger();
        given(uuidGenerator.generate()).willAnswer(inv ->
            UUID.nameUUIDFromBytes(("c-" + counter.getAndIncrement()).getBytes()));
        given(dateTimeService.now()).willReturn(Instant.parse("2026-04-01T10:00:00Z"));

        service.processIncomingEvent(UUID.randomUUID(), "OrderConfirmed", customerId,
            "{\"customerId\":\"%s\"}".formatted(customerId));
        service.dispatchPending(10);

        var sentRow = jdbcTemplate.queryForObject(
            "SELECT id FROM notifications WHERE status = 'SENT' LIMIT 1", UUID.class);

        mockMvc.perform(post("/api/v1/notifications/{id}/retry", sentRow).with(operator()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_FOR_RETRY"));
    }
}
