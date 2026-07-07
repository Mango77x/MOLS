package com.mls.logistics.integration;

import com.mls.logistics.security.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests of the HttpOnly auth-cookie flow for browser clients:
 * login must set a hardened cookie, the cookie alone must authenticate API
 * requests (no Authorization header), bad cookies must yield 401, and logout
 * must clear the cookie. Header-based auth is untouched (covered elsewhere).
 */
class CookieAuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void login_SetsHardenedCookie_ThatAuthenticatesAndClearsOnLogout() {
        createUser("cookie-user", Role.ADMIN);

        // --- Login sets the cookie with the hardening attributes ---
        var login = restTemplate.postForEntity("/api/auth/login",
                jsonEntity("{\"username\":\"cookie-user\",\"password\":\"" + TEST_PASSWORD + "\"}", null),
                String.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);

        String setCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull()
                .contains("MOLS_AUTH=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api");

        // --- The cookie alone (no Authorization header) authenticates ---
        String cookiePair = setCookie.split(";", 2)[0];
        HttpHeaders withCookie = new HttpHeaders();
        withCookie.add(HttpHeaders.COOKIE, cookiePair);
        assertThat(restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                new HttpEntity<>(null, withCookie), String.class)
                .getStatusCode().value()).isEqualTo(200);

        // --- /auth/me restores the session for the SPA on page load ---
        var me = restTemplate.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(null, withCookie), String.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        var meBody = readJson(me.getBody());
        assertThat(meBody.get("username").asText()).isEqualTo("cookie-user");
        assertThat(meBody.get("role").asText()).isEqualTo("ADMIN");

        // --- /auth/me is not available anonymously ---
        assertThat(restTemplate.exchange("/api/auth/me", HttpMethod.GET,
                jsonEntity(null, null), String.class).getStatusCode().value()).isEqualTo(403);

        // --- A tampered cookie is treated as anonymous (403 on protected
        // endpoints) rather than 401: cookies travel implicitly, so an
        // unusable one must never block the user from reaching login again ---
        HttpHeaders badCookie = new HttpHeaders();
        badCookie.add(HttpHeaders.COOKIE, "MOLS_AUTH=not-a-real-jwt");
        assertThat(restTemplate.exchange("/api/warehouses", HttpMethod.GET,
                new HttpEntity<>(null, badCookie), String.class)
                .getStatusCode().value()).isEqualTo(403);

        // --- Login still works while carrying a stale/invalid cookie ---
        HttpHeaders staleCookieLogin = new HttpHeaders();
        staleCookieLogin.add(HttpHeaders.COOKIE, "MOLS_AUTH=not-a-real-jwt");
        staleCookieLogin.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        assertThat(restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"cookie-user\",\"password\":\"" + TEST_PASSWORD + "\"}",
                        staleCookieLogin), String.class)
                .getStatusCode().value()).isEqualTo(200);

        // --- Logout expires the cookie ---
        var logout = restTemplate.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(null, withCookie), String.class);
        assertThat(logout.getStatusCode().value()).isEqualTo(204);
        String cleared = logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cleared).isNotNull()
                .contains("MOLS_AUTH=")
                .contains("Max-Age=0");
    }
}
