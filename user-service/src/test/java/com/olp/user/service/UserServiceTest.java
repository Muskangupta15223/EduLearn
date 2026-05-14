package com.olp.user.service;

import com.olp.user.model.UserProfile;
import com.olp.user.repository.UserProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserProfileRepository repository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @Test
    void updateUserProfileDoesNotCreateMissingProfile() {
        UserService userService = new UserService(repository, kafkaProducerService);
        UserProfile details = new UserProfile();
        details.setEmail("manavgupta12@gmail.com");
        details.setRole("STUDENT");
        details.setFullName("manav gupta");
        details.setBio("Learning Java");
        details.setAvatarUrl("https://example.com/avatar.jpg");

        when(repository.findById(8L)).thenReturn(Optional.empty());

        Optional<UserProfile> saved = userService.updateUserProfile(8L, details);

        assertEquals(Optional.empty(), saved);
    }

    @Test
    void submitInstructorVerificationRequiresSelfOrAdmin() {
        UserService userService = new UserService(repository, kafkaProducerService);
        UserProfile instructor = new UserProfile();
        instructor.setId(12L);
        instructor.setRole("INSTRUCTOR");

        when(repository.findById(12L)).thenReturn(Optional.of(instructor));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                userService.submitInstructorVerification(12L, "verification/file.pdf", "file.pdf", 99L, "INSTRUCTOR"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void submitInstructorVerificationStoresPendingRequest() {
        UserService userService = new UserService(repository, kafkaProducerService);
        UserProfile instructor = new UserProfile();
        instructor.setId(12L);
        instructor.setRole("INSTRUCTOR");

        when(repository.findById(12L)).thenReturn(Optional.of(instructor));
        when(repository.save(org.mockito.ArgumentMatchers.any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserProfile saved = userService.submitInstructorVerification(12L, "verification/file.pdf", "file.pdf", 12L, "INSTRUCTOR");

        assertEquals("PENDING", saved.getInstructorVerificationStatus());
        assertEquals("/users/uploads/verification/file.pdf", saved.getGovernmentIdFileUrl());
        assertEquals("file.pdf", saved.getGovernmentIdFileName());
    }

    @Test
    void reviewInstructorVerificationRequiresAdmin() {
        UserService userService = new UserService(repository, kafkaProducerService);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                userService.reviewInstructorVerification(12L, "APPROVED", "Looks good", 1L, "INSTRUCTOR"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void reviewInstructorVerificationApprovesRequest() {
        UserService userService = new UserService(repository, kafkaProducerService);
        UserProfile instructor = new UserProfile();
        instructor.setId(12L);
        instructor.setRole("INSTRUCTOR");
        instructor.setGovernmentIdFileUrl("/users/uploads/verification/file.pdf");

        when(repository.findById(12L)).thenReturn(Optional.of(instructor));
        when(repository.save(org.mockito.ArgumentMatchers.any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserProfile saved = userService.reviewInstructorVerification(12L, "APPROVED", "Looks good", 1L, "ADMIN");

        assertEquals("APPROVED", saved.getInstructorVerificationStatus());
        assertEquals("Looks good", saved.getVerificationComment());
        assertEquals(1L, saved.getVerificationReviewedBy());
    }
}
