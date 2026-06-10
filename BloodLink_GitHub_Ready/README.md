# 🩸 BloodLink | Premium Blood Bank Management System
> **Final Year Project Documentation**

**BloodLink** is a comprehensive, full-stack management solution developed for healthcare facilities to manage blood inventory, donor relationships, and hospital requests in real-time. It features a high-performance Java backend and a modern, responsive web interface.

---

## 📋 Project Overview
Managing a blood bank requires high precision, security, and speed. BloodLink addresses these needs by centralizing data and providing an intuitive dashboard for administrators and a portal for donors.

### Core Objectives
- **Inventory Management**: Real-time tracking of blood units by group.
- **Donor Engagement**: Safe storage of donor history and automated eligibility tracking.
- **Request Coordination**: Streamlined approval process for hospital blood requests.
- **Security**: Role-based access control with SHA-256 password hashing.

---

## 🏗️ System Architecture

### Backend (Java)
- **High-Performance Server**: Uses `com.sun.net.httpserver` for a lightweight, dependency-free RESTful API.
- **Database Architecture**: SQLite relational database ensures ACID compliance.
- **Data Integrity**: Uses Transactional SQL to safe-guard inventory levels during updates.

### Frontend (Modern Web)
- **Glassmorphic Design**: A premium UI with dark mode, blur effects, and smooth CSS animations.
- **Dynamic Content**: JavaScript (ES6) handles all API interactions without page reloads.
- **Responsive Layout**: Optimized for desktop and widescreen dashboards.

### Database Design (ER Highlights)
- `Donors`: Stores personal info, blood group, and contact details.
- `Blood_Inventory`: Tracks unit counts per blood group.
- `Blood_Requests`: Manages hospital orders and their statuses (Pending/Approved).
- `Users`: Handles authentication for Admins and Donors.
- `Donation_Records`: Logs every historical donation contribution.

---

## 🌟 Key Features
- 📊 **Real-time Analytics**: Visualized stock levels and community stats.
- 🛡️ **Admin Control**: Full CRUD operations for donors, users, and requests.
- 💉 **Donor Portal**: Personal dashboards for donors to see their history.
- 🏥 **Request System**: Logic that automatically checks stock levels before approving requests.
- ⚡ **Instant Feedback**: Modern modal-based notifications for all user actions.

---

## 🚀 Presentation & Setup Guide

### 1. Prerequisites
- **JDK 11+**
- **SQLite JDBC Driver** (found in `lib/`)

### 2. Quick Setup
1. **Compile the System**:
   ```bash
   build.bat
   ```
2. **Seed Demonstration Data** (Crucial for presentation):
   ```bash
   seed.bat
   ```
3. **Launch the Server**:
   ```bash
   run.bat
   ```
4. **Access the App**:
   Navigate to `http://localhost:9090`

### 3. Login Credentials
| Role | Username | Password |
|---|---|---|
| **Administrator** | `admin` | `admin123` |
| **Sample Donor** | `john_doe` | (Register or check Seeder) |

---

## 📁 Project Structure
```text
BloodBankManagement/
├── src/                # Java Source Code
│   └── org/bloodbank/  # Main Logic & API Handlers
├── web/                # Frontend Assets (HTML, CSS, JS)
├── lib/                # Database Drivers
├── bin/                # Compiled Classes
├── bloodbank.db        # SQLite Database File
├── build.bat           # Compilation Shortcut
├── seed.bat           # Demo Data Seeder
└── run.bat             # Server Startup
```

---

## 🎓 Conclusion
This project demonstrates a professional-grade integration of backend efficiency and frontend aesthetics. It is designed to be scalable, maintainable, and ready for real-world deployment or academic evaluation.
