package com.hastane.merkezi_randevu_sistemi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class WebConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*"); // Tüm portlara izin ver
        config.addAllowedHeader("*"); // Tüm başlıklara izin ver
        config.addAllowedMethod("*"); // GET, POST, OPTIONS vb. hepsine izin ver
        
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}