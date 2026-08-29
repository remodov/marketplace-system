package ru.vikulinva.catalogstarter.search;

import java.math.BigDecimal;

/**
 * Во что превращается фраза покупателя: текст для поиска по названию и, если
 * из фразы это следует, потолок цены и требование наличия.
 */
public record SearchFilters(String text, BigDecimal maxPrice, boolean inStockOnly) {

    public static SearchFilters plainText(String query) {
        return new SearchFilters(query, null, false);
    }
}
