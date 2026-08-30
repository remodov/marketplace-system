package ru.remodov.backoffice.catalog;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.remodov.backoffice.catalog.dto.ProductStatus;
import ru.remodov.backoffice.catalog.dto.ProductView;
import ru.remodov.backoffice.catalog.generated.api.model.ProductDto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CatalogMapperTest {

    private static final UUID PRODUCT_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID SELLER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    private static final String TITLE = "iPhone 15 Pro 256GB";
    private static final String DESCRIPTION = "Б/у, состояние идеальное, в комплекте чехол";
    private static final BigDecimal PRICE = new BigDecimal("89990.00");

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 5, 23, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.of(2026, 6, 24, 11, 1, 1, 0, ZoneOffset.UTC);

    private final CatalogMapper mapper = new CatalogMapper();

    @Test
    @DisplayName("toView переносит все девять полей, не перепутав два соседних момента времени")
    void toViewCarriesEveryField() {
        ProductView view = mapper.toView(fullProduct());

        SoftAssertions.assertSoftly(each -> {
            each.assertThat(view.id()).as("id").isEqualTo(PRODUCT_ID);
            each.assertThat(view.title()).as("title").isEqualTo(TITLE);
            each.assertThat(view.description()).as("description").isEqualTo(DESCRIPTION);
            each.assertThat(view.price()).as("price").isEqualTo(PRICE);
            each.assertThat(view.currency()).as("currency").isEqualTo("RUB");
            each.assertThat(view.sellerId()).as("sellerId").isEqualTo(SELLER_ID);
            each.assertThat(view.status()).as("status").isEqualTo(ProductStatus.PUBLISHED);
            each.assertThat(view.createdAt()).as("createdAt").isEqualTo(CREATED_AT);
            each.assertThat(view.updatedAt()).as("updatedAt").isEqualTo(UPDATED_AT);
        });
    }

    @Test
    @DisplayName("currency берётся из wire-значения контракта, а не из имени константы")
    void currencyComesFromWireValue() {
        ProductView view = mapper.toView(fullProduct());

        assertThat(view.currency()).isEqualTo(ProductDto.CurrencyEnum.RUB.getValue());
    }

    @ParameterizedTest
    @CsvSource({"DRAFT,DRAFT", "PUBLISHED,PUBLISHED", "HIDDEN,HIDDEN"})
    @DisplayName("каждый статус контракта переносится в одноимённый статус backoffice")
    void everyStatusIsCarried(
        ru.remodov.backoffice.catalog.generated.api.model.ProductStatus source,
        ProductStatus expected
    ) {
        ProductView view = mapper.toView(fullProduct().status(source));

        assertThat(view.status()).isEqualTo(expected);
    }

    @Test
    @DisplayName("наборы констант статуса совпадают — новый статус в контракте не пройдёт молча")
    void statusConstantSetsMatch() {
        assertThat(ru.remodov.backoffice.catalog.generated.api.model.ProductStatus.values())
            .extracting(Enum::name)
            .containsExactly("DRAFT", "PUBLISHED", "HIDDEN");
        assertThat(ProductStatus.values())
            .extracting(Enum::name)
            .containsExactly("DRAFT", "PUBLISHED", "HIDDEN");
    }

    @Test
    @DisplayName("товар без описания даёт null в description, остальные поля сохраняются")
    void missingDescriptionBecomesNull() {
        ProductView view = mapper.toView(fullProduct().description(null));

        assertThat(view.description()).isNull();
        assertThat(view.title()).isEqualTo(TITLE);
        assertThat(view.price()).isEqualTo(PRICE);
    }

    @Test
    @DisplayName("товар без currency даёт null, а не имя константы")
    void missingCurrencyBecomesNull() {
        ProductView view = mapper.toView(fullProduct().currency(null));

        assertThat(view.currency()).isNull();
    }

    @Test
    @DisplayName("товар без статуса даёт null в status")
    void missingStatusBecomesNull() {
        ProductView view = mapper.toView(fullProduct().status(null));

        assertThat(view.status()).isNull();
    }

    @Test
    @DisplayName("пустой ответ каталога роняет маппинг NullPointerException — не отдаёт null-view")
    void nullProductThrows() {
        assertThatNullPointerException().isThrownBy(() -> mapper.toView(null));
    }

    private static ProductDto fullProduct() {
        return new ProductDto()
            .id(PRODUCT_ID)
            .title(TITLE)
            .description(DESCRIPTION)
            .price(PRICE)
            .currency(ProductDto.CurrencyEnum.RUB)
            .sellerId(SELLER_ID)
            .status(ru.remodov.backoffice.catalog.generated.api.model.ProductStatus.PUBLISHED)
            .createdAt(CREATED_AT)
            .updatedAt(UPDATED_AT);
    }
}
