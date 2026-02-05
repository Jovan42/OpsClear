package com.opsclear.service;

import com.opsclear.entity.User;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserSyncService userSyncService;

    @BeforeEach
    void setUp() {
        userSyncService = new UserSyncService(userRepository);
    }

    @Test
    @DisplayName("Should create new user when user does not exist")
    void syncFromJwt_createsNewUser_whenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        String email = "newuser@example.com";
        String name = "New User";

        Jwt jwt = createJwt(userId, email, name);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userSyncService.syncFromJwt(jwt);

        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getName()).isEqualTo(name);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getId()).isEqualTo(userId);
        assertThat(savedUser.getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("Should update existing user when user exists")
    void syncFromJwt_updatesExistingUser_whenUserExists() {
        UUID userId = UUID.randomUUID();
        String oldEmail = "old@example.com";
        String newEmail = "new@example.com";
        String newName = "Updated Name";

        User existingUser = User.builder()
                .id(userId)
                .email(oldEmail)
                .name("Old Name")
                .build();

        Jwt jwt = createJwt(userId, newEmail, newName);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userSyncService.syncFromJwt(jwt);

        assertThat(result.getEmail()).isEqualTo(newEmail);
        assertThat(result.getName()).isEqualTo(newName);
        assertThat(result.getLastLoginAt()).isNotNull();
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("Should extract name from given_name and family_name when name claim is missing")
    void syncFromJwt_extractsNameFromGivenAndFamilyName() {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";
        String givenName = "John";
        String familyName = "Doe";

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("email", email)
                .claim("given_name", givenName)
                .claim("family_name", familyName)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userSyncService.syncFromJwt(jwt);

        assertThat(result.getName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("Should fallback to preferred_username when name claims are missing")
    void syncFromJwt_fallsBackToPreferredUsername() {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";
        String preferredUsername = "johndoe";

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("email", email)
                .claim("preferred_username", preferredUsername)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userSyncService.syncFromJwt(jwt);

        assertThat(result.getName()).isEqualTo(preferredUsername);
    }

    @Test
    @DisplayName("Should fallback to email when all name claims are missing")
    void syncFromJwt_fallsBackToEmail() {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userSyncService.syncFromJwt(jwt);

        assertThat(result.getName()).isEqualTo(email);
    }

    private Jwt createJwt(UUID userId, String email, String name) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("email", email)
                .claim("name", name)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
