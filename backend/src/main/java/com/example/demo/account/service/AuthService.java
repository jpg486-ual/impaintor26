package com.example.demo.account.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.account.dto.AuthDtos;
import com.example.demo.account.model.AppUser;
import com.example.demo.account.model.RefreshToken;
import com.example.demo.account.model.UserRankProfile;
import com.example.demo.account.model.UserStatus;
import com.example.demo.account.repository.AppUserRepository;
import com.example.demo.account.repository.RefreshTokenRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthService {

    public static final String SESSION_COOKIE = "IMPAINTOR_SESSION";
    private static final long SESSION_TTL_SECONDS = 60L * 60L * 24L * 30L;

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RatingService ratingService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
            AppUserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            RatingService ratingService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.ratingService = ratingService;
    }

    @Transactional
    public AuthResult register(AuthDtos.RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario ya existe");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        return issueSessionForUser(user);
    }

    @Transactional
    public AuthResult login(AuthDtos.LoginRequest request) {
        String identifier = request.identifier().trim();
        if (identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credenciales inválidas");
        }

        Optional<AppUser> byEmail = userRepository.findByEmailIgnoreCase(identifier);
        AppUser user = byEmail.or(() -> userRepository.findByUsernameIgnoreCase(identifier))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario inactivo");
        }

        return issueSessionForUser(user);
    }

    @Transactional
    public AuthDtos.SessionResponse getCurrentSession() {
        AppUser user = requireAuthenticatedUser();
        UserRankProfile profile = ratingService.getOrCreateProfile(user.getId());
        return toSessionResponse(user, profile);
    }

    @Transactional
    public void logoutCurrentSession() {
        RefreshToken token = resolveCurrentRefreshToken();
        if (token != null && token.getRevokedAt() == null) {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        }
    }

    @Transactional(readOnly = true)
    public long requireAuthenticatedUserId() {
        return requireAuthenticatedUser().getId();
    }

    @Transactional(readOnly = true)
    public AppUser requireAuthenticatedUser() {
        RefreshToken token = resolveCurrentRefreshToken();
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión");
        }

        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión expirada");
        }

        AppUser user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario inactivo");
        }
        return user;
    }

    public long sessionTtlSeconds() {
        return SESSION_TTL_SECONDS;
    }

    private AuthResult issueSessionForUser(AppUser user) {
        refreshTokenRepository.deleteByUserIdAndExpiresAtBefore(user.getId(), Instant.now());

        UserRankProfile profile = ratingService.getOrCreateProfile(user.getId());
        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setExpiresAt(Instant.now().plusSeconds(SESSION_TTL_SECONDS));
        refreshTokenRepository.save(refreshToken);

        return new AuthResult(toSessionResponse(user, profile), rawToken);
    }

    private AuthDtos.SessionResponse toSessionResponse(AppUser user, UserRankProfile profile) {
        return new AuthDtos.SessionResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                profile.getElo(),
                profile.getRankedGamesPlayed(),
                profile.getProvisionalMatchesRemaining());
    }

    private RefreshToken resolveCurrentRefreshToken() {
        HttpServletRequest request = currentRequest();
        if (request == null || request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if (!SESSION_COOKIE.equals(cookie.getName())) {
                continue;
            }
            String rawToken = cookie.getValue();
            if (rawToken == null || rawToken.isBlank()) {
                return null;
            }
            return refreshTokenRepository.findByTokenHash(hashToken(rawToken)).orElse(null);
        }

        return null;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.length() < 2 || normalized.length() > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de usuario inválido");
        }
        return normalized;
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email inválido");
        }
        return normalized;
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public record AuthResult(AuthDtos.SessionResponse session, String rawToken) {
    }
}
