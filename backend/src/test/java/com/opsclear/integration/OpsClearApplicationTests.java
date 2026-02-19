package com.opsclear.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OpsClearApplicationTests {

    @Test
    @DisplayName("Spring application context should load successfully")
    void contextLoads() {
    }
}
