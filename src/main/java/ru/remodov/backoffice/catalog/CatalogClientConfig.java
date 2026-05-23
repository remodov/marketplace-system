package ru.remodov.backoffice.catalog;

import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ru.remodov.backoffice.catalog.generated.api.ProductsApi;
import ru.remodov.backoffice.catalog.generated.api.invoker.ApiClient;

@Configuration
@EnableConfigurationProperties(CatalogClientSettings.class)
public class CatalogClientConfig {

    @Bean("catalogRestClient")
    public RestClient catalogRestClient(CatalogClientSettings settings) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(settings.connectTimeout())
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.readTimeout());
        return RestClient.builder()
            .baseUrl(settings.baseUrl())
            .requestFactory(requestFactory)
            .defaultHeader("Authorization", "Bearer " + settings.adminToken())
            .build();
    }

    @Bean
    public ApiClient catalogApiClient(@Qualifier("catalogRestClient") RestClient restClient,
                                      CatalogClientSettings settings) {
        ApiClient apiClient = new ApiClient(restClient);
        apiClient.setBasePath(settings.baseUrl());
        return apiClient;
    }

    @Bean
    public ProductsApi catalogProductsApi(ApiClient apiClient) {
        return new ProductsApi(apiClient);
    }
}
