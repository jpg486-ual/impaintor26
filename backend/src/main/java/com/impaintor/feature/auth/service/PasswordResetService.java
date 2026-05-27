package com.impaintor.feature.auth.service;

import com.impaintor.feature.user.models.User;
import com.impaintor.feature.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${impaintor.mail.from:}")
    private String mailFrom;

    @Value("${impaintor.reset.base-url:http://localhost:4200/reset-password}")
    private String resetBaseUrl;

    @Value("${impaintor.reset.token-ttl-minutes:30}")
    private long tokenTtlMinutes;

    public void requestPasswordReset(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);
        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();
        String token = generateToken();
        String tokenHash = hashToken(token);

        user.setPasswordResetTokenHash(tokenHash);
        user.setPasswordResetTokenExpiresAt(LocalDateTime.now().plusMinutes(tokenTtlMinutes));
        userRepository.save(user);

        sendResetEmail(user, token);
    }

    public void resetPassword(String token, String newPassword) {
        String tokenHash = hashToken(token);
        User user = userRepository.findByPasswordResetTokenHash(tokenHash)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        LocalDateTime expiresAt = user.getPasswordResetTokenExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetTokenExpiresAt(null);
        userRepository.save(user);
    }

    private void sendResetEmail(User user, String token) {
        String resetLink = resetBaseUrl + "?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        if (mailFrom != null && !mailFrom.isBlank()) {
            message.setFrom(mailFrom);
        }
        message.setSubject("Impaintor - Restablecer tu contrasena");
        message.setText(
            "Hola " + user.getUsername() + ",\n\n"
                + "Recibimos una solicitud para restablecer tu contrasena.\n"
                + "Abre este enlace para crear una nueva contrasena:\n"
                + resetLink + "\n\n"
                + "Si no solicitaste este cambio, puedes ignorar este correo.\n"
        );

        try {
            mailSender.send(message);
        } catch (RuntimeException ex) {
            logger.error("Failed to send reset email to {}", user.getEmail(), ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email service unavailable");
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
