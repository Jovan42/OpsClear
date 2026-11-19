package com.opsclear.service;

import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.OrganisationModel;
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
    private static final int MIN_QUERY_LENGTH = 2;

    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;

    @Transactional(readOnly = true)
    public List<UserModel> searchOrgMembers(String query, UUID callerId) {
        String term = query == null ? "" : query.strip();
        if (term.length() < MIN_QUERY_LENGTH) {
            throw new BadRequestException(ErrorMessages.User.SEARCH_QUERY_TOO_SHORT);
        }
        // JOB-400: results are limited to the caller's own organisation. Org invites are the way to bring
        // someone new in, so there's no reason to expose users of other organisations here.
        OrganisationModel org = organisationRepository.findByMember(callerId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_IN_ORG));
        log.debug("Searching members of organisation {} for '{}'", org.getId(), term);
        return userRepository.searchOrgMembers(org.getId(), term, SEARCH_LIMIT);
    }
}
