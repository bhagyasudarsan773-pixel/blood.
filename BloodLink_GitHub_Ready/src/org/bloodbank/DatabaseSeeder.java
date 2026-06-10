package org.bloodbank;

/**
 * Utility to initialize the database schema.
 * Run this to set up a fresh, empty database ready for manual data entry.
 */
public class DatabaseSeeder {
    public static void main(String[] args) {
        DatabaseManager.initializeDatabase();
        System.out.println("Database initialized successfully with empty tables.");
        System.out.println("You can now add your own data through the web interface.");
    }
}
