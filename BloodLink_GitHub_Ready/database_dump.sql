-- Blood Bank Management System - Database Export
-- Generated on: 2026-03-11

-- TABLE: Donors
-- Data currently in system:
INSERT INTO Donors (donor_id, name, blood_group, contact_number, email, address) VALUES (4, 'testuser', 'A+', '1234567890', 'test@example.com', 'TVM');

-- TABLE: Blood_Inventory
INSERT INTO Blood_Inventory (blood_group, quantity_bags) VALUES ('A+', 7);

-- TABLE: Hospitals
INSERT INTO Hospitals (hospital_id, name) VALUES (1, 'RCC, TVM');
INSERT INTO Hospitals (hospital_id, name) VALUES (2, 'MISSION HOSPITAL, TVM');

-- TABLE: Blood_Requests
INSERT INTO Blood_Requests (request_id, hospital_id, blood_group_required, quantity_needed, request_date, status) VALUES (2, 1, 'A+', 7, '2026-03-09', 'Approved');
INSERT INTO Blood_Requests (request_id, hospital_id, blood_group_required, quantity_needed, request_date, status) VALUES (3, 2, 'A+', 10, '2026-03-09', 'Pending (Low Stock)');

-- TABLE: Donation_Records
INSERT INTO Donation_Records (donation_id, donor_id, donation_date, quantity) VALUES (4, 4, '2026-03-09', 4);

-- TABLE: Users
INSERT INTO Users (user_id, username, password_hash, role, donor_id) VALUES (1, 'admin', '240be518...', 'Admin', NULL);
INSERT INTO Users (user_id, username, password_hash, role, donor_id) VALUES (6, 'testuser', '03ac6742...', 'Donor', 4);
