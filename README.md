# GlucoSense Lab Backend

GlucoSense Lab Backend is a Spring Boot service for glucose sensor experiment management. It provides REST APIs for experiment batches, participants, experiment members, sensor profiles, wear records, competitor files, finger blood glucose data, daily experiment indicators, and CGM sensor data aggregation.

The service connects one main business database and three external sensor-related databases, then exposes unified APIs for management pages, data import/export, and visualization.

## Features

- JWT login, registration, current user lookup, and logout.
- BCrypt password hashing.
- Admin/User roles with read, write, and delete permissions for 10 business modules.
- Batch, participant, experiment, and experiment-member management.
- Sensor profile, sensor detail, and wear-record management.
- Conflict checks for active wear records.
- Competitor file upload, download, rename, delete, integrity check, and Excel export.
- Finger blood glucose CRUD, batch delete, filtering, and Excel export.
- Daily experiment data Excel import with MARD/PARD average calculation.
- CGM data aggregation across business, user, device, and sensor-data databases.
- Global exception handling and unified JSON error responses.

## Tech Stack

- Java 8
- Spring Boot 2.7.18
- Spring Web
- Spring JDBC
- MySQL Connector/J
- JJWT 0.11.5
- Spring Security Crypto
- Apache POI 5.2.5
- Maven

## Project Structure

```text
.
├── pom.xml
├── src/main/java/com/experimentms
│   ├── config/          # Web, CORS, and datasource configuration
│   ├── controller/      # REST API controllers
│   ├── exception/       # API exception and global handler
│   ├── security/        # JWT, authentication, and permission checks
│   ├── service/         # JDBC helper and activity log service
│   └── util/            # Payload, time, and map utilities
├── src/main/resources
│   └── application.yml
└── database_schemas/    # Optional SQL scripts for local initialization
```

## Database

The backend uses four MySQL databases:

| Database | Purpose |
| --- | --- |
| `experiment_manage` | Main business data: users, permissions, batches, persons, experiments, files, finger blood data, sensors, wear records, activity logs, and daily experiment data |
| `cloud_user_db` | External user lookup |
| `cloud_device_db` | External CGM device and sensor parameter data |
| `cloud_sensor_data_db` | External CGM sensor readings |

Initialize table structures:

```powershell
mysql -uroot -p < database_schemas\experiment_manage.sql
mysql -uroot -p < database_schemas\external_three_databases.sql
```

Optional demo data:

```powershell
mysql -uroot -p < database_schemas\simulated_data.sql
```

Demo data uses placeholder password hashes. For local development, either register a new user from the API or create an admin user manually:

```sql
USE experiment_manage;

INSERT INTO users (username, password_hash, role, createTime, updateTime)
VALUES (
  'admin',
  '$2b$12$dMsyzgd.zRm.rztTUkZ8neYtHFa1fUTyuMwkYRPP1t98enGpYZ3lu',
  'Admin',
  NOW(),
  NOW()
)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  role = 'Admin',
  updateTime = NOW();
```

Local account:

```text
username: admin
password: 123456
```

## Configuration

Configuration is stored in `src/main/resources/application.yml`.

| Environment Variable | Default | Description |
| --- | --- | --- |
| `DB_HOST` | `localhost` | Main database host |
| `DB_PORT` | `3306` | Main database port |
| `DB_NAME` | `experiment_manage` | Main database name |
| `DB_USER` | `root` | Main database username |
| `DB_PASSWORD` | `123456` | Main database password |
| `JWT_SECRET` | built-in local default | JWT signing secret |
| `JWT_EXPIRATION_MINUTES` | `30` | Token expiration in minutes |
| `UPLOADS_DIR` | `uploads` | Upload directory |
| `DOWNLOADS_DIR` | `downloads` | Download directory |
| `EXTERNAL_DB_HOST` | `localhost` | External database host |
| `EXTERNAL_DB_PORT` | `3306` | External database port |
| `EXTERNAL_DB_USER` | `root` | External database username |
| `EXTERNAL_DB_PASSWORD` | `123456` | External database password |
| `EXTERNAL_USER_DB` | `cloud_user_db` | External user database |
| `EXTERNAL_DEVICE_DB` | `cloud_device_db` | External device database |
| `EXTERNAL_SENSOR_DATA_DB` | `cloud_sensor_data_db` | External sensor-data database |

Default server port:

```text
8000
```

Default upload limit:

```text
20MB
```

## Run Locally

Build:

```powershell
mvn clean package -DskipTests
```

Run:

```powershell
java -jar target\experimentms-backend-1.0.0.jar
```

Health check:

```text
http://localhost:8000/health
```

Use a different port:

```powershell
java -jar target\experimentms-backend-1.0.0.jar --server.port=8001
```

## API Overview

| Module | Prefix | Description |
| --- | --- | --- |
| Auth and users | `/api/auth` | Login, registration, user management, and permission assignment |
| Batches | `/api/batches` | Experiment batch CRUD |
| Persons | `/api/persons` | Participant CRUD |
| Experiments | `/api/experiments` | Experiment and member management |
| Competitor files | `/api/competitorFiles` | File upload, download, rename, delete, integrity check, and export |
| Finger blood data | `/api/fingerBloodData` | Finger blood glucose CRUD, batch delete, and export |
| Sensors | `/api/sensors` | Sensor management |
| Sensor details | `/api/sensorDetails` | Sensor detail management, batch create, duplicate check, and batch delete |
| Wear records | `/api/wearRecords` | Wear-record management and active-record conflict checks |
| Experiment data | `/api/experimentData` | Daily MARD/PARD data import, query, and statistics |
| Sensor visualization | `/api/sensor-data` | CGM data aggregation, available dates, competitor data, finger blood data, and Excel download |
| Activities | `/api/activities` | Activity log query and creation |
| Dashboard stats | `/api/stats/dashboard` | Dashboard count statistics |

All `/api/**` endpoints except `/api/auth/login` and `/api/auth/register` require a JWT:

```text
Authorization: Bearer <access_token>
```

## Core Rules

- Batch numbers are unique.
- Sensor test numbers and probe numbers are unique.
- An experiment must have at least one member.
- The same sensor can have only one active wear record.
- The same person can have only one active wear record at the same wear position.
- Ending a wear record synchronizes the sensor end time and reason.
- Daily experiment data import creates or updates records by person and experiment day.
- CGM visualization queries sensor readings within the device wear-time window.

