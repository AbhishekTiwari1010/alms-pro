# ⬆ ALMS Pro — Advanced Lift Management System v2.0

A fully redesigned, production-grade lift management platform.

---

## 🆕 What's New in v2.0 (vs previous version)

| Feature | v1 | v2 |
|---------|----|----|
| Multi-building support | ❌ | ✅ |
| Lift types (Standard/Express/VIP/Freight) | ❌ | ✅ |
| Emergency priority requests | ❌ | ✅ |
| Lift capacity tracking | ❌ | ✅ |
| Live lift shaft visualization | ❌ | ✅ |
| Saved routes with color/icon | ❌ | ✅ |
| Travel & wait time tracking | ❌ | ✅ |
| Maintenance reporting | ❌ | ✅ |
| Admin dashboard with stats | ❌ | ✅ |
| Top floors analytics | ❌ | ✅ |
| Auto-refresh lift status (5s) | ❌ | ✅ |
| Role: EMPLOYEE / ADMIN / SUPER_ADMIN | ❌ | ✅ |
| Dynamic dispatch algorithm | ✅ | ✅ (improved) |

---

## 🗂 Project Structure

```
alms-pro/
├── database/
│   └── schema.sql
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/alms/
│       ├── AlmsProApplication.java
│       ├── algorithm/   LiftAssignmentAlgorithm.java
│       ├── config/      (via SecurityConfig)
│       ├── controller/  Controllers.java
│       ├── dto/         PublicDtos.java
│       ├── model/       Building, User, Lift, LiftTrip, LiftQueue, SavedRoute, MaintenanceLog
│       ├── repository/  (7 repositories)
│       ├── security/    SecurityConfig.java (JwtUtil, JwtFilter, UserDetailsService)
│       └── service/     AuthService.java, LiftService.java
└── frontend/
    ├── index.html
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── main.jsx
        └── App.jsx      (complete SPA)
```

---

## ⚙️ Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Node.js | 18+ |

---

## 🗄️ Database Setup

```bash
mysql -u root -p < database/schema.sql
```

Creates:
- `alms_pro` database
- 7 tables with proper constraints
- 6 dynamic lifts (L-1 to L-6, types: STANDARD×3, EXPRESS×2, VIP×1)
- Default super admin: `admin@alms.com` / `Admin@123`

---

## 🛠️ Backend Setup

Edit `backend/src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/alms_pro?...
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD
```

```bash
cd backend
mvn clean install
mvn spring-boot:run
```
→ Runs on `http://localhost:8080`

---

## 💻 Frontend Setup

```bash
cd frontend
npm install
npm run dev
```
→ Runs on `http://localhost:3000`

---

## 🔐 API Reference

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register employee |
| POST | `/api/auth/login`    | Login, get JWT |

### User (Bearer token required)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/lift/request`       | Request a lift |
| POST | `/api/lift/complete`      | Mark trip done, free lift |
| GET  | `/api/lift/status`        | All 6 lift statuses |
| GET  | `/api/lift/trips`         | My trip history |
| POST | `/api/lift/routes`        | Create saved route |
| GET  | `/api/lift/routes`        | Get my routes |
| DELETE | `/api/lift/routes/{id}` | Delete route |
| POST | `/api/lift/maintenance/{liftId}` | Report issue |

### Admin (ADMIN/SUPER_ADMIN role)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET   | `/api/admin/dashboard`                   | Full stats |
| GET   | `/api/admin/trips`                        | All trips |
| GET   | `/api/admin/trips/employee/{employeeId}` | By employee |
| GET   | `/api/admin/lifts`                        | All lift statuses |
| PATCH | `/api/admin/lifts/{buildingId}/{liftNum}/maintenance?active=true` | Toggle maintenance |

---

## 🧠 Algorithm v2.0

```
BLOCK = floor range of size 10 (configurable via alms.block-size)
  floor  3 → block  1-10
  floor 45 → block 41-50

PRIORITY ORDER:
═══════════════════════════════════════════════════════════

EMERGENCY requests:
  → Skip sharing. Find nearest VIP/EXPRESS idle lift first.
  → Fall back to any idle lift.
  → Queue if all busy.

HIGH / NORMAL requests:
  1. SHARE  — Any BUSY lift on this block with spare capacity?
              → Pick least-loaded (lowest activeTripCount)
              → Increment activeTripCount + currentLoad

  2. DISPATCH — Any IDLE lift available?
              → Sort by |currentFloor - fromFloor| ascending
              → Nearest wins. Mark BUSY, assign block, count = 1

  3. QUEUE  — All 6 lifts BUSY (on any blocks)?
              → Store in lift_queue with 10-min expiry
              → processQueue() auto-runs on every trip completion

COMPLETION:
  - decrementTripCount & currentLoad
  - If activeTripCount == 0 → lift goes IDLE, block cleared
  - currentFloor updated to toFloor (improves future distance sort)
  - processQueue() drains pending requests

DYNAMIC EXAMPLE:
  60 requests for floor 45 (block 41-50):
  → Requests 1-6:  Each dispatches one idle lift to block 41-50
  → Requests 7-60: All SHARE existing lifts on block 41-50
  → All 6 lifts serving floor 45 simultaneously ✅
```

---

## 🎨 Frontend Features

### User Console
- **Auth** — Login / Register with full name, employee ID, floor
- **Live Lift Shafts** — Animated visualization showing each lift's real floor position
- **Lift Status Grid** — 6 tiles showing type, floor, status, load bar, block, trip count
- **Priority Selector** — NORMAL / HIGH / EMERGENCY
- **Saved Routes** — Create named routes with custom colors; quick-tap to select
- **Manual Request** — Enter any From/To floors directly
- **Assignment Result** — Large animated display showing lift number or queue position
- **Complete Trip Panel** — Free a lift when the trip is done
- **Trip History** — Full table with type, priority, status, timing

### Admin Panel
- **Dashboard** — 6 stat cards (trips today, all-time, active/idle/maintenance lifts, avg wait)
- **Live Lift Grid** — With maintenance toggle buttons per lift
- **Top Floors** — Bar chart of most-requested floors
- **Recent Trips** — Last 10 trips with employee name
- **All Trips** — Complete trip log with filtering
- **Employee Lookup** — Search all trips by Employee ID

---

## 🔑 Default Credentials

| Role | Email | Password |
|------|-------|----------|
| Super Admin | admin@alms.com | Admin@123 |

Register employees via the app UI.
