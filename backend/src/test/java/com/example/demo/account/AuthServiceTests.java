package com.example.demo.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.account.dto.AuthDtos;
import com.example.demo.account.repository.AppUserRepository;
import com.example.demo.account.service.AuthService;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@Transactional
class AuthServiceTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private AppUserRepository userRepository;

    @Test
    void register_createsUserAndIssuesSession() {
        AuthService.AuthResult result = authService
                .register(new AuthDtos.RegisterRequest("ranked-user", "ranked-user@example.com", "secret123"));

        assertThat(result.rawToken()).isNotBlank();
        assertThat(result.session().userId()).isPositive();
        assertThat(result.session().username()).isEqualTo("ranked-user");
        assertThat(result.session().elo()).isEqualTo(com.example.demo.account.model.UserRankProfile.DEFAULT_ELO);
        assertThat(userRepository.findById(result.session().userId())).hasValueSatisfying(user -> {
            assertThat(user.getPasswordHash()).isNotEqualTo("secret123");
            assertThat(user.getEmail()).isEqualTo("ranked-user@example.com");
        });
    }

    @Test
    void login_allowsEmailOrUsernameIdentifier() {
        authService.register(new AuthDtos.RegisterRequest("ranked-login", "ranked-login@example.com", "secret123"));

        AuthService.AuthResult byEmail = authService
                .login(new AuthDtos.LoginRequest("ranked-login@example.com", "secret123"));
        AuthService.AuthResult byUsername = authService
                .login(new AuthDtos.LoginRequest("ranked-login", "secret123"));

        assertThat(byEmail.session().userId()).isEqualTo(byUsername.session().userId());
    }

    @Test
    void requireAuthenticatedUserId_readsSessionCookie() {
        AuthService.AuthResult result = authService
                .register(new AuthDtos.RegisterRequest("ranked-session", "ranked-session@example.com", "secret123"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthService.SESSION_COOKIE, result.rawToken()));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            long userId = authService.requireAuthenticatedUserId();
            assertThat(userId).isEqualTo(result.session().userId());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void requireAuthenticatedUserId_failsWithoutSessionCookie() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        try {
            assertThatThrownBy(() -> authService.requireAuthenticatedUserId())
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Debes iniciar sesión");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
