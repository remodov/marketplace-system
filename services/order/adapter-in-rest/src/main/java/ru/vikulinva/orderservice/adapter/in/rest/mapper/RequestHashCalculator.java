package ru.vikulinva.orderservice.adapter.in.rest.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Считает sha256 от стабильной (sorted) JSON-сериализации тела запроса —
 * для проверки равенства идемпотентных запросов (BR-010).
 */
@Component
public class RequestHashCalculator {

    private final ObjectMapper canonical;

    public RequestHashCalculator(ObjectMapper baseMapper) {
        this.canonical = baseMapper.copy()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String hash(Object body) {
        try {
            byte[] bytes = canonical.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute request hash", e);
        }
    }
}
