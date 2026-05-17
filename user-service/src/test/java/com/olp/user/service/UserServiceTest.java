package com.olp.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.olp.user.model.UserProfile;
import com.olp.user.repository.UserProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock
  private UserProfileRepository repository;

  @Mock
  private KafkaProducerService kafkaProducerService;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(repository, kafkaProducerService);
  }

  // --- createUser ---

  @Test
  void createUser_setsDefaultRole() {
    UserProfile profile = new UserProfile();
    profile.setEmail("new@test.com");
    when(repository.save(any(UserProfile.class)))
      .thenAnswer(i -> {
        UserProfile p = i.getArgument(0);
        p.setId(1L);
        return p;
      });

    UserProfile saved = userService.createUser(profile);

    assertEquals("STUDENT", saved.getRole());
    verify(kafkaProducerService)
      .sendUserSignupEvent(any(), any(), any(), any());
  }

  @Test
  void createUser_preservesInstructorRole() {
    UserProfile profile = new UserProfile();
    profile.setEmail("prof@test.com");
    profile.setRole("INSTRUCTOR");
    when(repository.save(any(UserProfile.class)))
      .thenAnswer(i -> {
        UserProfile p = i.getArgument(0);
        p.setId(2L);
        return p;
      });

    UserProfile saved = userService.createUser(profile);

    assertEquals("INSTRUCTOR", saved.getRole());
  }

  @Test
  void createUser_initializesVerificationStatusForInstructor() {
    UserProfile profile = new UserProfile();
    profile.setRole("INSTRUCTOR");
    when(repository.save(any(UserProfile.class)))
      .thenAnswer(i -> {
        UserProfile p = i.getArgument(0);
        p.setId(3L);
        return p;
      });

    UserProfile saved = userService.createUser(profile);

    assertEquals("NOT_SUBMITTED", saved.getInstructorVerificationStatus());
  }

  // --- getAllUsers ---

  @Test
  void getAllUsers_returnsList() {
    when(repository.findAll())
      .thenReturn(List.of(new UserProfile(), new UserProfile()));
    List<UserProfile> users = userService.getAllUsers();
    assertEquals(2, users.size());
  }

  // --- getUsersByRole ---

  @Test
  void getUsersByRole_returnsFilteredList() {
    when(repository.findByRoleIgnoreCase("ADMIN"))
      .thenReturn(List.of(new UserProfile()));
    List<UserProfile> users = userService.getUsersByRole("ADMIN");
    assertEquals(1, users.size());
  }

  @Test
  void getUsersByRole_nullRole_returnsAll() {
    when(repository.findAll()).thenReturn(List.of(new UserProfile()));
    List<UserProfile> users = userService.getUsersByRole(null);
    assertEquals(1, users.size());
  }

  @Test
  void getUsersByRole_blankRole_returnsAll() {
    when(repository.findAll()).thenReturn(List.of(new UserProfile()));
    List<UserProfile> users = userService.getUsersByRole("  ");
    assertEquals(1, users.size());
  }

  // --- getUserById ---

  @Test
  void getUserById_existingUser_returnsUser() {
    UserProfile user = new UserProfile();
    user.setId(1L);
    when(repository.findById(1L)).thenReturn(Optional.of(user));

    Optional<UserProfile> result = userService.getUserById(1L);

    assertTrue(result.isPresent());
    assertEquals(1L, result.get().getId());
  }

  @Test
  void getUserById_nonExistingUser_returnsEmpty() {
    when(repository.findById(999L)).thenReturn(Optional.empty());
    assertTrue(userService.getUserById(999L).isEmpty());
  }

  // --- updateUserRole ---

  @Test
  void updateUserRole_success() {
    UserProfile user = new UserProfile();
    user.setId(1L);
    user.setRole("STUDENT");
    when(repository.findById(1L)).thenReturn(Optional.of(user));
    when(repository.save(any(UserProfile.class)))
      .thenAnswer(i -> i.getArgument(0));

    Optional<UserProfile> result = userService.updateUserRole(1L, "INSTRUCTOR");

    assertTrue(result.isPresent());
    assertEquals("INSTRUCTOR", result.get().getRole());
    verify(kafkaProducerService)
      .sendUserRoleUpdatedEvent(any(), any(), any(), eq("INSTRUCTOR"));
  }

  @Test
  void updateUserRole_deactivated_setsSuspendedStatus() {
    UserProfile user = new UserProfile();
    user.setId(1L);
    user.setRole("STUDENT");
    when(repository.findById(1L)).thenReturn(Optional.of(user));
    when(repository.save(any(UserProfile.class)))
      .thenAnswer(i -> i.getArgument(0));

    Optional<UserProfile> result = userService.updateUserRole(
      1L,
      "DEACTIVATED"
    );

    assertTrue(result.isPresent());
    assertEquals("SUSPENDED", result.get().getAccountStatus());
  }

  @Test
  void updateUserRole_fromDeactivatedToStudent_activatesAccount() {
    UserProfile user = new UserProfile();
    user.setId(1L);
    user.setRole("DEACTIVATED");
    user.setAccountStatus("SUSPENDED");
    when(repository.findById(1L)).thenReturn(Optional.of(user));
    when(repository.save(any(UserProfile.class)))
      .thenAnswer(i -> i.getArgument(0));

    Optional<UserProfile> result = userService.updateUserRole(1L, "STUDENT");

    assertTrue(result.isPresent());
    assertEquals("ACTIVE", result.get().getAccountStatus());
  }

  // --- deleteUser ---

  @Test
  void deleteUser_existingUser_returnsTrue() {
    UserProfile user = new UserProfile();
    when(repository.findById(1L)).thenReturn(Optional.of(user));

    assertTrue(userService.deleteUser(1L));
    verify(repository).delete(user);
  }

  @Test
  void deleteUser_nonExistingUser_returnsFalse() {
    when(repository.findById(999L)).thenReturn(Optional.empty());
    assertFalse(userService.deleteUser(999L));
  }

  // --- updateUserProfile ---

  @Test
  void updateUserProfile_doesNotCreateMissingProfile() {
    when(repository.findById(8L)).thenReturn(Optional.empty());
    Optional<UserProfile> saved = userService.updateUserProfile(
      8L,
      new UserProfile()
    );
    assertEquals(Optional.empty(), saved);
  }

  @Test
  void updateUserProfile_updatesFieldsSelectively() {
    UserProfile existing = new UserProfile();
    existing.setId(1L);
    existing.setEmail("old@test.com");
    existing.setRole("STUDENT");

    when(repository.findById(1L)).thenReturn(Optional.of(existing));
    when(repository.save(any(UserProfile.class)))
      .thenAnswer(i -> i.getArgument(0));

    UserProfile patch = new UserProfile();
    patch.setFullName("New Name");
    patch.setBio("New Bio");

    Optional<UserProfile> result = userService.updateUserProfile(1L, patch);

    assertTrue(result.isPresent());
    assertEquals("New Name", result.get().getFullName());
    assertEquals("New Bio", result.get().getBio());
    assertEquals("old@test.com", result.get().getEmail()); // unchanged
  }

  // --- submitInstructorVerification ---

  @Test
  void submitInstructorVerification_requiresSelfOrAdmin() {
    UserProfile instructor = new UserProfile();
    instructor.setId(12L);
    instructor.setRole("INSTRUCTOR");
    when(repository.findById(12L)).thenReturn(Optional.of(instructor));

    ResponseStatusException ex = assertThrows(
      ResponseStatusException.class,
      () ->
        userService.submitInstructorVerification(
          12L,
          "file.pdf",
          "file.pdf",
          99L,
          "INSTRUCTOR"
        )
    );
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
  }

  @Test
  void submitInstructorVerification_storesPendingRequest() {
    UserProfile instructor = new UserProfile();
    instructor.setId(12L);
    instructor.setRole("INSTRUCTOR");
    when(repository.findById(12L)).thenReturn(Optional.of(instructor));
    when(repository.save(any(UserProfile.class)))
      .thenAnswer(i -> i.getArgument(0));

    UserProfile saved = userService.submitInstructorVerification(
      12L,
      "verification/file.pdf",
      "file.pdf",
      12L,
      "INSTRUCTOR"
    );

    assertEquals("PENDING", saved.getInstructorVerificationStatus());
    assertEquals(
      "/users/uploads/verification/file.pdf",
      saved.getGovernmentIdFileUrl()
    );
  }

  @Test
  void submitInstructorVerification_rejectsNonInstructor() {
    UserProfile student = new UserProfile();
    student.setId(5L);
    student.setRole("STUDENT");
    when(repository.findById(5L)).thenReturn(Optional.of(student));

    ResponseStatusException ex = assertThrows(
      ResponseStatusException.class,
      () ->
        userService.submitInstructorVerification(
          5L,
          "file.pdf",
          "file.pdf",
          5L,
          "STUDENT"
        )
    );
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  // --- reviewInstructorVerification ---

  @Test
  void reviewInstructorVerification_requiresAdmin() {
    ResponseStatusException ex = assertThrows(
      ResponseStatusException.class,
      () ->
        userService.reviewInstructorVerification(
          12L,
          "APPROVED",
          "ok",
          1L,
          "INSTRUCTOR"
        )
    );
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
  }

  @Test
  void reviewInstructorVerification_approvesRequest() {
    UserProfile instructor = new UserProfile();
    instructor.setId(12L);
    instructor.setRole("INSTRUCTOR");
    instructor.setGovernmentIdFileUrl("/users/uploads/verification/file.pdf");
    when(repository.findById(12L)).thenReturn(Optional.of(instructor));
    when(repository.save(any(UserProfile.class)))
      .thenAnswer(i -> i.getArgument(0));

    UserProfile result = userService.reviewInstructorVerification(
      12L,
      "APPROVED",
      "Good",
      1L,
      "ADMIN"
    );

    assertEquals("APPROVED", result.getInstructorVerificationStatus());
    assertEquals("Good", result.getVerificationComment());
  }

  @Test
  void reviewInstructorVerification_invalidStatus_throwsBadRequest() {
    ResponseStatusException ex = assertThrows(
      ResponseStatusException.class,
      () ->
        userService.reviewInstructorVerification(
          12L,
          "INVALID",
          "note",
          1L,
          "ADMIN"
        )
    );
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  // --- countUsers ---

  @Test
  void countUsers_returnsCount() {
    when(repository.count()).thenReturn(42L);
    assertEquals(42L, userService.countUsers());
  }

  // --- getVerificationProfileForDocument ---

  @Test
  void getVerificationProfileForDocument_noDocument_throwsNotFound() {
    UserProfile instructor = new UserProfile();
    instructor.setId(1L);
    instructor.setRole("INSTRUCTOR");
    instructor.setGovernmentIdFileUrl(null);
    when(repository.findById(1L)).thenReturn(Optional.of(instructor));

    ResponseStatusException ex = assertThrows(
      ResponseStatusException.class,
      () -> userService.getVerificationProfileForDocument(1L, 1L, "INSTRUCTOR")
    );
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  // --- getPendingInstructorVerifications ---

  @Test
  void getPendingInstructorVerifications_returnsList() {
    when(
      repository.findByRoleIgnoreCaseAndInstructorVerificationStatusIgnoreCase(
        "INSTRUCTOR",
        "PENDING"
      )
    )
      .thenReturn(List.of(new UserProfile()));
    assertEquals(1, userService.getPendingInstructorVerifications().size());
  }
}
