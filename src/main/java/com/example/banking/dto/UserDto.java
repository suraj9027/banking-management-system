package com.example.banking.dto;

public record UserDto(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String role
) {
}
