package com.example.library.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Дві паралельні SecurityFilterChain:
 *  1) /api/**  -> JWT (Bearer), stateless, без сесій, 401 при відсутності токена
 *  2) решта    -> OIDC Login через Keycloak, сесія + CSRF, редирект на сторінку логіну IdP
 *
 * @EnableMethodSecurity вмикає @PreAuthorize.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ROLES_CLAIM = "roles";

    // ---------- JWT chain (API) ----------
    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth
                // Acceptance criteria 6: правило на рівні SecurityFilterChain
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    // ---------- OIDC chain (UI) ----------
    @Bean
    @Order(2)
    SecurityFilterChain uiFilterChain(HttpSecurity http,
                                      ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        OidcClientInitiatedLogoutSuccessHandler logoutHandler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutHandler.setPostLogoutRedirectUri("{baseUrl}/");

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(ui -> ui.userAuthoritiesMapper(oidcAuthoritiesMapper())))
            .logout(logout -> logout.logoutSuccessHandler(logoutHandler));
        return http.build();
    }

    // ---------- Роли з Keycloak -> Spring authorities ----------
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> extractRoles(jwt.getClaims()));
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    private GrantedAuthoritiesMapper oidcAuthoritiesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            authorities.forEach(authority -> {
                if (authority instanceof OidcUserAuthority oidc) {
                    mapped.addAll(extractRoles(oidc.getIdToken().getClaims()));
                    if (oidc.getUserInfo() != null) {
                        mapped.addAll(extractRoles(oidc.getUserInfo().getClaims()));
                    }
                }
            });
            return mapped;
        };
    }

    private Collection<GrantedAuthority> extractRoles(Map<String, Object> claims) {
        Object roles = claims.get(ROLES_CLAIM);
        if (roles instanceof Collection<?> collection) {
            return collection.stream()
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();
        }
        return List.of();
    }
}
