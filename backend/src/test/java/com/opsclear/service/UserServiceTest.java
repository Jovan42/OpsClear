package com.opsclear.service;

import com.opsclear.model.UserModel;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    @DisplayName("searchByEmail_shouldReturnResults_fromRepository")
    void searchByEmail_shouldReturnResults_fromRepository() {
        String prefix = "ali";
        List<UserModel> expected = List.of(
                UserModel.builder().id(UUID.randomUUID()).email("alice@example.com").name("Alice").build(),
                UserModel.builder().id(UUID.randomUUID()).email("alicia@example.com").name("Alicia").build()
        );
        when(userRepository.searchByEmail(prefix, 10)).thenReturn(expected);

        List<UserModel> result = userService.searchByEmail(prefix);

        assertThat(result).isEqualTo(expected);
        verify(userRepository).searchByEmail(prefix, 10);
    }

    @Test
    @DisplayName("searchByEmail_shouldReturnEmptyList_whenNoMatches")
    void searchByEmail_shouldReturnEmptyList_whenNoMatches() {
        when(userRepository.searchByEmail("zzz", 10)).thenReturn(List.of());

        List<UserModel> result = userService.searchByEmail("zzz");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("searchByEmail_shouldEnforceLimit_ofTen")
    void searchByEmail_shouldEnforceLimit_ofTen() {
        userService.searchByEmail("test");

        verify(userRepository).searchByEmail("test", 10);
    }
}
