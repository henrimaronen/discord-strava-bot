# Discord Strava Bot

Java 21 service: JDA Gateway client plus Spring MVC HTTP server and PostgreSQL/Flyway.

## Required environment

`DATABASE_URL`, `DISCORD_TOKEN`, `DISCORD_GUILD_ID`, `STRAVA_CLIENT_ID`,
`STRAVA_CLIENT_SECRET`, `STRAVA_WEBHOOK_VERIFY_TOKEN`, `OAUTH_TOKEN_ENCRYPTION_KEY`, and
`PUBLIC_BASE_URL` are required at startup. `PUBLIC_BASE_URL` must be HTTPS; Railway sets `PORT`
automatically. Never commit their values.

`GET /healthz` is public and returns only `{ "status": "ok" }`.

## Local run

Copy `.env.example` to `.env`, fill in the values, then load it before starting the app. `.env` is
gitignored and must never be committed.

```sh
cp .env.example .env
set -a; source .env; set +a
mise exec java@21 -- gradle bootRun
```

Use a local PostgreSQL `DATABASE_URL` when developing locally. `PUBLIC_BASE_URL` needs an HTTPS
tunnel URL for a real Strava OAuth or webhook test.

## Commands

Run tests with Gradle 8.12+ and Java 21:

```sh
gradle test
```

Build the production image:

```sh
docker build -t discord-strava-bot .
```

Tests require Docker: the Flyway integration test runs a real PostgreSQL container and fails if
Docker is unavailable, so CI must provide a Docker daemon. Railway-style `postgresql://` URLs are
accepted; encoded username/password values are decoded and supplied to the JDBC driver. No test
calls Discord or Strava.
