package com.barbearia.api.dto;
import jakarta.validation.constraints.*;
public record Codigo2faRequest(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Pattern(regexp = "[0-9]{6}") String codigo) {}
