package com.opsclear.service;

import com.opsclear.model.UserModel;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final int SEARCH_LIMIT = 10;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserModel> searchByEmail(String emailPrefix) {
        log.debug("Searching users by email prefix: {}", emailPrefix);
        return userRepository.searchByEmail(emailPrefix, SEARCH_LIMIT);
    }
}
