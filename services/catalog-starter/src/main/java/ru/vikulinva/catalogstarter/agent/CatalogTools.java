package ru.vikulinva.catalogstarter.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.vikulinva.catalogstarter.product.OutOfStockException;
import ru.vikulinva.catalogstarter.product.Product;
import ru.vikulinva.catalogstarter.product.ProductNotFoundException;
import ru.vikulinva.catalogstarter.product.ProductService;

import java.util.List;
import java.util.UUID;

/**
 * Инструменты агента поверх каталога.
 *
 * <p>Это обычные методы сервиса, завёрнутые в описание для модели. Важное здесь
 * не код, а то, что агенту НЕ дано: сменить цену, удалить товар, выполнить
 * произвольный запрос. Набор инструментов — это и есть граница его полномочий,
 * и сужать её надёжнее, чем просить в подсказке «ничего не ломай». Скидку,
 * например, агент придумает охотно, а отвечать за неё будет магазин — поэтому
 * инструмента на цену здесь нет.
 */
@Configuration
public class CatalogTools {

    @Bean
    Tool findProductsTool(ProductService products) {
        return new Tool() {
            @Override
            public String name() {
                return "find_products";
            }

            @Override
            public String description() {
                return "найти товары по части названия; аргументы: {\"query\": \"мышь\"}";
            }

            @Override
            public String run(JsonNode arguments) {
                String query = text(arguments, "query");
                if (query.isBlank()) {
                    return "нужен аргумент query";
                }
                List<Product> found = products.search(query);
                if (found.isEmpty()) {
                    return "ничего не найдено по запросу " + query;
                }
                return found.stream()
                    .map(p -> "%s | %s руб | доступно %d | id %s".formatted(
                        p.getTitle(), p.getPrice().stripTrailingZeros().toPlainString(),
                        p.available(), p.getId()))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            }
        };
    }

    @Bean
    Tool stockTool(ProductService products) {
        return new Tool() {
            @Override
            public String name() {
                return "stock";
            }

            @Override
            public String description() {
                return "остаток товара по идентификатору; аргументы: {\"id\": \"<uuid>\"}";
            }

            @Override
            public String run(JsonNode arguments) {
                try {
                    Product product = products.byId(UUID.fromString(text(arguments, "id")));
                    return "%s: доступно %d".formatted(product.getTitle(), product.available());
                } catch (IllegalArgumentException e) {
                    return "идентификатор не похож на uuid";
                } catch (ProductNotFoundException e) {
                    return "товара с таким идентификатором нет";
                }
            }
        };
    }

    /**
     * Изменяющий инструмент: резерв уменьшает доступный остаток, и ошибочный
     * вызов виден покупателям сразу.
     */
    @Bean
    Tool reserveTool(ProductService products) {
        return new Tool() {
            @Override
            public String name() {
                return "reserve";
            }

            @Override
            public String description() {
                return "зарезервировать товар; аргументы: {\"id\": \"<uuid>\", \"quantity\": 1}";
            }

            @Override
            public boolean mutating() {
                return true;
            }

            @Override
            public String run(JsonNode arguments) {
                try {
                    Product product = products.reserve(
                        UUID.fromString(text(arguments, "id")),
                        arguments.path("quantity").asInt(1));
                    return "зарезервировано, осталось доступно " + product.available();
                } catch (IllegalArgumentException e) {
                    return "идентификатор не похож на uuid";
                } catch (ProductNotFoundException e) {
                    return "товара с таким идентификатором нет";
                } catch (OutOfStockException e) {
                    return "столько нет в наличии";
                }
            }
        };
    }

    private static String text(JsonNode arguments, String field) {
        return arguments == null ? "" : arguments.path(field).asText("");
    }
}
