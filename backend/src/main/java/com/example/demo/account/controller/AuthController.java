package com.example.demo.account.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.account.dto.AuthDtos;
import com.example.demo.account.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.SessionResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request,
            HttpServletRequest httpRequest) {
        AuthService.AuthResult result = authService.register(request);
        return withSessionCookie(ResponseEntity.ok(), result.rawToken(), httpRequest)
                .body(result.session());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.SessionResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthService.AuthResult result = authService.login(request);
        return withSessionCookie(ResponseEntity.ok(), result.rawToken(), httpRequest)
                .body(result.session());
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDtos.SessionResponse> me() {
        return ResponseEntity.ok(authService.getCurrentSession());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        authService.logoutCurrentSession();
        ResponseCookie cookie = ResponseCookie.from(AuthService.SESSION_COOKIE, "")
            .httpOnly(true)
            .secure(httpRequest.isSecure())
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build();
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .build();
    }

    private <B extends ResponseEntity.BodyBuilder> B withSessionCookie(
            B builder,
            String rawToken,
            HttpServletRequest request) {
        ResponseCookie cookie = ResponseCookie.from(AuthService.SESSION_COOKIE, rawToken)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(authService.sessionTtlSeconds())
                .build();
        builder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        return builder;
    }

}
