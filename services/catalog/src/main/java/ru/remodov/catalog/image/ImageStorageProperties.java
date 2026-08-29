package ru.remodov.catalog.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.images")
public record ImageStorageProperties(String bucket,
                                     String endpoint,
                                     String region,
                                     String accessKey,
                                     String secretKey,
                                     long uploadTtlMinutes,
                                     long downloadTtlMinutes) {
}
