package unimagdeportes.dto.contact;

import jakarta.validation.constraints.NotBlank;

public record ContactMessage(@NotBlank String subject, @NotBlank String message) { }
