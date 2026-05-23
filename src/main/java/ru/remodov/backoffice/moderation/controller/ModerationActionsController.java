package ru.remodov.backoffice.moderation.controller;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.remodov.backoffice.auth.ModeratorContext;
import ru.remodov.backoffice.generated.api.ModerationActionsApi;
import ru.remodov.backoffice.generated.api.model.HideProductByModerationRequest;
import ru.remodov.backoffice.generated.api.model.ModerationActionDto;
import ru.remodov.backoffice.generated.api.model.ModerationActionPageDto;
import ru.remodov.backoffice.generated.api.model.ModerationReasonDto;
import ru.remodov.backoffice.moderation.mapper.ModerationActionMapper;
import ru.remodov.backoffice.moderation.usecase.HideProductByModerationUseCase;
import ru.remodov.backoffice.moderation.usecase.ListModerationActionsQuery;
import ru.remodov.backoffice.moderation.usecase.ListMyModerationActionsQuery;
import ru.remodov.backoffice.moderation.usecase.ModerationActionPage;
import ru.remodov.backoffice.moderation.usecase.ModerationActionView;
import ru.vikulinva.usecase.UseCaseDispatcher;

@RestController
@RequiredArgsConstructor
public class ModerationActionsController implements ModerationActionsApi {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;

    private final UseCaseDispatcher dispatcher;
    private final ModerationActionMapper mapper;
    private final ModeratorContext moderatorContext;

    @Override
    @PreAuthorize("hasAnyRole('moderator', 'admin')")
    public ResponseEntity<ModerationActionDto> hideProductByModeration(
        UUID idempotencyKey,
        HideProductByModerationRequest request,
        UUID xModeratorId
    ) {
        UUID moderatorId = moderatorContext.currentModeratorId(xModeratorId);
        HideProductByModerationUseCase useCase = new HideProductByModerationUseCase(
            idempotencyKey,
            request.getProductId(),
            moderatorId,
            mapper.toJooqReason(request.getReason()),
            request.getNote()
        );
        ModerationActionView view = dispatcher.dispatch(useCase);
        return ResponseEntity
            .created(URI.create("/api/v1/moderation-actions/" + view.id()))
            .body(mapper.toDto(view));
    }

    @Override
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ModerationActionPageDto> listModerationActions(
        UUID moderatorId,
        OffsetDateTime from,
        OffsetDateTime to,
        ModerationReasonDto reason,
        Integer page,
        Integer size
    ) {
        ListModerationActionsQuery query = new ListModerationActionsQuery(
            moderatorId,
            from,
            to,
            mapper.toJooqReason(reason),
            page != null ? page : DEFAULT_PAGE,
            size != null ? size : DEFAULT_SIZE
        );
        ModerationActionPage result = dispatcher.dispatch(query);
        return ResponseEntity.ok(mapper.toPageDto(result));
    }

    @Override
    @PreAuthorize("hasAnyRole('moderator', 'admin')")
    public ResponseEntity<ModerationActionPageDto> listMyModerationActions(
        OffsetDateTime from,
        OffsetDateTime to,
        Integer page,
        Integer size,
        UUID xModeratorId
    ) {
        UUID moderatorId = moderatorContext.currentModeratorId(xModeratorId);
        ListMyModerationActionsQuery query = new ListMyModerationActionsQuery(
            moderatorId,
            from,
            to,
            page != null ? page : DEFAULT_PAGE,
            size != null ? size : DEFAULT_SIZE
        );
        ModerationActionPage result = dispatcher.dispatch(query);
        return ResponseEntity.ok(mapper.toPageDto(result));
    }
}
