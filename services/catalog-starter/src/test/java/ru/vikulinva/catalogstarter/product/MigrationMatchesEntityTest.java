package ru.vikulinva.catalogstarter.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:catalog-migrated;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.liquibase.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class MigrationMatchesEntityTest {

    @Autowired
    DataSource dataSource;

    @Test
    void schemaBuiltByLiquibaseHasEveryColumn() throws Exception {
        List<String> columns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.getMetaData().getColumns(null, null, "PRODUCTS", null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }

        assertThat(columns).contains("id", "title", "price", "stock", "reserved", "version");
    }
}
