# Banking Management System

A Java 25 and Spring Boot banking demo with a responsive browser UI, session-based authentication, account management, and a transaction ledger. This is a learning project, not a real banking service. Do not enter real credentials or financial information.

## Features

- Customer registration, login, logout, and profile editing
- BCrypt password hashing and server-side session authentication
- CSRF protection for state-changing requests
- Customer-owned account creation, listing, updates, and closure
- Deposits, withdrawals, and transfers recorded in a transaction history
- Transactional balance changes with pessimistic account locking
- Dashboard balances, cash-flow summaries, and recent transactions
- Input validation, consistent API error responses, and ownership checks
- GitHub Actions Maven test workflow

## Technologies

- Java 25 (LTS)
- Spring Boot 3.5
- Spring MVC, Spring Security, Spring Data JPA, and Jakarta Bean Validation
- H2 for local development; MySQL is available through the `mysql` profile
- Maven, HTML, CSS, and vanilla JavaScript

## Architecture

```text
Browser (static HTML/CSS/JavaScript)
  -> REST controllers (/api/**)
  -> services (business rules and transaction boundaries)
  -> Spring Data repositories
  -> JPA entities (users, accounts, transactions)
  -> H2 by default, or MySQL with the mysql profile
```

Controllers handle HTTP and authentication context, services enforce account ownership and banking rules, and repositories persist the `User`, `Account`, and `Transaction` entities. The browser and REST API are served by the same Spring Boot application.

## Database configuration

The default profile uses an in-memory H2 database. It requires no database server or credentials, and its data is cleared when the application stops. Hibernate uses `ddl-auto=update`; the application does not drop or recreate the default database schema.

For a local MySQL database, create a database named `banking_app`, then set the following environment variables in PowerShell before starting the application:

```powershell
$env:SPRING_PROFILES_ACTIVE = "mysql"
$env:DB_URL = "jdbc:mysql://localhost:3306/banking_app?serverTimezone=UTC"
$env:DB_USERNAME = Read-Host "MySQL username"
$env:DB_PASSWORD = Read-Host "MySQL password"
```

The MySQL profile requires credentials from the environment and does not provide a default password.

## Prerequisites

- JDK 25 or newer (`java --version`)
- Maven 3.9 or newer (`mvn --version`)
- For the optional MySQL profile: a local MySQL server and an existing `banking_app` database

## Install, test, and run on Windows

From the project directory:

```powershell
mvn clean test
mvn spring-boot:run
```

Open `http://localhost:8080`. Register a customer, create an account, and use the deposit, withdrawal, and transfer pages. The default H2 database is in-memory.

To package and run the executable JAR:

```powershell
mvn clean package
java -jar .\target\banking-management-system-1.0.0.jar
```

## REST API

All endpoints are served from `http://localhost:8080`. Requests with a JSON body use `Content-Type: application/json`.

| Method | Endpoint | Purpose | Access |
|---|---|---|---|
| GET | `/api/auth/csrf` | Start a session and retrieve a CSRF token | Public |
| POST | `/api/auth/register` | Register a customer | Public, CSRF token required |
| POST | `/api/auth/login` | Authenticate and start a session | Public, CSRF token required |
| POST | `/api/auth/logout` | End the current session | Authenticated, CSRF token required |
| GET / PUT | `/api/auth/me` | Read or update the current profile | Authenticated |
| GET / POST | `/api/accounts` | List accounts or create an account | Authenticated |
| GET / PUT / DELETE | `/api/accounts/{id}` | View, update, or close an account | Owner or admin |
| POST | `/api/accounts/{id}/deposit` | Deposit to an account | Owner or admin |
| POST | `/api/accounts/{id}/withdraw` | Withdraw from an account | Owner or admin |
| POST | `/api/transfers` | Transfer between accounts | Authenticated |
| GET | `/api/transactions` | List the current user's transactions | Authenticated |
| GET | `/api/transactions/{id}` | View one transaction | Owner, participant, or admin |
| GET | `/api/dashboard/summary` | Retrieve account and cash-flow totals | Authenticated |
| GET | `/api/dashboard/monthly-summary` | Retrieve the last six monthly summaries | Authenticated |
| GET | `/api/dashboard/recent-transactions` | Retrieve the ten most recent transactions | Authenticated |
| GET | `/api/admin/overview`, `/api/admin/users`, `/api/admin/accounts` | Admin summaries and lists | Admin |

### Postman quick start

1. Send `GET http://localhost:8080/api/auth/csrf`. Keep Postman's cookie jar enabled for `localhost` and copy the response's `token`.
2. For every `POST`, `PUT`, or `DELETE`, include `X-CSRF-TOKEN: <token>` and keep the session cookie. Registration and login both require the token.
3. Register with:

   ```json
   {
     "username": "demo.user",
     "email": "demo@example.com",
     "password": "change-this-demo-password",
     "firstName": "Demo",
     "lastName": "User"
   }
   ```

4. Log in using the same username and password. Fetch `/api/auth/csrf` again after login, then use the new token and the same session cookie for later state-changing requests.
5. Create an account with `POST /api/accounts` and body `{"accountType":"SAVINGS","balance":0}`. Use its returned `id` for deposit or withdrawal requests, for example `POST /api/accounts/{id}/deposit` with `{"amount":25.00,"description":"Demo deposit"}`.
6. To transfer, send `POST /api/transfers` with `{"sourceAccountId":1,"targetAccountId":2,"amount":5.00,"description":"Demo transfer"}` using account IDs from your local database.
7. Check `GET /api/accounts`, `GET /api/transactions`, and `GET /api/dashboard/summary`, then send `POST /api/auth/logout`.

Use invented local test data only. Admin endpoints require an account whose role is provisioned as `ADMIN`; public registration always creates a customer.

## Tests

Run the full test suite with:

```powershell
mvn clean test
```

GitHub Actions runs `mvn --batch-mode test` on pushes and pull requests.
