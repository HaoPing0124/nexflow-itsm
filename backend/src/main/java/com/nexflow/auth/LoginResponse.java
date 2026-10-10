package com.nexflow.auth;

public record LoginResponse(String token, String refreshToken, UserDto user) {
}
