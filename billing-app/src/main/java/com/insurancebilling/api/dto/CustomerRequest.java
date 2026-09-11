package com.insurancebilling.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for creating a customer.
 *
 * <p>Validation here is structural only: presence, length and email shape. Anything that is a billing
 * rule rather than a malformed request is enforced in the domain and reported as 422.
 */
public record CustomerRequest(
    @NotBlank(message = "firstName is required")
        @Size(max = 60, message = "firstName must be at most 60 characters")
        String firstName,
    @NotBlank(message = "lastName is required")
        @Size(max = 60, message = "lastName must be at most 60 characters")
        String lastName,
    @NotBlank(message = "email is required") @Email(message = "email must be a valid address")
        String email) {}
