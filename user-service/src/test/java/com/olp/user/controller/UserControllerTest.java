package com.olp.user.controller;

import com.olp.user.model.UserProfile;
import com.olp.user.service.FileStorageService;
import com.olp.user.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private FileStorageService fileStorageService;

    @Test
    void updateProfileRejectsDifferentNonAdminUser() {
        UserController controller = new UserController(userService, fileStorageService);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.updateProfile(9L, new UserProfile(), 7L, "STUDENT")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void updateProfileAllowsOwnerAndReturnsUpdatedProfile() {
        UserController controller = new UserController(userService, fileStorageService);
        UserProfile updated = new UserProfile();
        updated.setId(9L);
        updated.setFullName("Asha");

        when(userService.updateUserProfile(9L, updated)).thenReturn(Optional.of(updated));

        UserProfile body = controller.updateProfile(9L, updated, 9L, "STUDENT").getBody();

        assertEquals("Asha", body.getFullName());
    }

    @Test
    void getProfileAllowsAdminAccess() {
        UserController controller = new UserController(userService, fileStorageService);
        UserProfile profile = new UserProfile();
        profile.setId(12L);

        when(userService.getUserById(12L)).thenReturn(Optional.of(profile));

        UserProfile body = controller.getProfile(12L, 1L, "ADMIN").getBody();

        assertEquals(12L, body.getId());
    }

    @Test
    void uploadAvatarRejectsDifferentNonAdminUser() {
        UserController controller = new UserController(userService, fileStorageService);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1});

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.uploadAvatar(9L, file, 5L, "STUDENT")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void uploadAvatarUpdatesOwnProfile() {
        UserController controller = new UserController(userService, fileStorageService);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1});
        UserProfile updated = new UserProfile();
        updated.setAvatarUrl("/users/uploads/avatars/avatar.png");

        when(fileStorageService.storeAvatar(file)).thenReturn("avatars/avatar.png");
        when(userService.updateUserProfile(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.any(UserProfile.class)))
                .thenReturn(Optional.of(updated));

        UserProfile body = controller.uploadAvatar(9L, file, 9L, "STUDENT").getBody();

        assertEquals("/users/uploads/avatars/avatar.png", body.getAvatarUrl());
        verify(fileStorageService).storeAvatar(file);
    }
}
