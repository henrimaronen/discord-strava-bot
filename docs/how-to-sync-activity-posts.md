# Sync an activity announcement with Strava

Build this so a sent card stays equal to the activity the athlete is willing to share. An update edits that card. A delete removes it. A delete that arrives before the card is sent stops the card from being sent.

Leave photo, pace, elevation, and the reconnect message for later. They are ranked in `docs/next-user-features.md`.

## The record you are changing

One `activity_delivery` row is one activity. The row is already the idempotency boundary. Extend that row. Do not add a second queue.

Add these fields.

- `discord_channel_id`. The channel the card was sent to. A later `/configure channel` must not move an old card.
- `discord_message_id`. Null until Discord accepts the message.
- `refresh_pending`. True when Strava has reported an update that Discord has not applied yet.

Add one state, `REMOVED`. Use it after the bot deletes the Discord message, and when a delete arrives before a send. A late create for that activity id must not post.

Rows that are already `SENT` have no message id. An update or delete for those rows does nothing. Do not backfill them.

The existing purge deletes rows older than seven days. After that, the bot cannot find the message. Edits and deletes inside seven days work. Later ones do not. Keep that limit.

## Store the message before you acknowledge Strava

Strava retries when the HTTP handler is slow or fails. Do the database write in the request. Do the Discord call after the response, on the webhook executor, as create already does.

1. In `StravaWebhookController.receive`, accept `update` and `delete` when `object_type` is `activity` and the subscription id matches. Keep rejecting every other event. Check the subscription id for all three aspects.
2. On `update`, if a row exists and it is not `REMOVED`, set `refresh_pending`. Return 200. If no row exists, return 200 and do nothing. The create has not been stored, or this activity was never announced.
3. On `delete`, if the athlete has no connection, return 200 and do nothing. If no row exists, insert `REMOVED` with a null message id. If a row exists, set the state to `REMOVED` and keep any message id already stored. `REMOVED` with a message id means Discord still shows the card. Clear the message id only after Discord accepts the delete.
4. Schedule the Discord work the same way a queued create schedules `deliver`.

`deliver` must read the row again before it sends. If the state is `REMOVED`, it stops. That covers a delete that lands while the create fetch is in flight.

## Edit and delete the same card

`DiscordActivityAnnouncements.send` returns nothing, and `JdaActivityAnnouncements.send` discards the message `complete()` returns. Change the send boundary so the workflow receives the channel id and the message id. Write both in the same database update that marks the row sent.

`JdbcActivityDeliveryRepository` reclaims a `PROCESSING` row whose `processing_started_at` is at least 300 seconds old. If that reclaim finds a message id, it must edit or stop. It must not send a second card. A crash after Discord accepts the message and before that update can still post twice. That gap already exists today.

Add `edit` and `deleteMessage` on that same boundary. `edit` builds the embed the send path already builds, from a fresh `DiscordActivityAnnouncement`. `deleteMessage` deletes by channel id and message id. Discord lets a bot edit and delete its own messages with the permissions this bot already asks for, which are View Channel, Send Messages, and Embed Links. Confirm that on a test server before shipping. This claim is from the Discord permission model, not from a test in this repository.

When `refresh_pending` is set and the message id is present, fetch the activity again and `edit`. Then clear `refresh_pending`. If the fetch fails, leave the flag set and let the retry worker try for the same 24 hour window creates already use.

If an update arrives while the row is still `PENDING`, `RETRY`, or `PROCESSING`, the in-flight fetch may have read the old activity. Leave `refresh_pending` set. After `markSent`, if the flag is still set, edit once with a new fetch.

On delete, call `deleteMessage`, then set `REMOVED` and clear the message id. If Discord says the message is already gone, treat that as success.

`ActivityDeliveryRetryWorker` today calls `deliver` for due rows. Also select `SENT` rows with `refresh_pending`, and `REMOVED` rows that still have a message id. A crash after the 200 must still finish the edit or the delete.

Do not store the webhook body or the activity payload. The message id and the channel id are enough.

## Tests that should fail if this breaks

Change `ignores_nonCreatesInactiveConnectionsAndPreConnectionActivities`. An update is no longer an ignore once a card exists.

Assert these outcomes with the fakes already in `AnnounceStravaActivityTest`.

- A create still sends one card, and the stored message id is the one the fake Discord returned.
- An update after send fetches again and edits that message once. A second update with the same result edits again and does not send a second card.
- An update that arrives before send does not send twice. The card that lands matches the later fetch.
- A delete after send removes that message and does not leave a second one.
- A delete before send produces no card, including when the create was already claimed.
- A missing message id on an old `SENT` row ignores both update and delete.
- A changed announcement channel does not move the old card. The edit uses the stored channel id.

No test should call Discord or Strava.
