package com.opsclear.service;

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

    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;

    @Transactional(readOnly = true)
    public List<UserModel> searchByEmail(String emailPrefix, UUID callerId) {
        OrganisationModel org = organisationRepository.findByMember(callerId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_IN_ORG));
        log.debug("Searching users by email prefix '{}' within org {}", emailPrefix, org.getId());
        return userRepository.searchByEmailWithinOrg(emailPrefix, org.getId(), SEARCH_LIMIT);
    }
}
