# Mobile phase 1 backend contract

The Expo client calls this Spring Boot API directly. A test deployment needs PostgreSQL, Redis, the existing email and S3 configuration, and a reachable HTTPS URL. The mobile auth endpoints are additive; web cookie login and refresh remain available.

## Native session

`POST /api/mobile/auth/login`

```json
{"identifier":"person@example.com","password":"example-password"}
```

`identifier` can be an email address or the stored phone number. Only active users with verified email can sign in. Invalid credentials return `401` with `code: INVALID_CREDENTIALS`; inactive or unverified accounts return `403` with `code: ACCOUNT_INACTIVE` or `EMAIL_UNVERIFIED`.

`POST /api/mobile/auth/refresh`

```json
{"refreshToken":"<current mobile refresh JWT>"}
```

Both successful calls return:

```json
{
  "accessToken": "<mobile access JWT>",
  "refreshToken": "<mobile refresh JWT>",
  "accessTokenExpiresAt": "2026-11-21T12:15:00Z",
  "refreshTokenExpiresAt": "2026-11-28T12:00:00Z",
  "sessionId": "<UUID>"
}
```

Access tokens last 15 minutes. Refresh tokens last seven days and rotate atomically in Redis. Reusing an old refresh token revokes that mobile session. The server stores only a SHA-256 hash of the current refresh token. A revoked or expired credential returns `401` with `code: INVALID_SESSION`. The client should coordinate concurrent refreshes and retry a protected request once.

Authenticated native requests send `Authorization: Bearer <accessToken>`. `POST /api/mobile/auth/logout` sends the same header and an empty body, and returns `204`. Protected endpoints reject a refresh token in place of an access token. Account deletion and admin session revocation also invalidate mobile sessions.

Web requests continue using `accessToken` and `refreshToken` cookies. Protected cookie requests require matching valid JWTs and an active Redis session. New web JWTs have explicit access and refresh purposes; already issued untyped web JWTs remain accepted until expiry. The web OAuth account-link request must include `token` containing the provider's ID token (Google) or access token (GitHub/Discord).
Web session listing returns opaque SHA-256 handles instead of refresh credentials; the existing `/api/users/sessions/id/{id}` deletion route accepts those handles.
API request logs redact authorization and cookie headers, sensitive query strings, and authentication request/response bodies.

## Existing mobile read shapes

- `GET /api/users/me` returns `UserRes` with `user` and `error` fields.
- `GET /api/categories/user` returns the user's category array.
- `GET /api/transactions/user/{userId}/fetch-with-conditions?page=1` returns `transactions`, `totalPages`, `totalElements`, and `pageNumber`. Pagination is one-based.

The authenticated principal must match `{userId}`. Category, transaction, budget, recurring expense, file, export, profile, and alert operations enforce ownership; admin-only operations check the stored user role. Financial creation uses the requested transaction currency when supplied, otherwise the user's preferred currency.

## Verification and test data

Run `./gradlew test` for the backend suite. It covers token purpose, mobile login/refresh/logout, Redis rotation behavior, Bearer and web cookie authentication, ownership checks, and existing service behavior. The tests use H2 and mocked Redis. Live request/response captures and iOS/Android device checks still require a reachable test API seeded with two ordinary verified users and one admin. Provider verification requires configured Google, GitHub, and Discord OAuth application IDs and GitHub client secret; external provider calls are not exercised by the unit suite.
