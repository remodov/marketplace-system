package ru.vikulinva.orderservice.port.out;

import ru.vikulinva.hexagonal.OutboundPort;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;

import java.util.List;
import java.util.Map;

/**
 * Порт во внешний Catalog Service. Реализация — в {@code adapter-out-catalog}.
 *
 * @see ru.vikulinva.orderservice.usecase.command.exception.CatalogUnavailableException
 * @see ru.vikulinva.orderservice.usecase.command.exception.ProductNotFoundException
 */
@OutboundPort
public interface CatalogPort {

    /**
     * Получить цены товаров. Если хотя бы один товар не найден — бросает
     * {@code ProductNotFoundException}.
     *
     * @param productIds непустой список идентификаторов товаров
     * @return цены в порядке запроса (Map для O(1) lookup)
     */
    Map<ProductId, Money> getPrices(List<ProductId> productIds);
}
