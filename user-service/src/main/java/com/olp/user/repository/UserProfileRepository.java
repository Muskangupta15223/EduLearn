package com.olp.user.repository;

import com.olp.user.model.UserProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository
  extends JpaRepository<UserProfile, Long> {

  List<UserProfile> findByRoleIgnoreCase(String role);

  List<UserProfile> findByRoleIgnoreCaseAndInstructorVerificationStatusIgnoreCase(String role, String instructorVerificationStatus);
}
