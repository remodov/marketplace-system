package ru.vikulinva.catalogstarter.product;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductRulesTest {

    private Product product(String price, int stock) {
        return new Product(UUID.randomUUID(), "Беспроводная мышь", new BigDecimal(price), stock);
    }

    @Test
    void discountLowersPrice() {
        Product product = product("1000.00", 5);

        product.applyDiscount(20);

        assertThat(product.getPrice()).isEqualByComparingTo("800.00");
    }

    @Test
    void discountIsRoundedToKopecks() {
        Product product = product("999.99", 5);

        product.applyDiscount(33);

        assertThat(product.getPrice()).isEqualByComparingTo("669.99");
    }

    @Test
    void halfPriceIsTheLimit() {
        Product product = product("1000.00", 5);

        assertThatCode(() -> product.applyDiscount(Product.MAX_DISCOUNT_PERCENT)).doesNotThrowAnyException();
        assertThat(product.getPrice()).isEqualByComparingTo("500.00");
    }

    @Test
    void deeperDiscountIsRejected() {
        Product product = product("1000.00", 5);

        assertThatThrownBy(() -> product.applyDiscount(60)).isInstanceOf(IllegalArgumentException.class);
        assertThat(product.getPrice()).isEqualByComparingTo("1000.00");
    }

    @Test
    void zeroAndNegativeDiscountsAreRejected() {
        Product product = product("1000.00", 5);

        assertThatThrownBy(() -> product.applyDiscount(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product.applyDiscount(-10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void priceStaysPositive() {
        Product product = product("1000.00", 5);

        assertThatThrownBy(() -> product.changePrice(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product.changePrice(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(product.getPrice()).isEqualByComparingTo("1000.00");
    }

    @Test
    void stockNeverGoesBelowZero() {
        Product product = product("1000.00", 3);

        assertThatThrownBy(() -> product.changeStock(-4)).isInstanceOf(OutOfStockException.class);
        assertThatThrownBy(() -> product.reserve(4)).isInstanceOf(OutOfStockException.class);
        assertThat(product.getStock()).isEqualTo(3);
    }

    @Test
    void stateIsChangedOnlyThroughMethods() {
        String setters = Arrays.stream(Product.class.getMethods())
            .map(Method::getName)
            .filter(name -> name.startsWith("set"))
            .reduce("", (a, b) -> a + " " + b);

        assertThat(setters).isBlank();
    }
}
