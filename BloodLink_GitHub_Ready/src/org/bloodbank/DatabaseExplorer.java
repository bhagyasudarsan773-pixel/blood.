package org.bloodbank;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseExplorer {
    public static void main(String[] args) {
        String outputFile = "BloodBank_Live_Database.html";
        try (Connection conn = DatabaseManager.getConnection();
             PrintWriter out = new PrintWriter(new FileWriter(outputFile))) {

            out.println("<!DOCTYPE html><html><head><title>BloodLink | Live Database Explorer</title>");
            out.println("<style>");
            out.println("body { font-family: 'Segoe UI', sans-serif; background: #0f172a; color: white; padding: 40px; }");
            out.println(".container { max-width: 1200px; margin: 0 auto; }");
            out.println("h1 { color: #ff4757; margin-bottom: 30px; border-bottom: 1px solid rgba(255,255,255,0.1); padding-bottom: 10px; }");
            out.println("h2 { color: #f8fafc; margin-top: 50px; padding: 10px; background: rgba(255,255,255,0.05); border-radius: 8px; }");
            out.println("table { width: 100%; border-collapse: collapse; margin: 20px 0; background: #1b2132; border-radius: 12px; overflow: hidden; }");
            out.println("th, td { padding: 15px; border: 1px solid rgba(255,255,255,0.05); text-align: left; }");
            out.println("th { background: rgba(255, 71, 87, 0.2); color: #ff4757; text-transform: uppercase; font-size: 0.8rem; letter-spacing: 1px; }");
            out.println("tr:hover { background: rgba(255,255,255,0.02); }");
            out.println("</style></head><body><div class='container'>");
            out.println("<h1>🩸 BloodLink | Live Database Snapshot</h1>");
            out.println("<p style='color: #94a3b8;'>Generated automatically by DatabaseExplorer.java tool.</p>");

            DatabaseMetaData metaData = conn.getMetaData();
            String[] types = {"TABLE"};
            ResultSet tables = metaData.getTables(null, null, "%", types);

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                if (tableName.startsWith("sqlite_")) continue; // Skip internal tables

                out.println("<h2>Table: " + tableName + "</h2>");
                out.println("<table><thead><tr>");

                // Get columns for this table
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " LIMIT 1");
                ResultSetMetaData rsmd = rs.getMetaData();
                int columnCount = rsmd.getColumnCount();

                for (int i = 1; i <= columnCount; i++) {
                    out.println("<th>" + rsmd.getColumnName(i) + "</th>");
                }
                out.println("</tr></thead><tbody>");
                rs.close();

                // Get all rows
                ResultSet rows = stmt.executeQuery("SELECT * FROM " + tableName);
                while (rows.next()) {
                    out.println("<tr>");
                    for (int i = 1; i <= columnCount; i++) {
                        String val = rows.getString(i);
                        out.println("<td>" + (val == null ? "<i>null</i>" : val) + "</td>");
                    }
                    out.println("</tr>");
                }
                out.println("</tbody></table>");
                rows.close();
                stmt.close();
            }

            out.println("</div></body></html>");
            System.out.println("Success! Database snapshot saved to: " + outputFile);

        } catch (Exception e) {
            System.err.println("Error generating database snapshot: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
