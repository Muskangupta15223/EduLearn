package com.olp.auth.dto;

import com.olp.auth.model.AuthUser;
import java.util.Date;

public class AuthDtos {
    public record RegisterRequest(String email, String name, String password, String role) {}
    public record LoginRequest(String email, String password) {}
    public record UserSummary(Long id, String email, String name, String role, String provider, String avatarUrl, String mobile, String bio, java.time.LocalDateTime createdAt) {
        public static UserSummary from(AuthUser user) {
            return new UserSummary(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    user.getRole(),
                    user.getProvider(),
                    user.getAvatarUrl(),
                    user.getMobile(),
                    user.getBio(),
                    user.getCreatedAt()
            );
        }
    }
    public record AuthResponse(String token, UserSummary user) {
        public static AuthResponse from(String token, AuthUser user) {
            return new AuthResponse(token, UserSummary.from(user));
        }
    }
    public record OAuthLoginSuccessResponse(String message, String token, UserSummary user) {
        public static OAuthLoginSuccessResponse from(String message, String token, AuthUser user) {
            return new OAuthLoginSuccessResponse(message, token, UserSummary.from(user));
        }
    }
    public record ErrorResponse(String error) {}
    public record MessageResponse(String message) {}
    public record ForgotPasswordRequest(String email) {}
    public record ResetPasswordRequest(String token, String password) {}
    public record TokenRequest(String token) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
    public record ProfileUpdateRequest(String name, String avatarUrl, String mobile, String bio) {}
    public record ProfileResponse(Long id, String email, String name, String role, String provider, String avatarUrl, String mobile, String bio, java.time.LocalDateTime createdAt) {
        public static ProfileResponse from(AuthUser user) {
            return new ProfileResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getProvider(), user.getAvatarUrl(), user.getMobile(), user.getBio(), user.getCreatedAt());
        }
    }
    public record TokenValidationResponse(boolean valid, String email, String role, Object userId, String name, Date expiresAt) {}
}
