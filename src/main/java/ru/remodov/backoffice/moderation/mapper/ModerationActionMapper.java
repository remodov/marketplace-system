package ru.remodov.backoffice.moderation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.remodov.backoffice.generated.api.model.ModerationActionDto;
import ru.remodov.backoffice.generated.api.model.ModerationActionPageDto;
import ru.remodov.backoffice.generated.api.model.ModerationReasonDto;
import ru.remodov.backoffice.generated.enums.ModerationReason;
import ru.remodov.backoffice.moderation.usecase.ModerationActionPage;
import ru.remodov.backoffice.moderation.usecase.ModerationActionView;

@Mapper(componentModel = "spring")
public interface ModerationActionMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "productId", source = "productId")
    @Mapping(target = "moderatorId", source = "moderatorId")
    @Mapping(target = "reason", source = "reason")
    @Mapping(target = "note", source = "note")
    @Mapping(target = "decidedAt", source = "decidedAt")
    ModerationActionDto toDto(ModerationActionView view);

    default ModerationActionPageDto toPageDto(ModerationActionPage page) {
        ModerationActionPageDto dto = new ModerationActionPageDto();
        dto.setContent(page.content().stream().map(this::toDto).toList());
        dto.setPage(page.page());
        dto.setSize(page.size());
        dto.setTotalElements(page.totalElements());
        dto.setTotalPages(page.totalPages());
        return dto;
    }

    default ModerationReason toJooqReason(ModerationReasonDto dto) {
        if (dto == null) {
            return null;
        }
        return switch (dto) {
            case PROHIBITED_GOODS -> ModerationReason.PROHIBITED_GOODS;
            case MISLEADING_TITLE -> ModerationReason.MISLEADING_TITLE;
            case INVALID_PRICE -> ModerationReason.INVALID_PRICE;
            case OTHER -> ModerationReason.OTHER;
        };
    }

    default ModerationReasonDto toDtoReason(ModerationReason reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case PROHIBITED_GOODS -> ModerationReasonDto.PROHIBITED_GOODS;
            case MISLEADING_TITLE -> ModerationReasonDto.MISLEADING_TITLE;
            case INVALID_PRICE -> ModerationReasonDto.INVALID_PRICE;
            case OTHER -> ModerationReasonDto.OTHER;
        };
    }
}
