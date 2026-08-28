package com.opsclear.service;

import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganisationRepository organisationRepository;

    private UserService userService;

    private UUID callerId;
    private OrganisationModel org;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, organisationRepository);
        callerId = UUID.randomUUID();
        org = OrganisationModel.builder()
                .id(UUID.randomUUID()).name("Test Org").slug("TST").createdBy(callerId).build();
    }

    // JOB-244: search is no longer scoped to the caller's own org (that made it
    // impossible to ever find a genuinely new candidate to invite) — the caller must
    // still belong to an org to use the endpoint at all, but the results themselves
    // are unscoped.
    @Test
    @DisplayName("searchByEmail_shouldReturnResults_fromRepository")
    void searchByEmail_shouldReturnResults_fromRepository() {
        String prefix = "ali";
        List<UserModel> expected = List.of(
                UserModel.builder().id(UUID.randomUUID()).email("alice@example.com").name("Alice").build(),
                UserModel.builder().id(UUID.randomUUID()).email("alicia@example.com").name("Alicia").build()
        );
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(org));
        when(userRepository.searchByEmail(prefix, 10)).thenReturn(expected);

        List<UserModel> result = userService.searchByEmail(prefix, callerId);

        assertThat(result).isEqualTo(expected);
        verify(userRepository).searchByEmail(prefix, 10);
    }

    @Test
    @DisplayName("searchByEmail_shouldReturnEmptyList_whenNoMatches")
    void searchByEmail_shouldReturnEmptyList_whenNoMatches() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(org));
        when(userRepository.searchByEmail("zzz", 10)).thenReturn(List.of());

        List<UserModel> result = userService.searchByEmail("zzz", callerId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("searchByEmail_shouldEnforceLimit_ofTen")
    void searchByEmail_shouldEnforceLimit_ofTen() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(org));
        when(userRepository.searchByEmail("test", 10)).thenReturn(List.of());

        userService.searchByEmail("test", callerId);

        verify(userRepository).searchByEmail("test", 10);
    }

    @Test
    @DisplayName("searchByEmail_shouldThrowForbiddenException_whenCallerHasNoOrg")
    void searchByEmail_shouldThrow_whenCallerHasNoOrg() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.searchByEmail("ali", callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Organisation.NOT_IN_ORG);
    }
}
