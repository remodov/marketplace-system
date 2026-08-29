package ru.remodov.backoffice.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.remodov.backoffice.generated.Tables.MODERATION_ACTIONS;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import ru.remodov.backoffice.generated.tables.pojos.ModerationActionsPojo;
import ru.remodov.backoffice.testsupport.BackofficeIntegrationTest;
import ru.remodov.backoffice.testsupport.CatalogStubs;
import ru.remodov.backoffice.testsupport.TestJwt;

class ModerationActionsIntegrationTest extends BackofficeIntegrationTest {

    private static final UUID MODERATOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_MODERATOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.of(2026, 5, 23, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    @DisplayName("AC-B1: Catalog 200 → 201 Created + ModerationAction записан с moderator_id, reason, request_id")
    void hideProductByModeration_whenCatalog200_then201AndPersisted() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID newActionId = UUID.fromString("00000000-aaaa-0000-0000-000000000001");
        given(uuidGenerator.generate()).willReturn(newActionId);
        given(dateTimeService.now()).willReturn(FIXED_NOW.toInstant());
        CatalogStubs.okHide(CATALOG_WM, productId);

        ResultActions r = mockMvc.perform(post("/api/v1/moderation-actions")
            .with(TestJwt.asModerator(MODERATOR_ID))
            .header("Idempotency-Key", requestId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"productId":"%s","reason":"PROHIBITED_GOODS","note":"test"}
                """.formatted(productId)));

        r.andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(newActionId.toString()))
            .andExpect(jsonPath("$.productId").value(productId.toString()))
            .andExpect(jsonPath("$.moderatorId").value(MODERATOR_ID.toString()))
            .andExpect(jsonPath("$.reason").value("PROHIBITED_GOODS"))
            .andExpect(jsonPath("$.note").value("test"));

        ModerationActionsPojo persisted = dsl.selectFrom(MODERATION_ACTIONS)
            .where(MODERATION_ACTIONS.ID.eq(newActionId))
            .fetchOneInto(ModerationActionsPojo.class);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getModeratorId()).isEqualTo(MODERATOR_ID);
        assertThat(persisted.getProductId()).isEqualTo(productId);
        assertThat(persisted.getRequestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("AC-B2: Catalog 409 INVALID_STATE_TRANSITION → 409 PRODUCT_ALREADY_HIDDEN; запись не создаётся")
    void hideProductByModeration_whenCatalogConflict_thenProductAlreadyHidden409() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        given(uuidGenerator.generate()).willReturn(UUID.randomUUID());
        given(dateTimeService.now()).willReturn(FIXED_NOW.toInstant());
        CatalogStubs.conflictHide(CATALOG_WM, productId);

        mockMvc.perform(post("/api/v1/moderation-actions")
                .with(TestJwt.asModerator(MODERATOR_ID))
                .header("Idempotency-Key", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"%s","reason":"PROHIBITED_GOODS"}
                    """.formatted(productId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PRODUCT_ALREADY_HIDDEN"));

        long rows = dsl.fetchCount(MODERATION_ACTIONS);
        assertThat(rows).isZero();
    }

    @Test
    @DisplayName("AC-B3: GET /my возвращает только записи moderator, sort decided_at DESC, pagination корректна")
    void listMyModerationActions_whenNRecordsExist_thenPageOfMineSortedDesc() {
        insertAction(MODERATOR_ID, "PROHIBITED_GOODS", FIXED_NOW.minusHours(2));
        insertAction(MODERATOR_ID, "OTHER", FIXED_NOW.minusHours(1));
        insertAction(OTHER_MODERATOR_ID, "OTHER", FIXED_NOW);

        try {
            mockMvc.perform(get("/api/v1/moderation-actions/my?size=10")
                    .with(TestJwt.asModerator(MODERATOR_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].moderatorId").value(MODERATOR_ID.toString()))
                .andExpect(jsonPath("$.content[1].moderatorId").value(MODERATOR_ID.toString()))
                .andExpect(jsonPath("$.content[0].reason").value("OTHER"))
                .andExpect(jsonPath("$.content[1].reason").value("PROHIBITED_GOODS"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("AC-B4: GET /api/v1/moderation-actions с ROLE_moderator (без admin) → 403 ACCESS_DENIED")
    void listModerationActions_whenModeratorRoleOnly_thenAccessDenied403() throws Exception {
        mockMvc.perform(get("/api/v1/moderation-actions")
                .with(TestJwt.asModerator(MODERATOR_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("AC-B5: admin GET /api/v1/moderation-actions с from/to — только записи в диапазоне")
    void listModerationActions_whenAdminFiltersByPeriod_thenOnlyInRange() throws Exception {
        OffsetDateTime t1 = FIXED_NOW.minusHours(3);
        OffsetDateTime t2 = FIXED_NOW.minusHours(2);
        OffsetDateTime t3 = FIXED_NOW.minusHours(1);
        insertAction(MODERATOR_ID, "OTHER", t1);
        insertAction(OTHER_MODERATOR_ID, "OTHER", t2);
        insertAction(MODERATOR_ID, "OTHER", t3);

        String from = FIXED_NOW.minusHours(2).minusMinutes(30).toString();
        String to = FIXED_NOW.minusHours(0).toString();

        mockMvc.perform(get("/api/v1/moderation-actions")
                .param("from", from)
                .param("to", to)
                .with(TestJwt.asAdmin(ADMIN_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("AC-B6: POST без reason → 400 VALIDATION_ERROR; Catalog не вызван")
    void hideProductByModeration_whenReasonEmpty_then400AndCatalogNotCalled() throws Exception {
        UUID productId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/moderation-actions")
                .with(TestJwt.asModerator(MODERATOR_ID))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"%s"}
                    """.formatted(productId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(CATALOG_WM.getAllServeEvents()).isEmpty();
        assertThat(dsl.fetchCount(MODERATION_ACTIONS)).isZero();
    }

    @Test
    @DisplayName("AC-B7: Catalog 503 → 503; ноль строк. Повтор после восстановления с тем же Idempotency-Key — одна запись.")
    void hideProductByModeration_whenCatalog503_then503AndIdempotentReplay() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID newActionId = UUID.fromString("00000000-bbbb-0000-0000-000000000001");
        given(uuidGenerator.generate()).willReturn(newActionId);
        given(dateTimeService.now()).willReturn(FIXED_NOW.toInstant());
        CatalogStubs.unavailableHide(CATALOG_WM, productId);

        String body = """
            {"productId":"%s","reason":"OTHER"}
            """.formatted(productId);

        mockMvc.perform(post("/api/v1/moderation-actions")
                .with(TestJwt.asModerator(MODERATOR_ID))
                .header("Idempotency-Key", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
        assertThat(dsl.fetchCount(MODERATION_ACTIONS)).isZero();

        CATALOG_WM.resetAll();
        CatalogStubs.okHide(CATALOG_WM, productId);

        mockMvc.perform(post("/api/v1/moderation-actions")
                .with(TestJwt.asModerator(MODERATOR_ID))
                .header("Idempotency-Key", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/moderation-actions")
                .with(TestJwt.asModerator(MODERATOR_ID))
                .header("Idempotency-Key", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(newActionId.toString()));

        assertThat(dsl.fetchCount(MODERATION_ACTIONS)).isEqualTo(1);
    }

    private void insertAction(UUID moderatorId, String reason, OffsetDateTime decidedAt) {
        dsl.insertInto(MODERATION_ACTIONS)
            .set(MODERATION_ACTIONS.ID, UUID.randomUUID())
            .set(MODERATION_ACTIONS.PRODUCT_ID, UUID.randomUUID())
            .set(MODERATION_ACTIONS.MODERATOR_ID, moderatorId)
            .set(MODERATION_ACTIONS.REASON, ru.remodov.backoffice.generated.enums.ModerationReason.valueOf(reason))
            .set(MODERATION_ACTIONS.NOTE, (String) null)
            .set(MODERATION_ACTIONS.DECIDED_AT, decidedAt)
            .set(MODERATION_ACTIONS.REQUEST_ID, UUID.randomUUID())
            .execute();
    }
}
