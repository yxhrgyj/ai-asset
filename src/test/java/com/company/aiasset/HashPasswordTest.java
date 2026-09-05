package com.company.aiasset;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashPasswordTest {
    @Test
    public void hashPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("ChangeMe_0901");
        System.out.println("Hash: " + hash);
    }
}
