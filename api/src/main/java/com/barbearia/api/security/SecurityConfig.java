package com.barbearia.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
        @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:http://localhost:8080}")
        private String allowedOrigins;


        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthFilter)
                        throws Exception {
                http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())

                .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .xssProtection(xss -> xss.headerValue(
                                org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://unpkg.com; "
                        +
                        "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://unpkg.com; "
                        +
                        "img-src 'self' data: blob: https://*.tile.openstreetmap.org https://unpkg.com; "
                        +
                        "font-src 'self' https://cdn.jsdelivr.net data:; "
                        +
                        "connect-src 'self' http://localhost:8080 http://127.0.0.1:8080 https://viacep.com.br https://nominatim.openstreetmap.org;")))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> res.sendError(401))
                        .accessDeniedHandler((req, res, ex) -> res.sendError(403)))
                .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/*.html", "/scripts/**", "/styles/**",
                                "/assets/**", "/css/**", "/js/**", "/images/**",
                                "/favicon.ico")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/usuarios",
                                "/api/usuarios/login", "/api/usuarios/validar-2fa")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/servicos/estabelecimento/**",
                                "/api/usuarios/estabelecimentos/proximos")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/webhooks/abacatepay").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();

                config.setAllowCredentials(false);

                config.setAllowedOrigins(java.util.Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim).filter(origin -> !origin.isEmpty()).toList());

                config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);

                return source;
        }
}