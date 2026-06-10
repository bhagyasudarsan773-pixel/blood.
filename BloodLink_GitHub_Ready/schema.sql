CREATE TABLE IF NOT EXISTS Donors (
    donor_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    blood_group TEXT NOT NULL,
    contact_number TEXT,
    email TEXT,
    address TEXT,
    last_donation_date DATE
);

CREATE TABLE IF NOT EXISTS Blood_Inventory (
    blood_group TEXT PRIMARY KEY,
    quantity_bags INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS Hospitals (
    hospital_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    contact_number TEXT,
    address TEXT
);

CREATE TABLE IF NOT EXISTS Requesters (
    requester_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    blood_group TEXT,
    contact_number TEXT,
    email TEXT,
    address TEXT
);

CREATE TABLE IF NOT EXISTS Blood_Requests (
    request_id INTEGER PRIMARY KEY AUTOINCREMENT,
    hospital_id INTEGER,
    requester_id INTEGER,
    blood_group_required TEXT NOT NULL,
    quantity_needed INTEGER NOT NULL,
    request_date DATE,
    status TEXT,
    is_emergency INTEGER DEFAULT 0,
    FOREIGN KEY (hospital_id) REFERENCES Hospitals(hospital_id),
    FOREIGN KEY (requester_id) REFERENCES Requesters(requester_id)
);

CREATE TABLE IF NOT EXISTS Donation_Records (
    donation_id INTEGER PRIMARY KEY AUTOINCREMENT,
    donor_id INTEGER,
    donation_date DATE,
    quantity INTEGER,
    health_status_notes TEXT,
    FOREIGN KEY (donor_id) REFERENCES Donors(donor_id)
);

CREATE TABLE IF NOT EXISTS Users (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT DEFAULT 'Admin',
    donor_id INTEGER,
    hospital_id INTEGER,
    requester_id INTEGER,
    FOREIGN KEY (donor_id) REFERENCES Donors(donor_id),
    FOREIGN KEY (hospital_id) REFERENCES Hospitals(hospital_id),
    FOREIGN KEY (requester_id) REFERENCES Requesters(requester_id)
);

CREATE TABLE IF NOT EXISTS Activity_Log (
    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
    action TEXT NOT NULL,
    description TEXT,
    performed_by TEXT DEFAULT 'System',
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT OR IGNORE INTO Users (username, password_hash) VALUES ('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9');
