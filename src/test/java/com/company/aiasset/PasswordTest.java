package com.company.aiasset;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = "$2a$10$44/5iuvUP1CDxGINLNrQyu0uDEwnnMKuo0tfKh/z2iVvcpwT2GgQi";

        System.out.println("Testing ChangeMe_0901: " + encoder.matches("ChangeMe_0901", hash));
        System.out.println("Testing admin: " + encoder.matches("admin", hash));
        System.out.println("Testing Admin123: " + encoder.matches("Admin123", hash));

        System.out.println("\nNew hash for ChangeMe_0901:");
        System.out.println(encoder.encode("ChangeMe_0901"));
    }
}
