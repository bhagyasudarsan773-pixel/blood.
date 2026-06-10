package org.bloodbank;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class FixDatabase {
    public static void main(String[] args) {
        String url = "jdbc:sqlite:bloodbank.db";
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            
            System.out.println("Applying schema fix...");
            try {
                stmt.execute("ALTER TABLE Requesters ADD COLUMN blood_group TEXT;");
                System.out.println("Column 'blood_group' added (or exists).");
            } catch (Exception e) {}
            
            try {
                stmt.execute("ALTER TABLE Requesters ADD COLUMN email TEXT;");
                System.out.println("Column 'email' added to Requesters table.");
            } catch (Exception e) {
                System.out.println("Note: Column 'email' might already exist: " + e.getMessage());
            }
            
            System.out.println("Fix complete!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
