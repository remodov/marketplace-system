package ru.remodov.backoffice.catalog;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import ru.remodov.backoffice.catalog.dto.ProductView;
import ru.remodov.backoffice.catalog.exception.CatalogClientException;
import ru.remodov.backoffice.catalog.exception.CatalogServerException;
import ru.remodov.backoffice.catalog.exception.CatalogUnavailableException;
import ru.remodov.backoffice.catalog.generated.api.ProductsApi;
import ru.remodov.backoffice.catalog.generated.api.model.ProblemDetails;
import ru.remodov.backoffice.catalog.generated.api.model.ProductDto;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogClient {

    private final ProductsApi productsApi;
    private final CatalogMapper mapper;

    @CircuitBreaker(name = "catalog")
    @Bulkhead(name = "catalog")
    @Retry(name = "catalog")
    public ProductView getProduct(UUID productId) {
        try {
            ProductDto dto = productsApi.getProduct(productId);
            return mapper.toView(dto);
        } catch (HttpClientErrorException e) {
            throw asClientException(e);
        } catch (HttpServerErrorException e) {
            throw new CatalogServerException(e.getStatusCode().value(),
                "Catalog 5xx on getProduct " + productId, e);
        } catch (ResourceAccessException e) {
            throw new CatalogServerException(0, "Catalog network error on getProduct " + productId, e);
        } catch (CallNotPermittedException e) {
            throw new CatalogUnavailableException("Catalog circuit breaker open", e);
        }
    }

    @CircuitBreaker(name = "catalog")
    @Bulkhead(name = "catalog")
    public ProductView hideProduct(UUID productId) {
        try {
            ProductDto dto = productsApi.hideProduct(productId);
            return mapper.toView(dto);
        } catch (HttpClientErrorException e) {
            throw asClientException(e);
        } catch (HttpServerErrorException e) {
            throw new CatalogServerException(e.getStatusCode().value(),
                "Catalog 5xx on hideProduct " + productId, e);
        } catch (ResourceAccessException e) {
            throw new CatalogServerException(0, "Catalog network error on hideProduct " + productId, e);
        } catch (CallNotPermittedException e) {
            throw new CatalogUnavailableException("Catalog circuit breaker open", e);
        }
    }

    private CatalogClientException asClientException(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        String code = extractCode(e);
        return new CatalogClientException(status, code, "Catalog " + status + " (" + code + ")", e);
    }

    private String extractCode(HttpClientErrorException e) {
        try {
            ProblemDetails pd = e.getResponseBodyAs(ProblemDetails.class);
            if (pd != null && pd.getCode() != null) {
                return pd.getCode().getValue();
            }
        } catch (Exception parseError) {
            log.debug("Failed to parse Catalog ProblemDetails body", parseError);
        }
        return "UNKNOWN";
    }
}
