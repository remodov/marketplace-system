package ru.vikulinva.customer.adapter.in.rest.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("customerAccess")
public class CustomerAccess {

    private static final String SERVICE_READ_AUTHORITY = "SCOPE_customer.read";

    public boolean isSelf(UUID customerId, Authentication authentication) {
        if (authentication == null || customerId == null) {
            return false;
        }
        if (hasServiceReadScope(authentication)) {
            return true;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return customerId.toString().equals(jwt.getSubject());
        }
        return false;
    }

    private boolean hasServiceReadScope(Authentication authentication) {
        for (GrantedAuthority a : authentication.getAuthorities()) {
            if (SERVICE_READ_AUTHORITY.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
