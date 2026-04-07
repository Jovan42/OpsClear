package com.opsclear.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    public void sendInvite(String toEmail, String token) {
        log.info("STUB — invite email to {} with token {}", toEmail, token);
    }
}
