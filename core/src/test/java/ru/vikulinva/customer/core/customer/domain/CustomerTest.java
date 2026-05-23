package ru.vikulinva.customer.core.customer.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.vikulinva.customer.core.customer.domain.aggregate.Customer;
import ru.vikulinva.customer.core.customer.domain.exception.ProfileUpdateForbiddenStatusException;
import ru.vikulinva.customer.core.customer.domain.exception.TokenInvalidOrExpiredException;
import ru.vikulinva.customer.core.customer.domain.exception.ValidationException;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;
import ru.vikulinva.customer.core.customer.domain.valueobject.Email;
import ru.vikulinva.customer.core.customer.domain.valueobject.Name;
import ru.vikulinva.customer.core.customer.domain.valueobject.Phone;
import ru.vikulinva.customer.core.customer.domain.valueobject.Profile;
import ru.vikulinva.customer.core.customer.domain.valueobject.Status;
import ru.vikulinva.customer.core.customer.domain.valueobject.VerificationTokenValue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerTest {

    private static final Instant NOW = Instant.parse("2026-05-23T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("BR-C08 + register: создаёт PENDING_VERIFICATION с токеном и эмитит CustomerRegistered")
    void register_setsPendingStatusAndIssuesToken() {
        Customer customer = registerCustomer();

        assertThat(customer.getStatus()).isEqualTo(Status.PENDING_VERIFICATION);
        assertThat(customer.getTokens()).hasSize(1);
        assertThat(customer.getTokens().get(0).getExpiresAt())
                .isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("BR-C02..C04 + verifyEmail: валидный неистёкший token → ACTIVE")
    void verifyEmail_withValidToken_activatesCustomer() {
        Customer customer = registerCustomer();
        VerificationTokenValue token = customer.getTokens().get(0).getId();

        customer.verifyEmail(token, CLOCK);

        assertThat(customer.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(customer.getTokens().get(0).isUsed()).isTrue();
    }

    @Test
    @DisplayName("BR-C04: просроченный токен → TokenInvalidOrExpiredException")
    void verifyEmail_withExpiredToken_throws() {
        Customer customer = registerCustomer();
        VerificationTokenValue token = customer.getTokens().get(0).getId();
        Clock future = Clock.fixed(NOW.plus(Duration.ofHours(25)), ZoneOffset.UTC);

        assertThatThrownBy(() -> customer.verifyEmail(token, future))
                .isInstanceOf(TokenInvalidOrExpiredException.class);
    }

    @Test
    @DisplayName("BR-C03: повторное использование токена → TokenInvalidOrExpiredException")
    void verifyEmail_withAlreadyUsedToken_throws() {
        Customer customer = registerCustomer();
        VerificationTokenValue token = customer.getTokens().get(0).getId();
        customer.verifyEmail(token, CLOCK);

        assertThatThrownBy(() -> customer.verifyEmail(token, CLOCK))
                .isInstanceOf(TokenInvalidOrExpiredException.class);
    }

    @Test
    @DisplayName("BR-C06: updateProfile в PENDING_VERIFICATION → ProfileUpdateForbiddenStatusException")
    void updateProfile_whenPendingVerification_throws() {
        Customer customer = registerCustomer();
        Profile newProfile = Profile.of(Name.of("New", "Name"));

        assertThatThrownBy(() -> customer.updateProfile(newProfile, CLOCK))
                .isInstanceOf(ProfileUpdateForbiddenStatusException.class);
    }

    @Test
    @DisplayName("BR-C06: updateProfile из ACTIVE обновляет профиль")
    void updateProfile_whenActive_updatesProfile() {
        Customer customer = registerCustomer();
        VerificationTokenValue token = customer.getTokens().get(0).getId();
        customer.verifyEmail(token, CLOCK);

        Profile newProfile = new Profile(
                Name.of("Ivan2", "Petrov2"),
                Optional.of(Phone.of("+79991112233")));
        customer.updateProfile(newProfile, CLOCK);

        assertThat(customer.getProfile().firstName()).isEqualTo("Ivan2");
        assertThat(customer.getProfile().phone()).hasValue(Phone.of("+79991112233"));
    }

    @Test
    @DisplayName("BR-C08: пустое firstName → ValidationException")
    void name_withBlankFirstName_throwsValidation() {
        assertThatThrownBy(() -> Name.of(" ", "Petrov"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Email нормализуется в lower-case")
    void email_isNormalizedToLowerCase() {
        Email e = Email.of("Buyer@Example.COM");
        assertThat(e.value()).isEqualTo("buyer@example.com");
    }

    @Test
    @DisplayName("Phone non-E.164 → ValidationException")
    void phone_nonE164_throws() {
        assertThatThrownBy(() -> Phone.of("8-999-123-45-67"))
                .isInstanceOf(ValidationException.class);
    }

    private Customer registerCustomer() {
        return Customer.register(
                CustomerId.of(UUID.randomUUID()),
                Email.of("buyer@example.com"),
                Profile.of(Name.of("Ivan", "Petrov")),
                VerificationTokenValue.of("abcdefghijklmnopqrstuvwxyz012345"),
                CLOCK);
    }
}
