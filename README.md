# Organization Service

**Backend**: Spring Boot (Java)

**Purpose**: Multi-tenant Organization Management service. Creates organizations, admin users and dynamic tenant collections in MongoDB. Implements JWT admin login and tenant-scoped employee CRUD (role-based access control).

---

## How It Works (Architecture)

```mermaid
graph TD
  %% Clients & Edge
  A[Clients<br/>(Web / Mobile / Postman)] -->|HTTP| B[API Gateway / Load Balancer]

  %% Application
  B --> C[Organization Service<br/>(Spring Boot)]
  subgraph AppComponents
    C1[Controllers] 
    C2[JWT Filter / Auth]
    C3[OrgService & TenantService]
    C4[Repositories]
    C5[Postman / Tests]
  end
  C --> C1
  C1 --> C2
  C1 --> C3
  C3 --> C4

  %% Databases
  C -->|master metadata queries| M[MongoDB - Master DB<br/>(master_organizations,<br/>master_admins)]
  C -->|tenant queries| T[MongoDB - Tenant Collections<br/>(org_<name>, seeded template)]

  %% Optional / infra
  B --> D[Monitoring & Logs<br/>(Prometheus / Grafana)]
  C --> E[Optional: Redis / Cache]
  C --> F[Optional: Object Storage (S3)]

  %% Notes
  classDef infra fill:#f9f9f9,stroke:#ddd;
  class M,T,D,E,F infra;
```

---

## Getting Started

### Clone the Repository

```bash
git clone https://github.com/your-username/organization-service.git
cd organization-service
```

### Prerequisites

Make sure you have the following installed:

* Java 17+ (JDK 17 recommended)
* Maven 3.6+
* MongoDB (local) or Docker (to run MongoDB container)
* (Optional) Postman for API testing

Check versions:

```bash
java -version
mvn -v
mongosh --version   # if using mongosh
```

---

## Quick overview

* Master DB (`org_master_db`) stores global metadata:

  * `master_organizations`
  * `master_admins`

* For each organization created the service programmatically creates a tenant collection named `org_<sanitized_org_name>` and seeds it with a basic schema template and an `admin_profile` document.

* Authentication: Admin login returns a JWT token with claims: `sub` (adminId), `organization` and `role`.

* Tenant APIs use the token's `organization` claim to route requests to the appropriate tenant collection.

---

## Prerequisites

Make sure you have the following installed:

* Java 17+ (JDK 17 recommended)
* Maven 3.6+
* MongoDB (local) or Docker (to run MongoDB container)
* (Optional) Postman for API testing

Check versions:

```bash
java -version
mvn -v
mongosh --version   # if using mongosh
```

---

## Project structure (important files)

```
pom.xml
src/main/java/com/example/organizationservice/
  ├─ controller/
  ├─ dto/
  ├─ model/
  ├─ repository/
  ├─ security/
  ├─ service/
src/main/resources/application.properties
README.md
postman/OrganizationService.postman_collection.json (optional)
```

---

## Configuration

Edit `src/main/resources/application.properties` to match your environment. Example configuration used in this project:

```properties
# MongoDB - use URI style (Spring Boot 3+)
spring.data.mongodb.uri=mongodb://localhost:27017/org_master_db

# JWT secret - keep safe in production
app.jwt.secret=bdf89a41e923c77e2cd9f7b123aa64ff913cbb87d2f11c4efae8e7d334b09da2
app.jwt.expiration-ms=3600000

# Prevent default Spring Security in-memory user creation
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration

# Optional: change server port (default 8080)
# server.port=8081
```
---

## Run MongoDB

### Local MongoDB

Start your local MongoDB service (platform-specific). The application will create the `org_master_db` automatically when it writes data.


## Build & Run (development)

From project root:

```bash
# build
mvn clean package

# run
mvn spring-boot:run
```

---

## API Endpoints (summary)

> Base URL: `http://localhost:8080` (or `{{baseUrl}}` if you use Postman environment)

### Organization & Admin (master)

* `POST /org/create`

  * Body JSON: `{ "organization_name": "Microsoft", "email": "admin@microsoft.com", "password": "Admin@1234" }`
  * Creates organization metadata, admin in `master_admins`, tenant collection `org_<name>` seeded with template and admin_profile.

* `GET /org/get?organization_name=<name>`

  * Fetches organization metadata from master DB.

* `PUT /org/update`

  * Body JSON: `{ "organization_name": "OldName", "new_organization_name": "NewName" }`
  * Renames organization, copies tenant collection data (and inserts template if empty), updates admin references and metadata.

* `DELETE /org/delete?organization_name=<name>`

  * Header: `Authorization: Bearer <JWT_TOKEN>` (Admin only; token must contain matching organization)
  * Deletes tenant collection, admin(s) and metadata.

* `POST /admin/login`

  * Body JSON: `{ "email": "admin@microsoft.com", "password": "Admin@1234" }`
  * Returns `{ "token": "<JWT>" }`.

### Tenant (employee CRUD) — multi-tenant routing by JWT claim

All tenant endpoints require `Authorization: Bearer <JWT>` where JWT contains the `organization` claim.

* `POST /tenant/employees` — create employee

  * Body JSON: `{ "name":"Alice","email":"alice@example.com","position":"SE","salary":120000 }`

* `GET /tenant/employees` — list employees

* `GET /tenant/employees/{id}` — get single employee

* `PUT /tenant/employees/{id}` — update employee

* `DELETE /tenant/employees/{id}` — delete employee (requires role `ADMIN` in token)

---

## Postman

Import the provided `postman/OrganizationService.postman_collection.json`. Create an environment `Local` with variables:

```
baseUrl=http://localhost:8080
token_microsoft=
employeeId=
```

Flow to test:

1. `01 - Create Organization (Microsoft)`
2. `02 - Admin Login (Microsoft)` (this request auto-saves `token_microsoft`)
3. `03 - Create Employee` (uses Bearer `{{token_microsoft}}`)
4. `04 - List Employees` (auto-saves `employeeId`)
5. `05 - Get Employee` / `06 - Update Employee` / `07 - Delete Employee`

---

## Useful commands (quick reference)

```bash
# build
mvn clean package

# run
mvn spring-boot:run

# run on other port
mvn spring-boot:run -Dserver.port=8081

# build jar and run
java -jar target/organization-service-0.0.1-SNAPSHOT.jar

# run Mongo local via Docker
docker run -d --name org-mongo -p 27017:27017 mongo:6

# test login, save token (requires jq)
TOKEN=$(curl -s -X POST http://localhost:8080/admin/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@microsoft.com","password":"Admin@1234"}' | jq -r .token)

# use token
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/tenant/employees
```

---

## License

This project is licensed under the MIT License.