package com.cosmetics.inventory.whatsapp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppConfig {
}
