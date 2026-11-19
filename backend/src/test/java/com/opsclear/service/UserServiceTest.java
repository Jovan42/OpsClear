package com.opsclear.service;

import com.opsclear.exception.BadRequestException;
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
import static org.mockito.Mockito.verifyNoInteractions;
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

    // JOB-400: the search is scoped to the caller's organisation, matched by name or email.
    @Test
    @DisplayName("searchOrgMembers_shouldSearchCallerOrg_withTrimmedQuery")
    void searchOrgMembers_shouldSearchCallerOrg_withTrimmedQuery() {
        List<UserModel> expected = List.of(
                UserModel.builder().id(UUID.randomUUID()).email("alice@example.com").name("Alice").build()
        );
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(org));
        when(userRepository.searchOrgMembers(org.getId(), "ali", 10)).thenReturn(expected);

        List<UserModel> result = userService.searchOrgMembers("  ali ", callerId);

        assertThat(result).isEqualTo(expected);
        verify(userRepository).searchOrgMembers(org.getId(), "ali", 10);
    }

    @Test
    @DisplayName("searchOrgMembers_shouldReturnEmptyList_whenNoMatches")
    void searchOrgMembers_shouldReturnEmptyList_whenNoMatches() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(org));
        when(userRepository.searchOrgMembers(org.getId(), "zzz", 10)).thenReturn(List.of());

        assertThat(userService.searchOrgMembers("zzz", callerId)).isEmpty();
    }

    @Test
    @DisplayName("searchOrgMembers_shouldThrowBadRequest_whenQueryNullOrShorterThanTwo")
    void searchOrgMembers_shouldThrowBadRequest_whenQueryNullOrShorterThanTwo() {
        for (String query : new String[] {null, "", "a", "  a  "}) {
            assertThatThrownBy(() -> userService.searchOrgMembers(query, callerId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage(ErrorMessages.User.SEARCH_QUERY_TOO_SHORT);
        }
        verifyNoInteractions(organisationRepository, userRepository);
    }

    @Test
    @DisplayName("searchOrgMembers_shouldThrowForbiddenException_whenCallerHasNoOrg")
    void searchOrgMembers_shouldThrow_whenCallerHasNoOrg() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.searchOrgMembers("ali", callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Organisation.NOT_IN_ORG);
    }
}
