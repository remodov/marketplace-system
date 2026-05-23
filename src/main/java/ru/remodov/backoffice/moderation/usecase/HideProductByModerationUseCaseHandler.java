package ru.remodov.backoffice.moderation.usecase;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.remodov.backoffice.catalog.CatalogClient;
import ru.remodov.backoffice.catalog.exception.CatalogClientException;
import ru.remodov.backoffice.catalog.exception.CatalogServerException;
import ru.remodov.backoffice.core.service.DateTimeService;
import ru.remodov.backoffice.core.service.UuidGenerator;
import ru.remodov.backoffice.generated.tables.pojos.ModerationActionsPojo;
import ru.remodov.backoffice.moderation.exception.CatalogUnavailableException;
import ru.remodov.backoffice.moderation.exception.InvalidNoteException;
import ru.remodov.backoffice.moderation.exception.InvalidReasonException;
import ru.remodov.backoffice.moderation.exception.ProductAlreadyHiddenException;
import ru.remodov.backoffice.moderation.exception.ProductNotFoundException;
import ru.remodov.backoffice.moderation.repository.IdempotencyRecordRepository;
import ru.remodov.backoffice.moderation.repository.ModerationActionRepository;
import ru.vikulinva.usecase.UseCaseHandler;

@Component
@RequiredArgsConstructor
@Slf4j
public class HideProductByModerationUseCaseHandler
    implements UseCaseHandler<HideProductByModerationUseCase, ModerationActionView> {

    private static final int MAX_NOTE_LENGTH = 1000;

    private final CatalogClient catalogClient;
    private final ModerationActionRepository moderationRepo;
    private final IdempotencyRecordRepository idempotencyRepo;
    private final UuidGenerator uuidGenerator;
    private final DateTimeService dateTimeService;

    @Override
    public Class<HideProductByModerationUseCase> useCaseType() {
        return HideProductByModerationUseCase.class;
    }

    @Override
    @Transactional
    public ModerationActionView handle(HideProductByModerationUseCase cmd) {
        validate(cmd);

        Optional<ModerationActionsPojo> existing = moderationRepo.findByRequestId(cmd.requestId());
        if (existing.isPresent()) {
            log.debug("Idempotent replay for requestId={}", cmd.requestId());
            return toView(existing.get());
        }

        callCatalog(cmd);

        ModerationActionsPojo pojo = new ModerationActionsPojo();
        pojo.setId(uuidGenerator.generate());
        pojo.setProductId(cmd.productId());
        pojo.setModeratorId(cmd.moderatorId());
        pojo.setReason(cmd.reason());
        pojo.setNote(cmd.note());
        pojo.setDecidedAt(OffsetDateTime.ofInstant(dateTimeService.now(), ZoneOffset.UTC));
        pojo.setRequestId(cmd.requestId());

        moderationRepo.insert(pojo);
        idempotencyRepo.insert(cmd.requestId(), pojo.getId().toString(), pojo.getDecidedAt());

        log.info("audit action=PRODUCT_HIDDEN moderationActionId={} moderatorId={} productId={} reason={} requestId={}",
            pojo.getId(), cmd.moderatorId(), cmd.productId(), cmd.reason(), cmd.requestId());

        return toView(pojo);
    }

    private void validate(HideProductByModerationUseCase cmd) {
        if (cmd.reason() == null) {
            throw new InvalidReasonException("reason is required and must be from controlled vocabulary");
        }
        if (cmd.note() != null && cmd.note().length() > MAX_NOTE_LENGTH) {
            throw new InvalidNoteException("note must be at most " + MAX_NOTE_LENGTH + " chars");
        }
    }

    private void callCatalog(HideProductByModerationUseCase cmd) {
        try {
            catalogClient.hideProduct(cmd.productId());
        } catch (CatalogClientException e) {
            throw switch (e.getCode()) {
                case "INVALID_STATE_TRANSITION" -> new ProductAlreadyHiddenException(cmd.productId(), e);
                case "PRODUCT_NOT_FOUND", "OWN_PRODUCT_REQUIRED" -> new ProductNotFoundException(cmd.productId(), e);
                default -> e;
            };
        } catch (CatalogServerException | ru.remodov.backoffice.catalog.exception.CatalogUnavailableException e) {
            throw new CatalogUnavailableException(e);
        }
    }

    private ModerationActionView toView(ModerationActionsPojo p) {
        return new ModerationActionView(
            p.getId(),
            p.getProductId(),
            p.getModeratorId(),
            p.getReason(),
            p.getNote(),
            p.getDecidedAt()
        );
    }
}
