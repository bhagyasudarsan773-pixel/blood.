package org.bloodbank;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class WebServer {
    public static void main(String[] args) throws Exception {
        DatabaseManager.initializeDatabase();
        
        int port = 9091;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try { port = Integer.parseInt(envPort); } catch (Exception e) {}
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/inventory", new InventoryHandler());
        server.createContext("/api/donors", new DonorsHandler());
        server.createContext("/api/donations", new DonationsHandler());
        server.createContext("/api/requests", new RequestsHandler());
        server.createContext("/api/stats", new StatsHandler());
        server.createContext("/api/fulfill_request", new FulfillRequestHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/my_donations", new MyDonationsHandler());
        server.createContext("/api/update_profile", new UpdateProfileHandler());
        server.createContext("/api/users", new UsersHandler());
        
        // Dedicated Delete Endpoints for robustness
        server.createContext("/api/delete_donor", new DeleteDonorHandler());
        server.createContext("/api/delete_request", new DeleteRequestHandler());
        server.createContext("/api/delete_user", new DeleteUserHandler());
        
        // NEW: Hospitals management
        server.createContext("/api/hospitals", new HospitalsHandler());
        server.createContext("/api/delete_hospital", new DeleteHospitalHandler());
        
        // NEW: Activity Log
        server.createContext("/api/activity_log", new ActivityLogHandler());
        
        // NEW: Eligibility check
        server.createContext("/api/check_eligibility", new EligibilityCheckHandler());
        
        // NEW: Blood compatibility
        server.createContext("/api/compatibility", new CompatibilityHandler());
        
        server.setExecutor(null);
        System.out.println("Starting web server on http://localhost:9091 ...");
        server.start();
    }

    // ===== UTILITY: Log activity =====
    private static void logActivity(String action, String description, String performedBy) {
        try {
            Connection conn = DatabaseManager.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO Activity_Log(action, description, performed_by) VALUES(?,?,?)");
            ps.setString(1, action);
            ps.setString(2, description);
            ps.setString(3, performedBy == null ? "System" : performedBy);
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            File file = new File("web" + path);
            
            if (!file.exists() && !path.endsWith(".html")) {
                File htmlFile = new File("web" + path + ".html");
                if (htmlFile.exists()) {
                    path = path + ".html";
                    file = htmlFile;
                }
            }
            
            if (file.exists() && !file.isDirectory()) {
                t.getResponseHeaders().add("Content-Type", getContentType(path));
                t.getResponseHeaders().add("Cache-Control", "no-cache, no-store, must-revalidate");
                t.sendResponseHeaders(200, file.length());
                OutputStream os = t.getResponseBody();
                Files.copy(file.toPath(), os);
                os.close();
            } else {
                String response = "404 Not Found";
                t.sendResponseHeaders(404, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            return "text/plain";
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                String user = extractStr(body, "username");
                String pass = hashPassword(extractStr(body, "password"));
                try {
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                        "SELECT u.role, u.donor_id, u.hospital_id, u.requester_id, " +
                        "COALESCE(d.name, h.name, r.name) as name, " +
                        "COALESCE(d.blood_group, r.blood_group) as blood_group, " +
                        "COALESCE(d.email, r.email) as email " +
                        "FROM Users u " +
                        "LEFT JOIN Donors d ON u.donor_id = d.donor_id " +
                        "LEFT JOIN Hospitals h ON u.hospital_id = h.hospital_id " +
                        "LEFT JOIN Requesters r ON u.requester_id = r.requester_id " +
                        "WHERE u.username = ? AND u.password_hash = ?");
                    ps.setString(1, user); ps.setString(2, pass);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        String role = rs.getString(1);
                        int donorId = rs.getInt(2);
                        int hospitalId = rs.getInt(3);
                        int requesterId = rs.getInt(4);
                        String nameValue = rs.getString(5);
                        String bgValue = rs.getString(6);
                        String emailValue = rs.getString(7);
                        logActivity("LOGIN", "User '" + user + "' logged in (" + role + ")", user);
                        // Standardize role to Patient if it's Requester for frontend consistency
                        String frontendRole = "Requester".equalsIgnoreCase(role) ? "Patient" : role;
                        sendJson(t, String.format("{\"success\":true, \"role\":\"%s\", \"donor_id\":%d, \"hospital_id\":%d, \"requester_id\":%d, \"name\":\"%s\", \"blood_group\":\"%s\", \"email\":\"%s\"}", 
                            frontendRole, donorId, hospitalId, requesterId, 
                            nameValue != null ? nameValue : "Admin", 
                            bgValue != null ? bgValue : "",
                            emailValue != null ? emailValue : ""), 200);
                    } else sendJson(t, "{\"error\":\"Invalid credentials\"}", 401);
                    rs.close(); ps.close();
                } catch (Exception e) { sendJson(t, "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "'").replace("\n", " ").replace("\\", "\\\\") : "Unknown Error") + "\"}", 500); }
            }
        }
    }

    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                String user = extractStr(body, "username");
                String pass = hashPassword(extractStr(body, "password"));
                String name = extractStr(body, "name");
                String bg = extractStr(body, "blood_group");
                String contact = extractStr(body, "contact");
                String email = extractStr(body, "email");
                String address = extractStr(body, "address");
                String role = extractStr(body, "role");
                if (role == null || role.isEmpty()) role = "Donor";
                
                try {
                    Connection conn = DatabaseManager.getConnection();
                    conn.setAutoCommit(false);
                    
                    int donorId = 0, requesterId = 0, hId = 0;
                    if ("Donor".equalsIgnoreCase(role)) {
                        // Create Donor ONLY
                        PreparedStatement psD = conn.prepareStatement("INSERT INTO Donors(name, blood_group, contact_number, email, address) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                        psD.setString(1, name); psD.setString(2, bg); psD.setString(3, contact); psD.setString(4, email); psD.setString(5, address);
                        psD.executeUpdate();
                        ResultSet rsD = psD.getGeneratedKeys();
                        if (rsD.next()) donorId = rsD.getInt(1);
                        rsD.close(); psD.close();
                    } else if ("Requester".equalsIgnoreCase(role) || "Patient".equalsIgnoreCase(role)) {
                        // Create Requester ONLY (Patient maps to Requester in backend)
                        role = "Patient"; // Ensure unified naming for patient role
                        PreparedStatement psR = conn.prepareStatement("INSERT INTO Requesters(name, blood_group, contact_number, email, address) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                        psR.setString(1, name); psR.setString(2, bg); psR.setString(3, contact); psR.setString(4, email); psR.setString(5, address);
                        psR.executeUpdate();
                        ResultSet rsR = psR.getGeneratedKeys();
                        if (rsR.next()) requesterId = rsR.getInt(1);
                        rsR.close(); psR.close();
                    } else if ("Hospital".equalsIgnoreCase(role)) {
                        // Create Hospital Record
                        PreparedStatement psH = conn.prepareStatement("INSERT INTO Hospitals(name, contact_number, address) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS);
                        psH.setString(1, name); psH.setString(2, contact); psH.setString(3, address);
                        psH.executeUpdate();
                        ResultSet rsH = psH.getGeneratedKeys();
                        if (rsH.next()) hId = rsH.getInt(1);
                        rsH.close(); psH.close();
                    }

                    PreparedStatement psU = conn.prepareStatement("INSERT INTO Users(username, password_hash, role, donor_id, requester_id, hospital_id) VALUES(?,?,?,?,?,?)");
                    psU.setString(1, user); psU.setString(2, pass); psU.setString(3, role);
                    if (donorId > 0) psU.setInt(4, donorId); else psU.setNull(4, java.sql.Types.INTEGER);
                    if (requesterId > 0) psU.setInt(5, requesterId); else psU.setNull(5, java.sql.Types.INTEGER);
                    if (hId > 0) psU.setInt(6, hId); else psU.setNull(6, java.sql.Types.INTEGER);
                    psU.executeUpdate(); psU.close();
                    
                    conn.commit(); conn.setAutoCommit(true);
                    logActivity("REGISTER", "New " + role + " '" + name + "' registered", user);
                    sendJson(t, "{\"success\":true}", 200);
                } catch (Exception e) { 
                    e.printStackTrace();
                    sendJson(t, "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "'").replace("\n", " ").replace("\\", "\\\\") : "Unknown Error") + "\"}", 500); 
                }
            }
        }
    }

    static class UpdateProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                String role = extractStr(body, "role");
                int id = Integer.parseInt(extractNum(body, "id"));
                String name = extractStr(body, "name");
                String bg = extractStr(body, "blood_group");
                String contact = extractStr(body, "contact");
                String email = extractStr(body, "email");
                String address = extractStr(body, "address");
                String user = extractStr(body, "username");

                try {
                    Connection conn = DatabaseManager.getConnection();
                    if ("Donor".equalsIgnoreCase(role)) {
                        PreparedStatement ps = conn.prepareStatement("UPDATE Donors SET name=?, contact_number=?, email=?, address=? WHERE donor_id=?");
                        ps.setString(1, name); ps.setString(2, contact); ps.setString(3, email); ps.setString(4, address); ps.setInt(5, id);
                        ps.executeUpdate(); ps.close();
                    } else if ("Requester".equalsIgnoreCase(role)) {
                        PreparedStatement ps = conn.prepareStatement("UPDATE Requesters SET name=?, blood_group=?, contact_number=?, email=?, address=? WHERE requester_id=?");
                        ps.setString(1, name); ps.setString(2, bg); ps.setString(3, contact); ps.setString(4, email); ps.setString(5, address); ps.setInt(6, id);
                        ps.executeUpdate(); ps.close();
                    }
                    logActivity("UPDATE_PROFILE", "User updated profile: " + name, user);
                    sendJson(t, "{\"success\":true}", 200);
                } catch (Exception e) { sendJson(t, "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "'").replace("\n", " ").replace("\\", "\\\\") : "Unknown Error") + "\"}", 500); }
            }
        }
    }

    static class MyDonationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                int donorId = Integer.parseInt(extractNum(body, "donor_id"));
                List<String> list = new ArrayList<>();
                try {
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement("SELECT donation_date, quantity FROM Donation_Records WHERE donor_id=? ORDER BY donation_date DESC");
                    ps.setInt(1, donorId);
                    ResultSet rs = ps.executeQuery();
                    while(rs.next()) {
                        list.add(String.format("{\"date\":\"%s\",\"qty\":%d}", rs.getString(1), rs.getInt(2)));
                    }
                    rs.close(); ps.close();
                } catch (Exception e) { e.printStackTrace(); }
                sendJson(t, "[" + String.join(",", list) + "]", 200);
            }
        }
    }

    private static String hashPassword(String password) {
        return AuthUtils.hashPassword(password);
    }

    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                int td=0,tb=0,pr=0,ar=0,er=0,el=0;
                try {
                    Connection conn = DatabaseManager.getConnection();
                    Statement s = conn.createStatement();
                    ResultSet r1 = s.executeQuery("SELECT COUNT(*) FROM Donors"); if(r1.next()) td=r1.getInt(1); r1.close();
                    ResultSet r2 = s.executeQuery("SELECT COALESCE(SUM(quantity_bags),0) FROM Blood_Inventory"); if(r2.next()) tb=r2.getInt(1); r2.close();
                    ResultSet r3 = s.executeQuery("SELECT COUNT(*) FROM Blood_Requests WHERE status LIKE 'Pending%'"); if(r3.next()) pr=r3.getInt(1); r3.close();
                    ResultSet r4 = s.executeQuery("SELECT COUNT(*) FROM Blood_Requests WHERE status = 'Approved'"); if(r4.next()) ar=r4.getInt(1); r4.close();
                    ResultSet r5 = s.executeQuery("SELECT COUNT(*) FROM Blood_Requests WHERE is_emergency = 1"); if(r5.next()) er=r5.getInt(1); r5.close();
                    // Count eligible donors (last donated > 56 days ago or never)
                    ResultSet r6 = s.executeQuery("SELECT COUNT(*) FROM Donors WHERE last_donation_date IS NULL OR julianday('now') - julianday(last_donation_date) >= 56"); if(r6.next()) el=r6.getInt(1); r6.close();
                    s.close();
                } catch (Exception e) { e.printStackTrace(); }
                sendJson(t, String.format("{\"total_donors\":%d,\"total_bags\":%d,\"pending_requests\":%d,\"approved_requests\":%d,\"emergency_requests\":%d,\"eligible_donors\":%d}", td,tb,pr,ar,er,el), 200);
            }
        }
    }

    static class InventoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                List<String> items = new ArrayList<>();
                try {
                    Connection conn = DatabaseManager.getConnection();
                    Statement s = conn.createStatement();
                    ResultSet rs = s.executeQuery("SELECT * FROM Blood_Inventory");
                    while (rs.next()) {
                        items.add(String.format("{\"blood_group\":\"%s\",\"quantity\":%d}", rs.getString("blood_group"), rs.getInt("quantity_bags")));
                    }
                    rs.close(); s.close();
                } catch (Exception e) { e.printStackTrace(); }
                sendJson(t, "[" + String.join(",", items) + "]", 200);
            }
        }
    }

    static class DonorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                List<String> donors = new ArrayList<>();
                try {
                    Connection conn = DatabaseManager.getConnection();
                    Statement s = conn.createStatement();
                    ResultSet rs = s.executeQuery("SELECT *, CASE WHEN last_donation_date IS NULL THEN 1 WHEN julianday('now') - julianday(last_donation_date) >= 56 THEN 1 ELSE 0 END as is_eligible, CASE WHEN last_donation_date IS NOT NULL AND julianday('now') - julianday(last_donation_date) < 56 THEN CAST(56 - (julianday('now') - julianday(last_donation_date)) AS INTEGER) ELSE 0 END as days_until_eligible FROM Donors ORDER BY donor_id DESC");
                    while (rs.next()) {
                        String n = nvl(rs.getString("name")), bg = nvl(rs.getString("blood_group")),
                               c = nvl(rs.getString("contact_number")), e = nvl(rs.getString("email")),
                               a = nvl(rs.getString("address")), ld = nvl(rs.getString("last_donation_date"));
                        int isEligible = rs.getInt("is_eligible");
                        int daysUntil = rs.getInt("days_until_eligible");
                        donors.add(String.format("{\"id\":%d,\"name\":\"%s\",\"blood_group\":\"%s\",\"contact\":\"%s\",\"email\":\"%s\",\"address\":\"%s\",\"last_donation\":\"%s\",\"is_eligible\":%d,\"days_until_eligible\":%d}", rs.getInt("donor_id"), n, bg, c, e, a, ld, isEligible, daysUntil));
                    }
                    rs.close(); s.close();
                } catch (Exception e) { e.printStackTrace(); }
                sendJson(t, "[" + String.join(",", donors) + "]", 200);
            } else if ("POST".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                String name=extractStr(body,"name"), bg=extractStr(body,"blood_group"), contact=extractStr(body,"contact"),
                       email=extractStr(body,"email"), address=extractStr(body,"address"), ld=extractStr(body,"last_donation_date");
                try {
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement("INSERT INTO Donors(name,blood_group,contact_number,email,address,last_donation_date) VALUES(?,?,?,?,?,?)");
                    ps.setString(1,name); ps.setString(2,bg); ps.setString(3,contact); ps.setString(4,email); ps.setString(5,address);
                    if(ld==null||ld.trim().isEmpty()) ps.setNull(6,java.sql.Types.DATE); else ps.setString(6,ld);
                    ps.executeUpdate(); ps.close();
                    logActivity("ADD_DONOR", "Donor '" + name + "' (" + bg + ") added", "Admin");
                    sendJson(t, "{\"success\":true}", 200);
                } catch (Exception e) { sendJson(t, "{\"error\":\""+e.getMessage()+"\"}", 500); }
            } else if ("PUT".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                int id = Integer.parseInt(extractNum(body,"id"));
                String name=extractStr(body,"name"), bg=extractStr(body,"blood_group"), contact=extractStr(body,"contact"),
                       email=extractStr(body,"email"), address=extractStr(body,"address"), ld=extractStr(body,"last_donation_date");
                try {
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement("UPDATE Donors SET name=?,blood_group=?,contact_number=?,email=?,address=?,last_donation_date=? WHERE donor_id=?");
                    ps.setString(1,name); ps.setString(2,bg); ps.setString(3,contact); ps.setString(4,email); ps.setString(5,address);
                    if(ld==null||ld.trim().isEmpty()) ps.setNull(6,java.sql.Types.DATE); else ps.setString(6,ld);
                    ps.setInt(7,id);
                    int rows = ps.executeUpdate(); ps.close();
                    if(rows>0) { logActivity("UPDATE_DONOR", "Donor #" + id + " updated", "Admin"); sendJson(t,"{\"success\":true}",200); }
                    else sendJson(t,"{\"error\":\"Donor not found\"}",404);
                } catch (Exception e) { sendJson(t, "{\"error\":\""+e.getMessage()+"\"}", 500); }
            } else if ("DELETE".equals(t.getRequestMethod())) {
                try {
                    int id = getRequestId(t);
                    if (id <= 0) { sendJson(t, "{\"error\":\"Invalid ID\"}", 400); return; }
                    
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement p1 = conn.prepareStatement("DELETE FROM Donation_Records WHERE donor_id=?");
                    p1.setInt(1,id); p1.executeUpdate(); p1.close();
                    PreparedStatement p2 = conn.prepareStatement("DELETE FROM Users WHERE donor_id=?");
                    p2.setInt(1,id); p2.executeUpdate(); p2.close();
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM Donors WHERE donor_id=?");
                    ps.setInt(1,id);
                    int rows = ps.executeUpdate(); ps.close();
                    if(rows>0) { logActivity("DELETE_DONOR", "Donor #" + id + " deleted", "Admin"); sendJson(t,"{\"success\":true}",200); }
                    else sendJson(t,"{\"error\":\"Donor not found\"}",404);
                } catch (Exception e) { sendJson(t, "{\"error\":\""+e.getMessage()+"\"}", 500); }
            }
        }
    }

    static class DonationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                int donorId = Integer.parseInt(extractNum(body,"donor_id"));
                int quantity = Integer.parseInt(extractNum(body,"quantity"));
                // Check for force flag (skip eligibility)
                String forceStr = extractStr(body, "force");
                boolean force = "true".equals(forceStr);
                
                try {
                    Connection conn = DatabaseManager.getConnection();
                    conn.setAutoCommit(false);
                    PreparedStatement ps = conn.prepareStatement("SELECT blood_group, last_donation_date FROM Donors WHERE donor_id=?");
                    ps.setInt(1,donorId); ResultSet rs = ps.executeQuery();
                    if(!rs.next()) { rs.close(); ps.close(); conn.setAutoCommit(true); sendJson(t,"{\"error\":\"Donor ID not found\"}",400); return; }
                    String userBg = rs.getString("blood_group");
                    String lastDonation = rs.getString("last_donation_date");
                    rs.close(); ps.close();

                    // Eligibility check (56-day rule)
                    if (!force && lastDonation != null && !lastDonation.isEmpty()) {
                        PreparedStatement ec = conn.prepareStatement("SELECT CAST(julianday('now') - julianday(?) AS INTEGER) as days_since");
                        ec.setString(1, lastDonation);
                        ResultSet er = ec.executeQuery();
                        if (er.next()) {
                            int daysSince = er.getInt("days_since");
                            if (daysSince < 56) {
                                er.close(); ec.close();
                                conn.setAutoCommit(true);
                                int daysLeft = 56 - daysSince;
                                sendJson(t, "{\"error\":\"Donor not eligible! Last donated " + daysSince + " days ago. Must wait " + daysLeft + " more days (56-day rule).\",\"ineligible\":true,\"days_left\":" + daysLeft + "}", 400);
                                return;
                            }
                        }
                        er.close(); ec.close();
                    }

                    PreparedStatement u1 = conn.prepareStatement("UPDATE Donors SET last_donation_date=date('now') WHERE donor_id=?");
                    u1.setInt(1,donorId); u1.executeUpdate(); u1.close();

                    PreparedStatement i1 = conn.prepareStatement("INSERT INTO Donation_Records(donor_id,donation_date,quantity) VALUES(?,date('now'),?)");
                    i1.setInt(1,donorId); i1.setInt(2,quantity); i1.executeUpdate(); i1.close();

                    PreparedStatement ci = conn.prepareStatement("SELECT quantity_bags FROM Blood_Inventory WHERE blood_group=?");
                    ci.setString(1,userBg); ResultSet ri = ci.executeQuery();
                    if(ri.next()) {
                        ri.close(); ci.close();
                        PreparedStatement ui = conn.prepareStatement("UPDATE Blood_Inventory SET quantity_bags=quantity_bags+? WHERE blood_group=?");
                        ui.setInt(1,quantity); ui.setString(2,userBg); ui.executeUpdate(); ui.close();
                    } else {
                        ri.close(); ci.close();
                        PreparedStatement ii = conn.prepareStatement("INSERT INTO Blood_Inventory(blood_group,quantity_bags) VALUES(?,?)");
                        ii.setString(1,userBg); ii.setInt(2,quantity); ii.executeUpdate(); ii.close();
                    }
                    conn.commit(); conn.setAutoCommit(true);
                    logActivity("DONATION", quantity + " bag(s) of " + userBg + " donated by Donor #" + donorId, "Donor #" + donorId);
                    sendJson(t, "{\"success\":true,\"message\":\"Donation recorded for "+userBg+"\"}", 200);
                } catch (Exception e) { sendJson(t, "{\"error\":\""+e.getMessage()+"\"}", 500); }
            }
        }
    }

    static class RequestsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                List<String> reqs = new ArrayList<>();
                try {
                    Connection conn = DatabaseManager.getConnection();
                    Statement s = conn.createStatement();
                    ResultSet rs = s.executeQuery(
                        "SELECT r.request_id, COALESCE(h.name, req.name) as requester_name, " +
                        "r.blood_group_required, r.quantity_needed, r.request_date, r.status, r.is_emergency " +
                        "FROM Blood_Requests r " +
                        "LEFT JOIN Hospitals h ON r.hospital_id=h.hospital_id " +
                        "LEFT JOIN Requesters req ON r.requester_id=req.requester_id " +
                        "ORDER BY r.is_emergency DESC, r.request_id DESC");
                    while(rs.next()) {
                        reqs.add(String.format("{\"id\":%d,\"hospital\":\"%s\",\"blood_group\":\"%s\",\"quantity\":%d,\"date\":\"%s\",\"status\":\"%s\",\"is_emergency\":%d}", 
                            rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getString(6), rs.getInt(7)));
                    }
                    rs.close(); s.close();
                } catch (Exception e) { e.printStackTrace(); }
                sendJson(t, "[" + String.join(",", reqs) + "]", 200);
            } else if ("POST".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                String bg = extractStr(body,"blood_group");
                int qty = Integer.parseInt(extractNum(body,"quantity"));
                String hospitalIdStr = extractNum(body, "hospital_id");
                String requesterIdStr = extractNum(body, "requester_id");
                int hospId = hospitalIdStr.isEmpty() ? -1 : Integer.parseInt(hospitalIdStr);
                int reqId = requesterIdStr.isEmpty() ? -1 : Integer.parseInt(requesterIdStr);
                
                String emergencyStr = extractStr(body,"is_emergency");
                boolean isEmergency = "true".equals(emergencyStr) || "1".equals(emergencyStr);
                
                try {
                    Connection conn = DatabaseManager.getConnection();
                    conn.setAutoCommit(false);
                    
                    // If no ID provided but name is there, try to fallback (for backward compatibility if needed)
                    if (hospId <= 0 && reqId <= 0) {
                        String hospName = extractStr(body, "hospital");
                        if (!hospName.isEmpty()) {
                            PreparedStatement ch = conn.prepareStatement("SELECT hospital_id FROM Hospitals WHERE name=?");
                            ch.setString(1, hospName); ResultSet rh = ch.executeQuery();
                            if (rh.next()) hospId = rh.getInt(1);
                            rh.close(); ch.close();
                        }
                    }

                    PreparedStatement ci = conn.prepareStatement("SELECT quantity_bags FROM Blood_Inventory WHERE blood_group=?");
                    ci.setString(1,bg); ResultSet ri = ci.executeQuery();
                    int available = 0; if(ri.next()) available=ri.getInt(1); ri.close(); ci.close();

                    String status;
                    if (isEmergency) {
                        status = "Approved";
                        if (available >= qty) {
                            PreparedStatement ui = conn.prepareStatement("UPDATE Blood_Inventory SET quantity_bags=quantity_bags-? WHERE blood_group=?");
                            ui.setInt(1,qty); ui.setString(2,bg); ui.executeUpdate(); ui.close();
                        } else if (available > 0) {
                            PreparedStatement ui = conn.prepareStatement("UPDATE Blood_Inventory SET quantity_bags=0 WHERE blood_group=?");
                            ui.setString(1,bg); ui.executeUpdate(); ui.close();
                        }
                    } else {
                        status = (available>=qty) ? "Approved" : "Pending (Low Stock)";
                        if(available>=qty) {
                            PreparedStatement ui = conn.prepareStatement("UPDATE Blood_Inventory SET quantity_bags=quantity_bags-? WHERE blood_group=?");
                            ui.setInt(1,qty); ui.setString(2,bg); ui.executeUpdate(); ui.close();
                        }
                    }
                    
                    PreparedStatement ir = conn.prepareStatement("INSERT INTO Blood_Requests(hospital_id, requester_id, blood_group_required, quantity_needed, request_date, status, is_emergency) VALUES(?,?,?,?,date('now'),?,?)");
                    if (hospId > 0) ir.setInt(1, hospId); else ir.setNull(1, java.sql.Types.INTEGER);
                    if (reqId > 0) ir.setInt(2, reqId); else ir.setNull(2, java.sql.Types.INTEGER);
                    ir.setString(3, bg); ir.setInt(4, qty); ir.setString(5, status); ir.setInt(6, isEmergency ? 1 : 0);
                    ir.executeUpdate(); ir.close();

                    conn.commit(); conn.setAutoCommit(true);
                    logActivity(isEmergency ? "EMERGENCY_REQUEST" : "REQUEST", "New request for " + qty + " bags of " + bg + " status: " + status, "User");
                    sendJson(t, "{\"success\":true,\"status\":\""+status+"\",\"is_emergency\":" + isEmergency + "}", 200);
                } catch (Exception e) { 
                    e.printStackTrace();
                    sendJson(t, "{\"error\":\""+e.getMessage()+"\"}", 500); 
                }
            } else if ("DELETE".equals(t.getRequestMethod())) {
                try {
                    int id = getRequestId(t);
                    if (id <= 0) { sendJson(t, "{\"error\":\"Invalid ID\"}", 400); return; }
                    
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM Blood_Requests WHERE request_id=?");
                    ps.setInt(1,id);
                    int rows = ps.executeUpdate(); ps.close();
                    if(rows>0) { logActivity("DELETE_REQUEST", "Request #" + id + " deleted", "Admin"); sendJson(t,"{\"success\":true}",200); }
                    else sendJson(t,"{\"error\":\"Request not found\"}",404);
                } catch (Exception e) { sendJson(t, "{\"error\":\""+e.getMessage()+"\"}", 500); }
            }
        }
    }

    static class FulfillRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                int reqId = Integer.parseInt(extractNum(body,"request_id"));
                try {
                    Connection conn = DatabaseManager.getConnection();
                    conn.setAutoCommit(false);
                    PreparedStatement gr = conn.prepareStatement("SELECT blood_group_required,quantity_needed,status FROM Blood_Requests WHERE request_id=?");
                    gr.setInt(1,reqId); ResultSet rr = gr.executeQuery();
                    if(!rr.next()) { rr.close(); gr.close(); conn.setAutoCommit(true); sendJson(t,"{\"error\":\"Request not found\"}",404); return; }
                    String bg = rr.getString("blood_group_required"); int qty = rr.getInt("quantity_needed"); String st = rr.getString("status");
                    rr.close(); gr.close();
                    if("Approved".equals(st)) { conn.setAutoCommit(true); sendJson(t,"{\"success\":false,\"message\":\"Already approved.\"}",200); return; }

                    PreparedStatement ci = conn.prepareStatement("SELECT quantity_bags FROM Blood_Inventory WHERE blood_group=?");
                    ci.setString(1,bg); ResultSet ri = ci.executeQuery();
                    int available=0; if(ri.next()) available=ri.getInt(1); ri.close(); ci.close();

                    if(available>=qty) {
                        PreparedStatement ui = conn.prepareStatement("UPDATE Blood_Inventory SET quantity_bags=quantity_bags-? WHERE blood_group=?");
                        ui.setInt(1,qty); ui.setString(2,bg); ui.executeUpdate(); ui.close();
                        PreparedStatement ur = conn.prepareStatement("UPDATE Blood_Requests SET status='Approved' WHERE request_id=?");
                        ur.setInt(1,reqId); ur.executeUpdate(); ur.close();
                        conn.commit(); conn.setAutoCommit(true);
                        logActivity("FULFILL", "Request #" + reqId + " fulfilled (" + qty + " bags of " + bg + ")", "Admin");
                        sendJson(t, "{\"success\":true,\"message\":\"Successfully processed and deducted from inventory!\"}", 200);
                    } else {
                        conn.rollback(); conn.setAutoCommit(true);
                        sendJson(t, "{\"success\":false,\"message\":\"Not enough stock. Have "+available+" bags, need "+qty+". Add a donation first!\"}", 200);
                    }
                } catch (Exception e) { sendJson(t, "{\"error\":\""+e.getMessage()+"\"}", 500); }
            }
        }
    }

    static class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                List<String> users = new ArrayList<>();
                try {
                    Connection conn = DatabaseManager.getConnection();
                    Statement s = conn.createStatement();
                    ResultSet rs = s.executeQuery(
                        "SELECT u.user_id, u.username, u.role, " +
                        "COALESCE(d.name, r.name, h.name, 'Admin') as display_name " +
                        "FROM Users u " +
                        "LEFT JOIN Donors d ON u.donor_id = d.donor_id " +
                        "LEFT JOIN Requesters r ON u.requester_id = r.requester_id " +
                        "LEFT JOIN Hospitals h ON u.hospital_id = h.hospital_id");
                    while (rs.next()) {
                        users.add(String.format("{\"id\":%d,\"username\":\"%s\",\"role\":\"%s\",\"name\":\"%s\"}",
                            rs.getInt("user_id"), rs.getString("username"), rs.getString("role"), rs.getString("display_name")));
                    }
                    rs.close(); s.close();
                } catch (Exception e) { e.printStackTrace(); }
                sendJson(t, "[" + String.join(",", users) + "]", 200);
            } else if ("POST".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                String user = extractStr(body, "username");
                String pass = hashPassword(extractStr(body, "password"));
                String role = extractStr(body, "role");
                if (role.isEmpty()) role = "Admin";
                try {
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement("INSERT INTO Users(username, password_hash, role) VALUES(?,?,?)");
                    ps.setString(1, user); ps.setString(2, pass); ps.setString(3, role);
                    ps.executeUpdate(); ps.close();
                    logActivity("ADD_USER", "User '" + user + "' (" + role + ") created", "Admin");
                    sendJson(t, "{\"success\":true}", 200);
                } catch (Exception e) { sendJson(t, "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "'").replace("\n", " ").replace("\\", "\\\\") : "Unknown Error") + "\"}", 500); }
            } else if ("DELETE".equals(t.getRequestMethod())) {
                try {
                    int id = getRequestId(t);
                    if (id <= 0) { sendJson(t, "{\"error\":\"Invalid ID\"}", 400); return; }
                    
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement check = conn.prepareStatement("SELECT username FROM Users WHERE user_id=?");
                    check.setInt(1, id); ResultSet rc = check.executeQuery();
                    if(rc.next() && "admin".equalsIgnoreCase(rc.getString("username"))) {
                        rc.close(); check.close();
                        sendJson(t, "{\"error\":\"Cannot delete default admin\"}", 403);
                        return;
                    }
                    rc.close(); check.close();

                    PreparedStatement ps = conn.prepareStatement("DELETE FROM Users WHERE user_id=?");
                    ps.setInt(1, id);
                    int rows = ps.executeUpdate(); ps.close();
                    if(rows>0) { logActivity("DELETE_USER", "User #" + id + " deleted", "Admin"); sendJson(t,"{\"success\":true}",200); }
                    else sendJson(t,"{\"error\":\"User not found\"}",404);
                } catch (Exception e) { sendJson(t, "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "'").replace("\n", " ").replace("\\", "\\\\") : "Unknown Error") + "\"}", 500); }
            }
        }
    }

    // ===== NEW: Hospitals Management =====
    static class HospitalsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                List<String> hospitals = new ArrayList<>();
                try {
                    Connection conn = DatabaseManager.getConnection();
                    Statement s = conn.createStatement();
                    ResultSet rs = s.executeQuery(
                        "SELECT h.hospital_id, h.name, h.contact_number, h.address, " +
                        "COUNT(r.request_id) as total_requests, " +
                        "SUM(CASE WHEN r.status='Approved' THEN 1 ELSE 0 END) as approved_requests " +
                        "FROM Hospitals h LEFT JOIN Blood_Requests r ON h.hospital_id=r.hospital_id " +
                        "GROUP BY h.hospital_id ORDER BY h.hospital_id DESC");
                    while (rs.next()) {
                        hospitals.add(String.format("{\"id\":%d,\"name\":\"%s\",\"contact\":\"%s\",\"address\":\"%s\",\"total_requests\":%d,\"approved_requests\":%d}",
                            rs.getInt("hospital_id"), nvl(rs.getString("name")), nvl(rs.getString("contact_number")), nvl(rs.getString("address")),
                            rs.getInt("total_requests"), rs.getInt("approved_requests")));
                    }
                    rs.close(); s.close();
                } catch (Exception e) { e.printStackTrace(); }
                sendJson(t, "[" + String.join(",", hospitals) + "]", 200);
            } else if ("POST".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                String name = extractStr(body, "name");
                String contact = extractStr(body, "contact");
                String address = extractStr(body, "address");
                String user = extractStr(body, "username");
                String pass = hashPassword(extractStr(body, "password"));
                
                try {
                    Connection conn = DatabaseManager.getConnection();
                    conn.setAutoCommit(false);
                    
                    PreparedStatement psH = conn.prepareStatement("INSERT INTO Hospitals(name, contact_number, address) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS);
                    psH.setString(1, name); psH.setString(2, contact); psH.setString(3, address);
                    psH.executeUpdate();
                    
                    ResultSet rsH = psH.getGeneratedKeys();
                    int hospitalId = 0;
                    if (rsH.next()) hospitalId = rsH.getInt(1);
                    rsH.close(); psH.close();
                    
                    if (hospitalId > 0 && !user.isEmpty()) {
                        PreparedStatement psU = conn.prepareStatement("INSERT INTO Users(username, password_hash, role, hospital_id) VALUES(?,?,?,?)");
                        psU.setString(1, user); psU.setString(2, pass); psU.setString(3, "Hospital"); psU.setInt(4, hospitalId);
                        psU.executeUpdate(); psU.close();
                    }
                    
                    conn.commit(); conn.setAutoCommit(true);
                    logActivity("ADD_HOSPITAL", "Hospital '" + name + "' registered with user '" + user + "'", user);
                    sendJson(t, String.format("{\"success\":true, \"hospital_id\":%d}", hospitalId), 200);
                } catch (Exception e) { 
                    sendJson(t, "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "'").replace("\n", " ").replace("\\", "\\\\") : "Unknown Error") + "\"}", 500); 
                }
            } else if ("PUT".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                int id = Integer.parseInt(extractNum(body, "id"));
                String name = extractStr(body, "name");
                String contact = extractStr(body, "contact");
                String address = extractStr(body, "address");
                try {
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement("UPDATE Hospitals SET name=?, contact_number=?, address=? WHERE hospital_id=?");
                    ps.setString(1, name); ps.setString(2, contact); ps.setString(3, address); ps.setInt(4, id);
                    int rows = ps.executeUpdate(); ps.close();
                    if(rows>0) { logActivity("UPDATE_HOSPITAL", "Hospital #" + id + " updated", "Admin"); sendJson(t,"{\"success\":true}",200); }
                    else sendJson(t,"{\"error\":\"Hospital not found\"}",404);
                } catch (Exception e) { sendJson(t, "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "'").replace("\n", " ").replace("\\", "\\\\") : "Unknown Error") + "\"}", 500); }
            }
        }
    }

    static class DeleteHospitalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod()) || "DELETE".equals(t.getRequestMethod())) {
                try {
                    int id = getRequestId(t);
                    Connection conn = DatabaseManager.getConnection();
                    // Delete associated requests first
                    PreparedStatement p1 = conn.prepareStatement("DELETE FROM Blood_Requests WHERE hospital_id=?");
                    p1.setInt(1, id); p1.executeUpdate(); p1.close();
                    
                    // Delete associated user account
                    PreparedStatement p2 = conn.prepareStatement("DELETE FROM Users WHERE hospital_id=?");
                    p2.setInt(1, id); p2.executeUpdate(); p2.close();
                    
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM Hospitals WHERE hospital_id=?");
                    ps.setInt(1, id); int rows = ps.executeUpdate(); ps.close();
                    logActivity("DELETE_HOSPITAL", "Hospital #" + id + " deleted", "Admin");
                    sendJson(t, "{\"success\":" + (rows > 0) + "}", 200);
                } catch (Exception e) { sendJson(t, "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "'").replace("\n", " ").replace("\\", "\\\\") : "Unknown Error") + "\"}", 500); }
            }
        }
    }

    // ===== NEW: Activity Log =====
    static class ActivityLogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                List<String> logs = new ArrayList<>();
                try {
                    Connection conn = DatabaseManager.getConnection();
                    Statement s = conn.createStatement();
                    ResultSet rs = s.executeQuery("SELECT * FROM Activity_Log ORDER BY log_id DESC LIMIT 50");
                    while (rs.next()) {
                        String desc = nvl(rs.getString("description")).replace("\"", "'");
                        logs.add(String.format("{\"id\":%d,\"action\":\"%s\",\"description\":\"%s\",\"performed_by\":\"%s\",\"timestamp\":\"%s\"}",
                            rs.getInt("log_id"), nvl(rs.getString("action")), desc,
                            nvl(rs.getString("performed_by")), nvl(rs.getString("timestamp"))));
                    }
                    rs.close(); s.close();
                } catch (Exception e) { e.printStackTrace(); }
                sendJson(t, "[" + String.join(",", logs) + "]", 200);
            }
        }
    }

    // ===== NEW: Eligibility Check =====
    static class EligibilityCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                int donorId = Integer.parseInt(extractNum(body, "donor_id"));
                try {
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement("SELECT name, blood_group, last_donation_date FROM Donors WHERE donor_id=?");
                    ps.setInt(1, donorId);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) {
                        rs.close(); ps.close();
                        sendJson(t, "{\"error\":\"Donor not found\"}", 404);
                        return;
                    }
                    String name = rs.getString("name");
                    String bg = rs.getString("blood_group");
                    String lastDonation = rs.getString("last_donation_date");
                    rs.close(); ps.close();

                    if (lastDonation == null || lastDonation.isEmpty()) {
                        sendJson(t, "{\"eligible\":true,\"name\":\"" + name + "\",\"blood_group\":\"" + bg + "\",\"message\":\"No previous donation on record. Eligible to donate!\"}", 200);
                    } else {
                        PreparedStatement ec = conn.prepareStatement("SELECT CAST(julianday('now') - julianday(?) AS INTEGER) as days_since");
                        ec.setString(1, lastDonation);
                        ResultSet er = ec.executeQuery();
                        er.next();
                        int daysSince = er.getInt("days_since");
                        er.close(); ec.close();

                        boolean eligible = daysSince >= 56;
                        String nextDate = "";
                        if (!eligible) {
                            PreparedStatement nd = conn.prepareStatement("SELECT date(?, '+56 days') as next_date");
                            nd.setString(1, lastDonation);
                            ResultSet nr = nd.executeQuery();
                            if (nr.next()) nextDate = nr.getString("next_date");
                            nr.close(); nd.close();
                        }
                        String msg = eligible ? "Eligible! Last donated " + daysSince + " days ago." : "Not eligible. " + (56 - daysSince) + " days remaining. Next eligible: " + nextDate;
                        sendJson(t, "{\"eligible\":" + eligible + ",\"name\":\"" + name + "\",\"blood_group\":\"" + bg + "\",\"days_since\":" + daysSince + ",\"days_left\":" + Math.max(0, 56-daysSince) + ",\"next_date\":\"" + nextDate + "\",\"message\":\"" + msg + "\"}", 200);
                    }
                } catch (Exception e) { sendJson(t, "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "'").replace("\n", " ").replace("\\", "\\\\") : "Unknown Error") + "\"}", 500); }
            }
        }
    }

    // ===== NEW: Blood Compatibility =====
    static class CompatibilityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes());
                String patientBg = extractStr(body, "blood_group");
                
                // Blood compatibility matrix
                String[] compatible;
                switch (patientBg) {
                    case "A+": compatible = new String[]{"A+","A-","O+","O-"}; break;
                    case "A-": compatible = new String[]{"A-","O-"}; break;
                    case "B+": compatible = new String[]{"B+","B-","O+","O-"}; break;
                    case "B-": compatible = new String[]{"B-","O-"}; break;
                    case "AB+": compatible = new String[]{"A+","A-","B+","B-","AB+","AB-","O+","O-"}; break;
                    case "AB-": compatible = new String[]{"A-","B-","AB-","O-"}; break;
                    case "O+": compatible = new String[]{"O+","O-"}; break;
                    case "O-": compatible = new String[]{"O-"}; break;
                    default: compatible = new String[]{}; break;
                }

                List<String> results = new ArrayList<>();
                try {
                    Connection conn = DatabaseManager.getConnection();
                    for (String bg : compatible) {
                        PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(quantity_bags, 0) as qty FROM Blood_Inventory WHERE blood_group=?");
                        ps.setString(1, bg);
                        ResultSet rs = ps.executeQuery();
                        int qty = 0;
                        if (rs.next()) qty = rs.getInt("qty");
                        rs.close(); ps.close();
                        results.add(String.format("{\"blood_group\":\"%s\",\"quantity\":%d}", bg, qty));
                    }
                } catch (Exception e) { e.printStackTrace(); }
                sendJson(t, "{\"patient_group\":\"" + patientBg + "\",\"compatible\":[" + String.join(",", results) + "]}", 200);
            }
        }
    }

    private static void sendJson(HttpExchange t, String response, int code) throws IOException {
        byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        t.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        t.sendResponseHeaders(code, bytes.length);
        OutputStream os = t.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static int getRequestId(HttpExchange t) throws IOException {
        try {
            String body = new String(t.getRequestBody().readAllBytes());
            if (body.isEmpty()) return -1;
            return Integer.parseInt(extractNum(body, "id"));
        } catch (Exception e) {
            return -1;
        }
    }

    static class DeleteDonorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod()) || "DELETE".equals(t.getRequestMethod())) {
                try {
                    int id = getRequestId(t);
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement p1 = conn.prepareStatement("DELETE FROM Donation_Records WHERE donor_id=?");
                    p1.setInt(1, id); p1.executeUpdate(); p1.close();
                    PreparedStatement p2 = conn.prepareStatement("DELETE FROM Users WHERE donor_id=?");
                    p2.setInt(1, id); p2.executeUpdate(); p2.close();
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM Donors WHERE donor_id=?");
                    ps.setInt(1, id); int rows = ps.executeUpdate(); ps.close();
                    logActivity("DELETE_DONOR", "Donor #" + id + " deleted", "Admin");
                    sendJson(t, "{\"success\":" + (rows > 0) + "}", 200);
                } catch (Exception e) { sendJson(t, "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "'").replace("\n", " ").replace("\\", "\\\\") : "Unknown Error") + "\"}", 500); }
            }
        }
    }

    static class DeleteRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod()) || "DELETE".equals(t.getRequestMethod())) {
                try {
                    int id = getRequestId(t);
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM Blood_Requests WHERE request_id=?");
                    ps.setInt(1, id); int rows = ps.executeUpdate(); ps.close();
                    logActivity("DELETE_REQUEST", "Request #" + id + " deleted", "Admin");
                    sendJson(t, "{\"success\":" + (rows > 0) + "}", 200);
                } catch (Exception e) { sendJson(t, "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "'").replace("\n", " ").replace("\\", "\\\\") : "Unknown Error") + "\"}", 500); }
            }
        }
    }

    static class DeleteUserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod()) || "DELETE".equals(t.getRequestMethod())) {
                try {
                    int id = getRequestId(t);
                    Connection conn = DatabaseManager.getConnection();
                    PreparedStatement check = conn.prepareStatement("SELECT username FROM Users WHERE user_id=?");
                    check.setInt(1, id); ResultSet rc = check.executeQuery();
                    if(rc.next() && "admin".equalsIgnoreCase(rc.getString("username"))) {
                        rc.close(); check.close();
                        sendJson(t, "{\"error\":\"Cannot delete default admin\"}", 403);
                        return;
                    }
                    rc.close(); check.close();
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM Users WHERE user_id=?");
                    ps.setInt(1, id); int rows = ps.executeUpdate(); ps.close();
                    logActivity("DELETE_USER", "User #" + id + " deleted", "Admin");
                    sendJson(t, "{\"success\":" + (rows > 0) + "}", 200);
                } catch (Exception e) { sendJson(t, "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "'").replace("\n", " ").replace("\\", "\\\\") : "Unknown Error") + "\"}", 500); }
            }
        }
    }

    private static String extractNum(String json, String key) {
        try {
            String search = "\"" + key + "\":";
            int start = json.indexOf(search);
            if (start == -1) return "0";
            start += search.length();
            while (start < json.length() && !Character.isDigit(json.charAt(start))) start++;
            int end = start;
            while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
            if (start == end) return "0";
            return json.substring(start, end);
        } catch (Exception e) { return "0"; }
    }

    private static String extractStr(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}

