package ru.vikulinva.orderservice.adapter.out.catalog.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.vikulinva.hexagonal.OutboundAdapter;
import ru.vikulinva.orderservice.adapter.out.catalog.dto.CatalogProductResponse;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.port.out.CatalogPort;
import ru.vikulinva.orderservice.usecase.command.exception.ProductNotFoundException;

import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST-клиент Catalog Service.
 * Resilience4j: Retry + CircuitBreaker по конфигурации {@code resilience4j.*.instances.catalog}.
 * При недоступности → доменное исключение {@link CatalogUnavailableException} (→ 503).
 * Если хотя бы один товар не найден → {@link ProductNotFoundException} (→ 404).
 */
@Component
@OutboundAdapter("Catalog Service REST client")
public class CatalogRestClient implements CatalogPort {

    private static final String INSTANCE = "catalog";

    private final RestTemplate restTemplate;

    public CatalogRestClient(@Qualifier("catalogRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // TODO шаг 8: сосед может отвечать медленно, срываться и лежать.
    // Повтор и размыкатель включаются аннотациями resilience4j с именем инстанса
    // INSTANCE; отказ должен превращаться в доменное исключение, а не утекать
    // наружу типом из клиента Spring.
    @Override
    public Map<ProductId, Money> getPrices(List<ProductId> productIds) {
        Map<ProductId, Money> prices = new HashMap<>(productIds.size());
        for (ProductId productId : productIds) {
            CatalogProductResponse response = fetchOne(productId);
            Currency currency = Currency.getInstance(response.currency());
            prices.put(productId, new Money(response.price(), currency));
        }
        return prices;
    }

    private CatalogProductResponse fetchOne(ProductId productId) {
        try {
            CatalogProductResponse body = restTemplate.getForObject(
                "/api/v1/products/{id}", CatalogProductResponse.class, productId.value());
            if (body == null) {
                throw new ProductNotFoundException(List.of(productId));
            }
            return body;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ProductNotFoundException(List.of(productId));
            }
            throw ex;
        }
    }

    // TODO шаг 8: метод-подстраховка на случай отказа.
    // Осторожно: «товар не найден» — это не отказ соседа, и превращать одно
    // в другое нельзя, иначе покупатель увидит 503 вместо 404.
}
