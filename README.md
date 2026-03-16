### ⚙️ **Backend – Expensely-BE (Java + PostgreSQL)**

# Expensely – Backend

This is the backend service for the Expensely expense tracking application. Built with **Java (Spring Boot)** and **PostgreSQL**, it provides REST APIs to manage user authentication, expense operations, and category analytics. Link to front end repository [[Link](https://github.com/n-saji/Expensely-FE)]

## 🔧 Features

- 🧾 Expense CRUD operations
- 🧑 User registration & JWT-based login
- 📈 Category-wise and date-wise aggregations
- 🔒 Secure endpoints and role-based protection
- 🌐 CORS enabled for frontend integration

## 💻 Tech Stack

- **Language:** Java
- **Framework:** Spring Boot
- **Database:** PostgreSQL
- **ORM**: Hibernate / JPA
- **Authentication**: JWT
- **Build Tool**: Maven

## 📦 Features

- User registration & login
- JWT-based authentication
- Expense CRUD operations
- Expense filtering by category/date
- API versioning and validation
- Secure error handling

## Email Verification (OTP)

- `POST /api/users/register` sends a 6-digit OTP to the user email.
- `POST /api/users/verify-otp` verifies the OTP and marks the user as verified.
- `POST /api/users/resend-otp` resends the OTP with a 2-minute cooldown.
- Unverified users receive `403` with `email not verified` for authenticated endpoints (including refresh).
