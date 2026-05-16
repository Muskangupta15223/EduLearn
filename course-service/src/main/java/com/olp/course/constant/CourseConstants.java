package com.olp.course.constant;

public final class CourseConstants {
    private CourseConstants() {} // prevent instantiation

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_INSTRUCTOR = "INSTRUCTOR";
    public static final String ROLE_STUDENT = "STUDENT";

    public static final String MSG_MISSING_USER_ID = "Missing user id";
    public static final String MSG_UNAUTHORIZED = "Unauthorized access";
    public static final String MSG_MUST_BE_ENROLLED_TO_POST = "You must be enrolled to post discussions";
    public static final String MSG_MUST_BE_ENROLLED_TO_VIEW = "You must be enrolled to view discussions";
    public static final String MSG_MUST_BE_ENROLLED_TO_REPLY = "You must be enrolled to reply to discussions";
    public static final String MSG_MUST_BE_ENROLLED_TO_VOTE = "You must be enrolled to vote on discussions";
    
    public static final String KEY_ERROR = "error";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
}
