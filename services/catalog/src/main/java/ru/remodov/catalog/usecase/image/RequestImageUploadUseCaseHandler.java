package ru.remodov.catalog.usecase.image;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.remodov.catalog.domain.Product;
import ru.remodov.catalog.exception.OwnProductRequiredException;
import ru.remodov.catalog.exception.ProductNotFoundException;
import ru.remodov.catalog.image.ProductImageService;
import ru.remodov.catalog.repository.ProductRepository;
import ru.vikulinva.usecase.UseCaseHandler;

@Component
@RequiredArgsConstructor
public class RequestImageUploadUseCaseHandler
    implements UseCaseHandler<RequestImageUploadUseCase, ProductImageService.PresignedUpload> {

    private final ProductRepository repo;
    private final ProductImageService images;

    @Override
    public Class<RequestImageUploadUseCase> useCaseType() { return RequestImageUploadUseCase.class; }

    @Override
    @Transactional(readOnly = true)
    public ProductImageService.PresignedUpload handle(RequestImageUploadUseCase uc) {
        // TODO шаг 12: ссылку на загрузку получает только владелец товара.
        // Карточку смотреть может кто угодно, а грузить в неё файлы — нет.
        // Чужой товар для не-владельца должен выглядеть как несуществующий.
        Product product = repo.findById(uc.productId().value(), ProductRepository.SelectMode.NO_LOCK)
            .orElseThrow(() -> new ProductNotFoundException(uc.productId().value()));

        return images.presignUpload(product.id(), uc.contentType());
    }
}
