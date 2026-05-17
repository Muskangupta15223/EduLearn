package com.olp.user.dto;

// import com.olp.user.model.UserProfile;

public class UserDtos {

  public record UserRoleUpdateRequest(String role) {}

  public record UserStatusUpdateRequest(String status) {}

  public record VerificationReviewRequest(String status, String comment) {}
}
