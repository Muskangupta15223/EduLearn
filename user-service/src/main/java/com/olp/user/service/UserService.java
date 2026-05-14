package com.olp.user.service;

import com.olp.user.model.UserProfile;
import com.olp.user.repository.UserProfileRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserProfileRepository repository;
    private final KafkaProducerService kafkaProducerService;

    public UserService(UserProfileRepository repository, KafkaProducerService kafkaProducerService) {
        this.repository = repository;
        this.kafkaProducerService = kafkaProducerService;
    }

    public UserProfile createUser(UserProfile profile) {
        if (profile.getRole() == null) {
            profile.setRole("STUDENT");
        }
        initializeVerificationState(profile);
        UserProfile saved = repository.save(profile);
        kafkaProducerService.sendUserSignupEvent(saved.getId(), saved.getEmail(), saved.getFullName(), saved.getAvatarUrl());
        return saved;
    }

    public List<UserProfile> getAllUsers() {
        return repository.findAll();
    }

    public List<UserProfile> getUsersByRole(String role) {
        if (role == null || role.isBlank()) {
            return repository.findAll();
        }
        return repository.findByRoleIgnoreCase(role.trim());
    }

    public List<UserProfile> getPendingInstructorVerifications() {
        return repository.findByRoleIgnoreCaseAndInstructorVerificationStatusIgnoreCase("INSTRUCTOR", "PENDING");
    }

    public Optional<UserProfile> getUserById(Long id) {
        return repository.findById(id);
    }

    public Optional<UserProfile> updateUserRole(Long id, String newRole) {
        return repository.findById(id).map(user -> {
            user.setRole(newRole);
            if ("DEACTIVATED".equalsIgnoreCase(newRole)) {
                user.setAccountStatus("SUSPENDED");
            } else if (user.getAccountStatus() == null || user.getAccountStatus().isBlank() || "SUSPENDED".equalsIgnoreCase(user.getAccountStatus())) {
                user.setAccountStatus("ACTIVE");
            }
            UserProfile saved = repository.save(user);
            kafkaProducerService.sendUserRoleUpdatedEvent(saved.getId(), saved.getEmail(), saved.getFullName(), saved.getRole());
            return saved;
        });
    }

    public boolean deleteUser(Long id) {
        return repository.findById(id).map(user -> {
            repository.delete(user);
            return true;
        }).orElse(false);
    }

    public Optional<UserProfile> updateUserProfile(Long id, UserProfile profileDetails) {
        return repository.findById(id)
                .map(user -> saveProfileDetails(user, profileDetails));
    }

    private UserProfile saveProfileDetails(UserProfile user, UserProfile profileDetails) {
        if (profileDetails.getEmail() != null) user.setEmail(profileDetails.getEmail());
        if (profileDetails.getRole() != null) user.setRole(profileDetails.getRole());
        if (profileDetails.getFullName() != null) user.setFullName(profileDetails.getFullName());
        if (profileDetails.getBio() != null) user.setBio(profileDetails.getBio());
        if (profileDetails.getAvatarUrl() != null) user.setAvatarUrl(profileDetails.getAvatarUrl());
        if (profileDetails.getExpertiseAreas() != null) user.setExpertiseAreas(profileDetails.getExpertiseAreas());
        if (profileDetails.getMobile() != null) user.setMobile(profileDetails.getMobile());
        if (profileDetails.getAccountStatus() != null) user.setAccountStatus(profileDetails.getAccountStatus());
        if (user.getRole() == null || user.getRole().isBlank()) user.setRole("STUDENT");
        initializeVerificationState(user);
        UserProfile saved = repository.save(user);
        kafkaProducerService.sendUserProfileUpdatedEvent(saved);
        return saved;
    }

    public UserProfile submitInstructorVerification(Long id, String storedFileUrl, String originalFileName, Long actorUserId, String actorRole) {
        return repository.findById(id)
                .map(user -> {
                    requireSelfOrAdmin(id, actorUserId, actorRole);
                    requireInstructor(user);
                    user.setGovernmentIdFileUrl("/users/uploads/" + storedFileUrl);
                    user.setGovernmentIdFileName(originalFileName);
                    user.setInstructorVerificationStatus("PENDING");
                    user.setVerificationComment("Verification submitted and awaiting admin review");
                    user.setVerificationReviewedAt(null);
                    user.setVerificationReviewedBy(null);
                    user.setVerificationSubmittedAt(LocalDateTime.now());
                    return repository.save(user);
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public UserProfile reviewInstructorVerification(Long id, String status, String comment, Long reviewerId, String reviewerRole) {
        if (!"ADMIN".equalsIgnoreCase(reviewerRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }

        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if (!"APPROVED".equals(normalizedStatus) && !"REJECTED".equals(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be APPROVED or REJECTED");
        }

        return repository.findById(id)
                .map(user -> {
                    requireInstructor(user);
                    if (user.getGovernmentIdFileUrl() == null || user.getGovernmentIdFileUrl().isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No verification document uploaded");
                    }
                    user.setInstructorVerificationStatus(normalizedStatus);
                    user.setVerificationComment(
                            comment == null || comment.isBlank()
                                    ? ("APPROVED".equals(normalizedStatus) ? "Instructor verified by admin" : "Instructor verification rejected")
                                    : comment.trim()
                    );
                    user.setVerificationReviewedBy(reviewerId);
                    user.setVerificationReviewedAt(LocalDateTime.now());
                    return repository.save(user);
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public UserProfile getVerificationProfileForDocument(Long id, Long actorUserId, String actorRole) {
        UserProfile user = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        requireSelfOrAdmin(id, actorUserId, actorRole);
        requireInstructor(user);
        if (user.getGovernmentIdFileUrl() == null || user.getGovernmentIdFileUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification document not found");
        }
        return user;
    }

    public long countUsers() {
        return repository.count();
    }

    private void initializeVerificationState(UserProfile profile) {
        if ("INSTRUCTOR".equalsIgnoreCase(profile.getRole())) {
            if (profile.getInstructorVerificationStatus() == null) {
                profile.setInstructorVerificationStatus("NOT_SUBMITTED");
            }
        } else {
            profile.setInstructorVerificationStatus("NOT_REQUIRED");
        }
        if (profile.getAccountStatus() == null || profile.getAccountStatus().isBlank()) {
            profile.setAccountStatus("ACTIVE");
        }
    }

    private void requireSelfOrAdmin(Long targetUserId, Long actorUserId, String actorRole) {
        if ("ADMIN".equalsIgnoreCase(actorRole)) {
            return;
        }
        if (actorUserId == null || !targetUserId.equals(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own verification");
        }
    }

    private void requireInstructor(UserProfile user) {
        if (!"INSTRUCTOR".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification is only required for instructors");
        }
        if (user.getInstructorVerificationStatus() == null || user.getInstructorVerificationStatus().isBlank()) {
            user.setInstructorVerificationStatus("NOT_SUBMITTED");
        }
    }
}
