### ⚙️ **Backend – Expensely-BE (Java + PostgreSQL)**

# Expensely – Backend

This is the backend service for the Expensely expense tracking application. Built with **Java (
Spring Boot)** and **PostgreSQL**, it provides REST APIs to manage user authentication, expense
operations, and category analytics. Link to front end
repository [[Link](https://github.com/n-saji/Expensely-FE)]

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
# Local finance development

Start only the isolated PostgreSQL service with `docker compose -f docker-compose.finance.yml up -d finance-db`, then run `./scripts/run-local-finance.sh`. The script checks `127.0.0.1:55432` and sets `DB_URL` to the local `expensely_finance` database; Flyway applies the additive migration. It never reads the repository's `.env` file. Set the frontend's ignored `.env.local` to `NEXT_PUBLIC_API_URL=http://127.0.0.1:18080/api` and `NEXT_PUBLIC_WS_URL=ws://127.0.0.1:18080/ws`.

Existing income and expense transactions retain a null account link. Account opening balances represent the balance at adoption; historical transactions are not silently assigned to accounts. Legacy category rows remain readable, while new categories must be income or expense.
