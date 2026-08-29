package ru.remodov.catalog.image;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * Ссылки на загрузку и скачивание изображений товара.
 *
 * <p>Файл не проходит через сервис: браузер кладёт его прямо в хранилище по
 * временной подписанной ссылке. Сервис решает, кому её выдать, и не тратит на
 * гигабайты ни поток, ни память.
 */
@Service
public class ProductImageService {

    private final S3Presigner presigner;
    private final ImageStorageProperties properties;

    public ProductImageService(S3Presigner presigner, ImageStorageProperties properties) {
        this.presigner = presigner;
        this.properties = properties;
    }

    public PresignedUpload presignUpload(UUID productId, String contentType) {
        // TODO шаг 12: подписанная ссылка на загрузку.
        // Ключ объекта должен говорить, чей это файл; срок жизни ссылки — из настроек.
        // Presigner уже настроен на хранилище стенда, ходить в сеть для подписи не нужно.
        throw new UnsupportedOperationException("Шаг 12: ссылка на загрузку не реализована");
    }

    public String presignDownload(String key) {
        GetObjectRequest get = GetObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(properties.downloadTtlMinutes()))
            .getObjectRequest(get)
            .build()).url().toString();
    }

    public record PresignedUpload(String key, String url, String expiresAt) {}
}
