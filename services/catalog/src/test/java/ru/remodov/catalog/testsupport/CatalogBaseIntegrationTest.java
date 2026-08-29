package ru.remodov.catalog.testsupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

public abstract class CatalogBaseIntegrationTest extends PlatformBaseIntegrationTest {

    @Autowired protected TestRestTemplate restTemplate;
    @Autowired protected CatalogDatabasePreparer databasePreparer;
}
