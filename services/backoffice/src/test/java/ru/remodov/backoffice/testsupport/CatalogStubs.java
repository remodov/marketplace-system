package ru.remodov.backoffice.testsupport;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.UUID;

public final class CatalogStubs {

    private CatalogStubs() {
    }

    public static void okHide(WireMockExtension wm, UUID productId) {
        wm.stubFor(post(urlEqualTo("/api/v1/products/" + productId + "/hide"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "%s",
                      "title": "stub",
                      "price": 1.00,
                      "currency": "RUB",
                      "sellerId": "00000000-0000-0000-0000-000000000099",
                      "status": "HIDDEN",
                      "createdAt": "2026-05-23T10:00:00Z",
                      "updatedAt": "2026-05-23T10:00:00Z"
                    }
                    """.formatted(productId))));
    }

    public static void conflictHide(WireMockExtension wm, UUID productId) {
        wm.stubFor(post(urlEqualTo("/api/v1/products/" + productId + "/hide"))
            .willReturn(aResponse()
                .withStatus(409)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("""
                    {
                      "type": "about:blank",
                      "status": 409,
                      "title": "Conflict",
                      "detail": "Already hidden",
                      "code": "INVALID_STATE_TRANSITION"
                    }
                    """)));
    }

    public static void unavailableHide(WireMockExtension wm, UUID productId) {
        wm.stubFor(post(urlEqualTo("/api/v1/products/" + productId + "/hide"))
            .willReturn(aResponse().withStatus(503)));
    }
}
