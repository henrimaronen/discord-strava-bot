# Next user-facing features

The bot already connects one athlete, receives a completed activity, and posts one card to one channel. The card shows the Discord name, activity name, sport, distance, moving time, heart rate, suffer score, description, device, and a link to Strava. That path is in `AnnounceStravaActivity` and `JdaActivityAnnouncements`.

These three ideas are for the people in that channel. Scores are a judgment of usefulness over a normal month in one club, from 1 to 10. They are not measurements.

`docs/activity-announcement-surface.md` describes an older card. The heart-rate, description, and device fields landed later, in the change that became pull request 13. Read those two types for what the card shows today.

## How the scores were chosen

A 9 means the channel can show a workout that is no longer true, including one the athlete has hidden. An 8 means every new post is harder to read than the workout itself. A 7 means one athlete's future posts stop, and the bot never tells them.

Issue 7, the production MVP spec, left activity edits, deletions, images, and elevation out of scope. Pull request 13 already moved past the minimum card by adding description, heart rate, suffer score, and device. The ranking below follows what a reader and an athlete notice now.

## Keep each announcement matched to the activity

**Score.** 9.

**Who notices.** Anyone reading the channel, and the athlete who edits or hides a workout.

Strava tells the bot about three activity events. The bot acts on `create` only. `StravaWebhookController` ignores every other aspect before it is stored, and `AnnounceStravaActivity` documents that non-create events have no effects. `activity_delivery` stores the activity id and a delivery state. It does not store the Discord message id. After the card is sent, nothing in the database can find that message again.

Two things follow. An athlete who renames a run, or adds the description the card is already able to show, leaves the old card in Discord. An athlete who deletes the activity, or makes it visible only to themselves, leaves that card in the channel. `docs/activity-announcement-surface.md` records the Strava rule that an `activity:read` subscription receives a delete when an activity becomes Only You. The row is also purged after seven days, so even a later fix could not edit a message whose id was never stored.

The build plan is `docs/how-to-sync-activity-posts.md`.

## Show the workout photo and the pace

**Score.** 8.

**Who notices.** Everyone who reads a new announcement.

The fetch in `HttpStravaActivityClient` drops fields the same response already contains. The useful ones for a reader are the primary photo, average speed, and total elevation gain. The card never shows a picture. Distance and moving time are on the card, so a reader has to divide them to get pace. A yoga session or a ride on a trainer still prints `0.00 km`, which looks like a broken post.

This is the smaller change. It is still second. A photo on a card that never updates, or on an activity the athlete has hidden, makes the wrong post more convincing. Build the sync first, then add photo, pace, and elevation to the same projection the sync edits. Do not request `activity:read_all`. Do not put start or end coordinates on the card.

## Tell the athlete when Strava must be reconnected

**Score.** 7.

**Who notices.** The athlete whose token Strava has rejected. On a bad day this is the only post that matters, because every later workout is missing.

`AnnounceStravaActivity` marks the connection `RECONNECT_NEEDED` when a token refresh is rejected, discards the delivery, and writes a log line. The member hears about it only by running `/status`. The status text already says to use `/connect`. Nothing sends that text on its own.

`ConnectStrava.authorizationFailed` is named for a Strava deauthorization, and production never calls it. The webhook controller ignores an athlete update, so deauthorization leaves the connection `ACTIVE` until a later refresh fails. The private message follows the `RECONNECT_NEEDED` transition. It does not fire when that webhook arrives.

When the state first changes to `RECONNECT_NEEDED`, send one private Discord message to that member with the same Connect Strava button `/connect` already uses. Do not send it again while the connection stays in that state. This does not fix a channel the bot can no longer see. That failure disables announcements and logs `announcement_channel_unavailable`, and the admin is also not told. Leave the admin notice for a later change.

## What not to build next

A second Discord server, a leaderboard, and a backfill of old activities are real products. They are not the next gap in the loop this bot already runs. The guild id is a single setting, and issue 7 already excluded those three.
