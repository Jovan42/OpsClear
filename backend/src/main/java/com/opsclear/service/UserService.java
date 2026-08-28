package com.opsclear.service;

import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.UserModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final int SEARCH_LIMIT = 10;

    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;

    @Transactional(readOnly = true)
    public List<UserModel> searchByEmail(String emailPrefix, UUID callerId) {
        // Caller must still belong to an org to use this at all (JOB-244 didn't touch
        // that guard) — but the search itself is no longer scoped to that org; see
        // UserRepository.searchByEmail()'s own comment for why.
        organisationRepository.findByMember(callerId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_IN_ORG));
        log.debug("Searching users by email prefix '{}'", emailPrefix);
        return userRepository.searchByEmail(emailPrefix, SEARCH_LIMIT);
    }
}
