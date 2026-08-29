package ru.remodov.catalog.image;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
public class ImageStorageConfig {

    @Bean
    public S3Presigner s3Presigner(ImageStorageProperties properties) {
        return S3Presigner.builder()
            .region(Region.of(properties.region()))
            .endpointOverride(URI.create(properties.endpoint()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
            .build();
    }
}
