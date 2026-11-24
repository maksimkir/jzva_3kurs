package com.example.library.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Перевіряє мапінг ролей Keycloak (claim "roles") -> Spring authorities (ROLE_*).
 * Фіксовані Instant — тест детермінований.
 */
class SecurityConfigTest {

    private static final Instant ISSUED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-01-01T01:00:00Z");

    private final SecurityConfig config = new SecurityConfig();

    private static Set<String> names(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static OidcIdToken idToken(Map<String, Object> extraClaims) {
        Map<String, Object> claims = new java.util.HashMap<>(extraClaims);
        claims.put("sub", "user-1");
        return new OidcIdToken("token-value", ISSUED, EXPIRES, claims);
    }

    // ------------------------------------------------------------ JWT converter

    @Test
    void jwtConverter_tokenWithRoles_mapsToRolePrefixedAuthoritiesAndUsesPreferredUsername() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .claim("preferred_username", "admin")
                .claim("roles", List.of("ADMIN", "USER"))
                .build();

        // Act
        AbstractAuthenticationToken auth = config.jwtAuthenticationConverter().convert(jwt);

        // Assert
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("admin");
        assertThat(names(auth.getAuthorities())).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void jwtConverter_tokenWithoutRolesClaim_hasNoAuthorities() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .claim("preferred_username", "guest")
                .build();

        // Act
        AbstractAuthenticationToken auth = config.jwtAuthenticationConverter().convert(jwt);

        // Assert
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void jwtConverter_rolesClaimIsNotACollection_isIgnored() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .claim("preferred_username", "weird")
                .claim("roles", "ADMIN")
                .build();

        // Act
        AbstractAuthenticationToken auth = config.jwtAuthenticationConverter().convert(jwt);

        // Assert
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).isEmpty();
    }

    // ------------------------------------------------------------ OIDC mapper

    @Test
    void oidcMapper_idTokenWithRoles_addsRolesAndKeepsOriginalAuthorities() {
        // Arrange
        GrantedAuthoritiesMapper mapper = config.oidcAuthoritiesMapper();
        OidcUserAuthority oidc = new OidcUserAuthority(
                idToken(Map.of("roles", List.of("ADMIN", "USER"))), null);
        SimpleGrantedAuthority scope = new SimpleGrantedAuthority("SCOPE_openid");

        // Act
        Collection<? extends GrantedAuthority> result = mapper.mapAuthorities(List.of(oidc, scope));

        // Assert
        assertThat(names(result)).containsExactlyInAnyOrder("OIDC_USER", "SCOPE_openid", "ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void oidcMapper_rolesInUserInfoOnly_areAdded() {
        // Arrange
        GrantedAuthoritiesMapper mapper = config.oidcAuthoritiesMapper();
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("sub", "user-1", "roles", List.of("USER")));
        OidcUserAuthority oidc = new OidcUserAuthority(idToken(Map.of()), userInfo);

        // Act
        Collection<? extends GrantedAuthority> result = mapper.mapAuthorities(List.of(oidc));

        // Assert
        assertThat(names(result)).contains("ROLE_USER");
    }

    @Test
    void oidcMapper_sameRoleInIdTokenAndUserInfo_isNotDuplicated() {
        // Arrange
        GrantedAuthoritiesMapper mapper = config.oidcAuthoritiesMapper();
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("sub", "user-1", "roles", List.of("USER")));
        OidcUserAuthority oidc = new OidcUserAuthority(idToken(Map.of("roles", List.of("USER"))), userInfo);

        // Act
        Collection<? extends GrantedAuthority> result = mapper.mapAuthorities(List.of(oidc));

        // Assert
        long roleUserCount = result.stream().filter(a -> a.getAuthority().equals("ROLE_USER")).count();
        assertThat(roleUserCount).isEqualTo(1);
    }

    @Test
    void oidcMapper_noRolesClaim_addsNoRoleAuthorities() {
        // Arrange
        GrantedAuthoritiesMapper mapper = config.oidcAuthoritiesMapper();
        OidcUserAuthority oidc = new OidcUserAuthority(idToken(Map.of()), null);

        // Act
        Collection<? extends GrantedAuthority> result = mapper.mapAuthorities(List.of(oidc));

        // Assert
        assertThat(names(result)).containsExactly("OIDC_USER");
    }

    @Test
    void oidcMapper_onlyPlainAuthorities_returnsThemUnchanged() {
        // Arrange
        GrantedAuthoritiesMapper mapper = config.oidcAuthoritiesMapper();

        // Act
        Collection<? extends GrantedAuthority> result =
                mapper.mapAuthorities(List.of(new SimpleGrantedAuthority("SCOPE_email")));

        // Assert
        assertThat(names(result)).containsExactly("SCOPE_email");
    }
}
