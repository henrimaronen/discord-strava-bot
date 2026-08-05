# Discord Strava Bot

Discord Strava Bot automatically shares completed Strava activities in a Discord server. Members privately connect their own Strava accounts, and the bot posts a simple activity summary, that includes the sport, distance, duration, and completion time.

Server admins choose where announcements appear. Members remain in control of their own connection and can check its status or disconnect at any time.


## Local run

Copy `.env.example` to `.env`, fill in the values, then load it before starting the app.

```sh
cp .env.example .env
set -a; source .env; set +a
mise exec java@21 -- gradle bootRun
```

Use a local PostgreSQL `DATABASE_URL` when developing locally.

`PUBLIC_BASE_URL` needs an HTTPS
tunnel URL for a real Strava OAuth or webhook test.

## Commands

`/help` privately lists the available commands.

`/connect`, `/status`, and `/unlink` manage a member's own Strava connection.

Members with **Manage Server** can use `/configure channel` and
`/configure disable` to manage activity announcements.

Run tests with Gradle 8.12+ and Java 21:

```sh
gradle test
```

Build the production image:

```sh
docker build -t discord-strava-bot .
```

Tests require Docker: the Flyway integration test runs a real PostgreSQL container and fails if Docker is unavailable, so CI must provide a Docker daemon.

Railway-style `postgresql://` URLs are accepted; encoded username/password values are decoded and supplied to the JDBC driver.

No test calls Discord or Strava.
