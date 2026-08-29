package ru.vikulinva.bff.screen;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "downstream")
public record DownstreamProperties(String order, String catalog, String payment) {
}
