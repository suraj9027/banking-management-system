# NorthBank Banking Management System

A portfolio banking application built with Java 21, Spring Boot, Spring Security, JPA, MySQL, and a responsive Bootstrap interface.

> **Portfolio/demo project only.** Do not use real banking credentials, payment information, or personal financial data. This application is not a real bank and has not been audited or hardened for production financial services.

## Features

- Customer registration and session-based login with BCrypt password hashing
- Account creation and account details
- Deposits, withdrawals, and account-to-account transfers
- Transaction history and dashboard summary
- H2 for local development and MySQL for persistent deployment

## Run locally

Requirements: Java 21 or newer and Maven 3.9+.

```powershell
mvn spring-boot:run
```

If Maven is not installed or `mvn` is not on PATH, use the Maven executable available on your machine:

```powershell
& "C:\path\to\apache-maven\bin\mvn.cmd" spring-boot:run
```

Open `http://localhost:8080`, register a customer, and log in. The default H2 database is in-memory and is cleared when the application stops.

Run the automated tests with:

```powershell
mvn test
```

## Deploy a persistent demo on Railway

The repository includes a Dockerfile for Railway. Use Railway's managed MySQL service so user and account data are not kept in the app's temporary filesystem.

1. Create a **public** GitHub repository and push this project to it.
2. In Railway, create a project and deploy the GitHub repository as a service. Railway will build the included `Dockerfile`.
3. Add a Railway **MySQL** service to the same project.
4. In the application service's Variables, add:

   ```text
   SPRING_PROFILES_ACTIVE=mysql
   DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?sslMode=REQUIRED&serverTimezone=UTC
   DB_USERNAME=${{MySQL.MYSQLUSER}}
   DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
   ```

   If the database service has a different name than `MySQL`, choose that service's variables from Railway's reference picker. Never put database passwords in GitHub files.

5. Redeploy the application service and use Railway's **Generate Domain** option to get a public HTTPS URL.
6. Test registration, login, account creation, and a demo transaction using invented test data. Add the Railway domain to your resume alongside the GitHub repository.

The MySQL deployment profile disables the H2 Console. The local default profile uses in-memory H2; Railway uses MySQL and retains data across app restarts.

## GitHub Actions

The workflow in `.github/workflows/maven.yml` runs the Maven test suite on pushes and pull requests.
