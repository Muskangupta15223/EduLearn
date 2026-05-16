package com.olp.auth.constant;

public final class AuthConstants {
    private AuthConstants() {} // prevent instantiation

    public static final String KEY_EMAIL = "email";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_ERROR = "error";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_MESSAGE = "message";
    
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_INSTRUCTOR = "INSTRUCTOR";
    public static final String ROLE_DEACTIVATED = "DEACTIVATED";
    
    public static final String MSG_INVALID_CREDENTIALS = "Invalid credentials";
    public static final String MSG_USER_NOT_FOUND = "User not found";
    public static final String MSG_ACCOUNT_DEACTIVATED = "Your account has been deactivated. Contact support.";
    public static final String MSG_ACCOUNT_DEACTIVATED_SHORT = "Your account has been deactivated.";
    public static final String MSG_MISSING_AUTH_USER = "Missing authenticated user";
    
    public static final String EVENT_USER_SIGNUP = "USER_SIGNUP";
    public static final String EVENT_USER_LOGIN = "USER_LOGIN";
    public static final String EVENT_USER_AVATAR_UPDATED = "USER_AVATAR_UPDATED";
    public static final String EVENT_USER_PROFILE_UPDATED = "USER_PROFILE_UPDATED";
    public static final String EVENT_PASSWORD_RESET = "PASSWORD_RESET";
    
    public static final String PROVIDER_LOCAL = "LOCAL";
    public static final String PROVIDER_GOOGLE = "GOOGLE";
}
