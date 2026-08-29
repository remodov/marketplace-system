package ru.remodov.catalog.testsupport;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public final class TestHttpHeaders {

    private TestHttpHeaders() {}

    public static HttpHeaders withSellerToken(UUID sellerId) {
        return token("seller", sellerId);
    }

    public static HttpHeaders withAdminToken(UUID adminId) {
        return token("admin", adminId);
    }

    public static HttpHeaders withCustomerToken(UUID customerId) {
        return token("customer", customerId);
    }

    public static HttpHeaders withServiceAccountToken() {
        var h = new HttpHeaders();
        h.setBearerAuth(FakeJwtDecoder.SERVICE_ACCOUNT_TOKEN);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    public static HttpHeaders anonymous() {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private static HttpHeaders token(String role, UUID id) {
        var h = new HttpHeaders();
        h.setBearerAuth(role + "." + id);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
