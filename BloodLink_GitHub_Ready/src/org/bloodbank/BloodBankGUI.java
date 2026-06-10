package org.bloodbank;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class BloodBankGUI extends JFrame {

    public BloodBankGUI() {
        DatabaseManager.initializeDatabase();
        if (!showLoginDialog()) {
            System.exit(0);
        }

        setTitle("Blood Bank Management System | Admin Dashboard");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); 

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tabbedPane.addTab("📊 Dashboard", createInventoryPanel());
        tabbedPane.addTab("👥 Donors", createDonorPanel());
        tabbedPane.addTab("💉 New Donation", createDonationPanel());
        tabbedPane.addTab("🏥 Requests", createRequestPanel());
        
        add(tabbedPane);
    }

    private boolean showLoginDialog() {
        JPanel loginPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        loginPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JTextField userField = new JTextField();
        JPasswordField passField = new JPasswordField();
        
        loginPanel.add(new JLabel("Username:")); loginPanel.add(userField);
        loginPanel.add(new JLabel("Password:")); loginPanel.add(passField);

        int result = JOptionPane.showConfirmDialog(null, loginPanel, "Admin Login", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String user = userField.getText();
            String pass = new String(passField.getPassword());
            return AuthUtils.authenticate(user, pass);
        }
        return false;
    }

    private JPanel createDonorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel inputPanel = new JPanel(new GridLayout(6, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField nameField = new JTextField();
        JComboBox<String> bgField = new JComboBox<>(new String[]{"O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-"});
        JTextField contactField = new JTextField();
        JTextField emailField = new JTextField();
        JTextField addressField = new JTextField();
        JButton addButton = new JButton("Add Donor");

        inputPanel.add(new JLabel("Donor Name:")); inputPanel.add(nameField);
        inputPanel.add(new JLabel("Blood Group:")); inputPanel.add(bgField);
        inputPanel.add(new JLabel("Contact Number:")); inputPanel.add(contactField);
        inputPanel.add(new JLabel("Email Address:")); inputPanel.add(emailField);
        inputPanel.add(new JLabel("Home Address:")); inputPanel.add(addressField);
        inputPanel.add(new JLabel("")); inputPanel.add(addButton);

        String[] cols = {"ID", "Name", "Blood Group", "Contact", "Email", "Address"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);
        
        Runnable loadDonors = () -> {
            model.setRowCount(0);
            try (Connection conn = DatabaseManager.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM Donors")) {
                while(rs.next()) {
                    model.addRow(new Object[]{
                        rs.getInt("donor_id"), rs.getString("name"), rs.getString("blood_group"), 
                        rs.getString("contact_number"), rs.getString("email"), rs.getString("address")
                    });
                }
            } catch (Exception e) { e.printStackTrace(); }
        };

        addButton.addActionListener(e -> {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("INSERT INTO Donors(name, blood_group, contact_number, email, address) VALUES(?,?,?,?,?)")) {
                pstmt.setString(1, nameField.getText());
                pstmt.setString(2, (String)bgField.getSelectedItem());
                pstmt.setString(3, contactField.getText());
                pstmt.setString(4, emailField.getText());
                pstmt.setString(5, addressField.getText());
                pstmt.executeUpdate();
                JOptionPane.showMessageDialog(this, "Donor Added Successfully!");
                nameField.setText(""); contactField.setText(""); emailField.setText(""); addressField.setText("");
                loadDonors.run();
            } catch (Exception ex) { 
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); 
            }
        });

        loadDonors.run();
        panel.add(inputPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        JButton refreshBtn = new JButton("Refresh Donor List");
        JButton deleteBtn = new JButton("Delete Selected Donor");
        deleteBtn.setForeground(Color.RED);

        refreshBtn.addActionListener(e -> loadDonors.run());
        deleteBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Please select a donor from the table.");
                return;
            }
            int id = (int) model.getValueAt(row, 0);
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete donor ID: " + id + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try (Connection conn = DatabaseManager.getConnection()) {
                    conn.setAutoCommit(false);
                    // Delete donations first
                    try (PreparedStatement ps1 = conn.prepareStatement("DELETE FROM Donation_Records WHERE donor_id = ?")) {
                        ps1.setInt(1, id); ps1.executeUpdate();
                    }
                    // Delete user account if exists
                    try (PreparedStatement ps2 = conn.prepareStatement("DELETE FROM Users WHERE donor_id = ?")) {
                        ps2.setInt(1, id); ps2.executeUpdate();
                    }
                    // Delete donor
                    try (PreparedStatement ps3 = conn.prepareStatement("DELETE FROM Donors WHERE donor_id = ?")) {
                        ps3.setInt(1, id); ps3.executeUpdate();
                    }
                    conn.commit();
                    JOptionPane.showMessageDialog(this, "Donor deleted successfully.");
                    loadDonors.run();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error deleting donor: " + ex.getMessage());
                }
            }
        });

        buttonPanel.add(refreshBtn);
        buttonPanel.add(deleteBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createDonationPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JTextField donorIdField = new JTextField();
        JTextField quantityField = new JTextField();
        JButton donateBtn = new JButton("Record Donation");

        panel.add(new JLabel("Enter Donor ID:")); panel.add(donorIdField);
        panel.add(new JLabel("Quantity Donated (in bags):")); panel.add(quantityField);
        panel.add(new JLabel("")); panel.add(donateBtn);

        donateBtn.addActionListener(e -> {
            try {
                int donorId = Integer.parseInt(donorIdField.getText());
                int quantity = Integer.parseInt(quantityField.getText());
                
                String userBg = null;
                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement("SELECT blood_group FROM Donors WHERE donor_id = ?")) {
                    pstmt.setInt(1, donorId);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) userBg = rs.getString("blood_group");
                }
                
                if (userBg == null) {
                    JOptionPane.showMessageDialog(this, "Error: Donor ID not found.");
                    return;
                }

                Connection conn = DatabaseManager.getConnection();
                conn.setAutoCommit(false);
                try {
                    // Insert Donation
                    PreparedStatement insertDnt = conn.prepareStatement("INSERT INTO Donation_Records(donor_id, donation_date, quantity) VALUES(?, date('now'), ?)");
                    insertDnt.setInt(1, donorId); insertDnt.setInt(2, quantity);
                    insertDnt.executeUpdate();

                    // Update Inventory
                    PreparedStatement checkInv = conn.prepareStatement("SELECT quantity_bags FROM Blood_Inventory WHERE blood_group = ?");
                    checkInv.setString(1, userBg);
                    ResultSet rsInv = checkInv.executeQuery();
                    if (rsInv.next()) {
                        PreparedStatement updateInv = conn.prepareStatement("UPDATE Blood_Inventory SET quantity_bags = quantity_bags + ? WHERE blood_group = ?");
                        updateInv.setInt(1, quantity); updateInv.setString(2, userBg); updateInv.executeUpdate();
                    } else {
                        PreparedStatement insertInv = conn.prepareStatement("INSERT INTO Blood_Inventory(blood_group, quantity_bags) VALUES(?, ?)");
                        insertInv.setString(1, userBg); insertInv.setInt(2, quantity); insertInv.executeUpdate();
                    }
                    conn.commit();
                    JOptionPane.showMessageDialog(this, "Donation Recorded & Inventory Updated for " + userBg + "!");
                    donorIdField.setText(""); quantityField.setText("");
                } catch(Exception ex) {
                    conn.rollback();
                    JOptionPane.showMessageDialog(this, "Database Error: " + ex.getMessage());
                } finally {
                    conn.setAutoCommit(true);
                    conn.close();
                }

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Please enter valid numbers for ID and Quantity.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Database Error: " + ex.getMessage());
            }
        });
        
        JPanel container = new JPanel(new BorderLayout());
        container.add(panel, BorderLayout.NORTH);
        return container;
    }

    private JPanel createInventoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"Blood Group", "Available Bags"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setFont(new Font("Arial", Font.PLAIN, 14));

        Runnable loadInventory = () -> {
            model.setRowCount(0);
            try (Connection conn = DatabaseManager.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM Blood_Inventory ORDER BY blood_group")) {
                while(rs.next()) {
                    model.addRow(new Object[]{rs.getString("blood_group"), rs.getInt("quantity_bags")});
                }
            } catch (Exception e) { e.printStackTrace(); }
        };

        JButton refreshBtn = new JButton("Refresh Inventory");
        refreshBtn.addActionListener(e -> loadInventory.run());

        loadInventory.run();
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(refreshBtn, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createRequestPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel inputPanel = new JPanel(new GridLayout(5, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTextField hospField = new JTextField();
        JComboBox<String> bgField = new JComboBox<>(new String[]{"O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-"});
        JTextField qtyField = new JTextField();
        JButton reqButton = new JButton("Submit Blood Request");

        inputPanel.add(new JLabel("Hospital Name:")); inputPanel.add(hospField);
        inputPanel.add(new JLabel("Blood Group Needed:")); inputPanel.add(bgField);
        inputPanel.add(new JLabel("Quantity (bags):")); inputPanel.add(qtyField);
        inputPanel.add(new JLabel("")); inputPanel.add(reqButton);

        String[] cols = {"Req ID", "Hospital", "Group", "Quantity", "Date", "Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);

        Runnable loadRequests = () -> {
            model.setRowCount(0);
            String query = "SELECT r.request_id, h.name, r.blood_group_required, r.quantity_needed, r.request_date, r.status " +
                           "FROM Blood_Requests r JOIN Hospitals h ON r.hospital_id = h.hospital_id ORDER BY r.request_id DESC";
            try (Connection conn = DatabaseManager.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while(rs.next()) {
                    model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getString(6)});
                }
            } catch (Exception e) { e.printStackTrace(); }
        };

        reqButton.addActionListener(e -> {
            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                String hospName = hospField.getText().trim();
                String bg = (String)bgField.getSelectedItem();
                int qty = Integer.parseInt(qtyField.getText());

                if (hospName.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please enter a Hospital Name.");
                    return;
                }

                // 1. Get or Create Hospital
                PreparedStatement checkHosp = conn.prepareStatement("SELECT hospital_id FROM Hospitals WHERE name = ?");
                checkHosp.setString(1, hospName);
                ResultSet rsHosp = checkHosp.executeQuery();
                int hospId = -1;
                if (rsHosp.next()) {
                    hospId = rsHosp.getInt(1);
                } else {
                    PreparedStatement insHosp = conn.prepareStatement("INSERT INTO Hospitals(name) VALUES(?)", Statement.RETURN_GENERATED_KEYS);
                    insHosp.setString(1, hospName);
                    insHosp.executeUpdate();
                    ResultSet keys = insHosp.getGeneratedKeys();
                    if(keys.next()) hospId = keys.getInt(1);
                }

                // 2. Check Inventory Availability
                PreparedStatement checkInv = conn.prepareStatement("SELECT quantity_bags FROM Blood_Inventory WHERE blood_group = ?");
                checkInv.setString(1, bg);
                ResultSet rsInv = checkInv.executeQuery();
                int available = 0;
                if (rsInv.next()) available = rsInv.getInt(1);

                String status = (available >= qty) ? "Approved" : "Pending (Not Enough)";

                // 3. Insert Request
                PreparedStatement insReq = conn.prepareStatement("INSERT INTO Blood_Requests(hospital_id, blood_group_required, quantity_needed, request_date, status) VALUES(?,?,?,date('now'),?)");
                insReq.setInt(1, hospId);
                insReq.setString(2, bg);
                insReq.setInt(3, qty);
                insReq.setString(4, status);
                insReq.executeUpdate();

                // 4. Update Inventory if Approved
                if (available >= qty) {
                    PreparedStatement updInv = conn.prepareStatement("UPDATE Blood_Inventory SET quantity_bags = quantity_bags - ? WHERE blood_group = ?");
                    updInv.setInt(1, qty);
                    updInv.setString(2, bg);
                    updInv.executeUpdate();
                }

                conn.commit();
                conn.setAutoCommit(true);
                JOptionPane.showMessageDialog(this, "Request Processed: " + status);
                hospField.setText(""); qtyField.setText("");
                loadRequests.run();
                
            } catch(NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Quantity must be a valid number.");
            } catch(Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });

        loadRequests.run();
        panel.add(inputPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        JButton refreshBtn = new JButton("Refresh Requests");
        JButton deleteBtn = new JButton("Delete Selected Request");
        deleteBtn.setForeground(Color.RED);

        refreshBtn.addActionListener(e -> loadRequests.run());
        deleteBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Please select a request from the table.");
                return;
            }
            int id = (int) model.getValueAt(row, 0);
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete request ID: " + id + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement("DELETE FROM Blood_Requests WHERE request_id = ?")) {
                    pstmt.setInt(1, id);
                    pstmt.executeUpdate();
                    JOptionPane.showMessageDialog(this, "Request deleted successfully.");
                    loadRequests.run();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error deleting request: " + ex.getMessage());
                }
            }
        });

        buttonPanel.add(refreshBtn);
        buttonPanel.add(deleteBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }
}
