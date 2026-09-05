package com.company.aiasset;

import java.sql.*;

public class CheckArchived {
    public static void main(String[] args) throws Exception {
        String url = requireEnvironment("AI_ASSET_DB_URL");
        String user = requireEnvironment("AI_ASSET_DB_USER");
        String password = requireEnvironment("AI_ASSET_DB_PASSWORD");

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name, archived FROM assets ORDER BY updated_at DESC")) {

            System.out.println("ID | Name | Archived");
            System.out.println("---+------+---------");
            while (rs.next()) {
                System.out.printf("%s | %s | %s%n",
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getBoolean("archived"));
            }
        }
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required");
        }
        return value;
    }
}
