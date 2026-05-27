package com.impaintor.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank
    String token,
    @NotBlank
    @Size(min = 6, max = 128)
    String newPassword
) {
}
