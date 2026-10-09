package com.coc.sba_treektour.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;

@Configuration
@ConfigurationProperties(prefix = "zalopay")
@Validated
@Getter
@Setter
public class ZaloPayConfig {
    @Min(1)
    private int appId;

    @NotBlank
    private String key1;

    @NotBlank
    private String key2;

    @NotBlank
    private String createUrl;

    @NotBlank
    private String queryUrl;

    @NotBlank
    private String callbackUrl;

    @Bean
    RestClient zaloPayRestClient() {
        return RestClient.create();
    }
}
