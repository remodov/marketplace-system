package ru.vikulinva.catalogstarter.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductReserveConcurrencyTest {

    private static final int BUYERS = 100;
    private static final int STOCK = 10;

    @Autowired
    ProductService service;

    @Autowired
    ProductRepository repository;

    @Test
    void hundredBuyersSellExactlyTheStock() throws Exception {
        UUID productId = service.create("Билет на распродажу", new BigDecimal("100.00"), STOCK).getId();

        int sold;
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<Callable<Boolean>> buyers = IntStream.range(0, BUYERS)
                .<Callable<Boolean>>mapToObj(i -> () -> {
                    try {
                        service.reserve(productId, 1);
                        return true;
                    } catch (RuntimeException e) {
                        return false;
                    }
                })
                .toList();

            int successes = 0;
            for (Future<Boolean> result : pool.invokeAll(buyers)) {
                if (result.get()) {
                    successes++;
                }
            }
            sold = successes;
        }

        Product product = repository.findById(productId).orElseThrow();

        assertThat(sold).as("успешных резервов").isEqualTo(STOCK);
        assertThat(product.getReserved()).isEqualTo(STOCK);
        assertThat(product.available()).isZero();
        assertThat(product.getStock()).as("остаток на складе резерв не трогает").isEqualTo(STOCK);
    }
}
