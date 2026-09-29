package ru.vikulinva.catalogstarter.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vikulinva.catalogstarter.product.Product;
import ru.vikulinva.catalogstarter.product.ProductRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Чем наполняется база знаний: правила магазина из файлов и карточки товаров
 * из каталога.
 *
 * <p>Товары попадают сюда намеренно. Половина вопросов покупателя — не про
 * правила, а про ассортимент («есть ли беспроводная мышь до двух тысяч»), и
 * отвечать на них по общим документам нечем.
 *
 * <p>Переиндексация здесь ручная и полная. В настоящем магазине карточка
 * меняется каждый день, и полный пересчёт становится дорогим: тогда куски
 * обновляют по событию об изменении товара, а не пересчитывают всё.
 */
@Component
public class ShopKnowledge {

    private static final Logger log = LoggerFactory.getLogger(ShopKnowledge.class);

    private final KnowledgeBase base;
    private final ProductRepository products;

    public ShopKnowledge(KnowledgeBase base, ProductRepository products) {
        this.base = base;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public void reindex() {
        base.clear();
        indexDocuments();
        indexProducts();
        log.info("База знаний собрана: кусков {}", base.size());
    }

    private void indexDocuments() {
        try {
            Resource[] found = new PathMatchingResourcePatternResolver()
                .getResources("classpath:knowledge/*.md");
            for (Resource resource : found) {
                String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                base.index(resource.getFilename(), text);
            }
        } catch (IOException e) {
            throw new IllegalStateException("не прочитались документы магазина", e);
        }
    }

    /* Карточка кладётся одним куском и фразой, а не полями через запятую:
       вопрос задают словами, и искать придётся по словам. */
    private void indexProducts() {
        for (Product product : products.findAll()) {
            String card = "Товар «%s». Цена %s рублей. %s".formatted(
                product.getTitle(),
                product.getPrice().stripTrailingZeros().toPlainString(),
                product.available() > 0 ? "В наличии " + product.available() + " штук." : "Нет в наличии.");
            base.index("каталог: " + product.getTitle(), card);
        }
    }
}
