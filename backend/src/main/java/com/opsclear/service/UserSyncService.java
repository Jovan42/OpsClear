package com.opsclear.service;

import com.opsclear.model.UserModel;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSyncService {

    private final UserRepository userRepository;

    @Transactional
    public UserModel syncFromJwt(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        String name = extractName(jwt);

        return userRepository.findById(userId)
                .map(existingUser -> updateExistingUser(existingUser, email, name))
                .orElseGet(() -> createNewUser(userId, email, name));
    }

    private UserModel updateExistingUser(UserModel user, String email, String name) {
        user.setEmail(email);
        user.setName(name);
        log.debug("Updated user: {}", user.getEmail());
        return userRepository.save(user);
    }

    private UserModel createNewUser(UUID userId, String email, String name) {
        UserModel user = UserModel.builder()
                .id(userId)
                .email(email)
                .name(name)
                .build();
        log.info("Created new user: {}", email);
        return userRepository.save(user);
    }

    private String extractName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }

        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");

        if (givenName != null && familyName != null) {
            return givenName + " " + familyName;
        }

        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null) {
            return preferredUsername;
        }

        return jwt.getClaimAsString("email");
    }
}
