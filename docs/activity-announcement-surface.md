# Activity announcement surface

What Strava will give us for a Completed activity, and what Discord will let an Activity announcement look like. Written so we can pick spice without guessing.

## Current bot surface

OAuth requests and verifies `activity:read` only. Not `activity:read_all`. (`ConnectStrava`, `HttpStravaOAuthClient`.)

Fetch is `GET https://www.strava.com/api/v3/activities/{id}` with the Athlete's Bearer token (`HttpStravaActivityClient`). The adapter then keeps only `id`, `name`, `sport_type`, `distance`, `moving_time`, `start_date`. The comment in that file is explicit: it "intentionally projects only announcement-safe fields."

Discord send is JDA 5.3.0. `JdaActivityAnnouncements` builds one embed:

- title = Discord member effective name
- url = `https://www.strava.com/activities/{id}`
- fields: Activity, Sport, Distance, Moving time, When (`<t:epoch:F>`)

No color, description, thumbnail, image, footer, author, or components. The webhook payload is only ids (`ActivityWebhook`); the Activity itself is always fetched afterward.

## Strava: what GET activity returns

`GET /activities/{id}` returns a DetailedActivity. Scope: `activity:read` for Everyone and Followers activities; `activity:read_all` for Only Me. Optional query `include_all_efforts` adds all segment efforts — not needed for an announcement.

`type` is deprecated. Prefer `sport_type` (this bot already does). `sport_type` is an enum including values like `HighIntensityIntervalTraining`, `TrailRun`, `MountainBikeRide` — that is why the current card shows concatenated CamelCase.

Fields below are from the official DetailedActivity model unless marked as sample-only. Availability assumes this bot's `activity:read` token and an Activity that is not Only You.

| Field | Useful for an announcement? | With `activity:read` | Notes |
| --- | --- | --- | --- |
| `id`, `name`, `sport_type` | Already used | Yes | Name is athlete-chosen; sport is raw enum |
| `distance`, `moving_time`, `start_date` | Already used | Yes | Distance is metres |
| `elapsed_time` | Total clock time vs moving | Yes | Indoor/HIIT often has distance 0; elapsed still useful |
| `start_date_local`, `timezone` | Local "when" | Yes | Discord timestamps already localize for the reader |
| `total_elevation_gain`, `elev_high`, `elev_low` | Climb stats | Yes | Often 0 for indoor |
| `average_speed`, `max_speed` | Pace/speed | Yes | m/s; derive pace for runs |
| `description` | Athlete caption | Yes | May be empty |
| `calories` | Energy | Yes | May be estimated |
| `kudos_count`, `comment_count`, `achievement_count` | Social proof | Yes | At create time these are usually 0 |
| `athlete_count` | Group activity | Yes | >1 means others tagged |
| `photo_count`, `total_photo_count` | Whether photos exist | Yes | Counts only |
| `photos` (`PhotosSummary`) | Primary photo URLs | Yes | Sample includes `primary.urls["100"]` and `["600"]` CloudFront JPEGs |
| `gear` (`SummaryGear.name`) | Bike/shoes | Yes | Null if unset |
| `device_name` | Watch/head unit | Yes | Strava asks apps to attribute Garmin devices |
| `map.summary_polyline` / `map.polyline` | Route image | Yes, but encoded | Google encoded polyline, not a picture. Privacy-zone data is excluded under `activity:read` |
| `start_latlng`, `end_latlng` | Start/end pins | Unsafe | Coordinates. Privacy-zone data excluded under `activity:read`. Do not announce |
| `private` | Visibility | Yes | `true` means Only You; this scope should not receive those as creates |
| `trainer`, `commute`, `manual` | Indoor / commute / typed-in | Yes | Explains 0 km HIIT |
| `workout_type` | Run/ride subtype | Yes | Integer; poorly named vs Activity |
| `kilojoules`, `average_watts`, `max_watts`, `weighted_average_watts`, `device_watts` | Power | Yes | Documented as rides only |
| `segment_efforts`, `laps`, `splits_metric`, `best_efforts` | Deep stats | Yes | Too dense for a glance card; `include_all_efforts` expands segments |
| `embed_token` | Official Strava embed | Yes | For Strava's own embed widget, not Discord |
| `average_cadence`, `average_temp`, `has_heartrate`, `suffer_score`, `pr_count` | Extra stats | Sample-only | Present in official sample JSON; **not listed on the DetailedActivity table** |
| `average_heartrate`, `max_heartrate` | HR | Sample-only on list | Official List Athlete Activities sample includes them when `has_heartrate` is true. Not on the DetailedActivity table |

### Scope and privacy

- `activity:read`: Activities visible to Everyone or Followers. **Excludes privacy zone data.** Required for activity webhooks.
- `activity:read_all`: Same, plus privacy zones, plus Only You activities.
- With only `activity:read`, Strava sends a `delete` webhook if visibility becomes Only You, and a `create` if it becomes public/followers. The API agreement requires respecting Activity privacy.
- Athletes may uncheck scopes on the consent screen. This bot already verifies `activity:read`.

Widening to `activity:read_all` is a product decision (announce private Activities, show privacy-zone maps), not a formatting decision.

## Strava: extra endpoints

| Endpoint | What it adds | Same `activity:read`? | Extra request? |
| --- | --- | --- | --- |
| `GET /activities/{id}/comments` | Comment text | Yes (Everyone/Followers) | Yes |
| `GET /activities/{id}/kudos` | Who kudoed (`SummaryAthlete`) | Yes | Yes |
| `GET /activities/{id}/streams?keys=&key_by_type=true` | Time series: latlng, altitude, heartrate, cadence, watts, etc. | Yes (`activity:read_all` for Only Me) | Yes. Needed to draw a real map/HR sparkline ourselves |
| `GET /athlete` | Profile photo (`profile`, `profile_medium`), name | Summary without `profile:read_all` | Yes. This bot does not request `profile:read_all` |
| `GET /athlete/activities` | Recent Activities (SummaryActivity) | Yes; Only Me filtered out | Yes. Sample includes HR. Useful for "vs last run", not for the create webhook itself |
| `GET /athletes/{id}/stats` | Totals | Separate | Yes |

Default non-upload rate limit is 100 requests / 15 min and 1,000 / day per application (higher after Strava review). One extra GET per announcement is cheap; streams + photos + athlete on every create is how you burn the budget. Webhooks already exist so we should not poll.

There is no separate "get photos" endpoint in the reference. Primary photo URLs come on DetailedActivity.

## Discord: how the card can be edited

The current "card" is a rich embed. Create Message allows `content`, up to 10 embeds, `components`, attachments (25 MiB), stickers, polls, and `allowed_mentions`. At least one of content/embeds/components/files/poll is required.

### Embed fields that would change this card

Bots set every embed field except `type` (always `rich`), `provider`, `video`, and image `height`/`width`/`proxy_url`.

| Embed field | Visible effect | JDA 5.3.0 |
| --- | --- | --- |
| `title` | Bold header (now: Discord nick) | `setTitle` |
| `url` | Makes title a link (now: Strava Activity) | `setUrl` or `setTitle(title, url)` |
| `description` | Body text under the title. Markdown. Best place for a cheer line or Activity name | `setDescription` |
| `color` | Left accent bar (integer RGB) | `setColor` |
| `timestamp` | Small datetime in the footer corner | `setTimestamp` |
| `footer.text` + `footer.icon_url` | Bottom caption (device, "via Strava") | `setFooter` |
| `author.name` + `icon_url` + `url` | Top row with avatar (Discord or Strava face) | `setAuthor` |
| `thumbnail` | Small image top-right | `setThumbnail` |
| `image` | Large image below fields (photo or rendered map) | `setImage` |
| `fields[]` | Up to 25 name/value rows; `inline: true` packs three per row (current Sport/Distance/Moving time) | `addField` |

Limits (inclusive, whitespace trimmed): title 256, description 4096, 25 fields, field name 256, field value 1024, footer 2048, author name 256. Combined title+description+fields+footer+author across all embeds on one message ≤ 6000. Embeds with the same URL are deduplicated.

Inline fields: Discord shows up to three across. Long values like `HighIntensityIntervalTraining` crush the row — that is a formatting bug, not a missing API.

### Beside the embed

- **Message `content`**: up to 2000 chars above the embed. Mentions (`<@id>`), emoji, timestamps. Use `allowed_mentions` so a cheer does not ping @everyone.
- **Timestamps**: `<t:UNIX:F>` is already used. Styles: `t` `T` `d` `D` `f` `F` `s` `S` `R` (relative, e.g. "2 hours ago"). Seconds, reader's locale.
- **Emoji**: unicode in any string field. Custom emoji needs `<:name:id>` and the bot in that Discord server.
- **Link button** (component type 2, style 5): URL, no `custom_id`, **does not send an interaction**. Label ≤ 80 chars. Must sit in an Action Row (max 5 buttons per row). This bot already uses `Button.link` on `/connect`. A "Open on Strava" button is free.
- **Interactive buttons** (styles 1–4): need `custom_id` and an interaction listener. This bot has none for announcements.
- **Components V2** (`IS_COMPONENTS_V2` flag): richer layout (sections, containers, galleries) but **disables `content` and `embeds`**. Different product, not a tweak to the current card.
- **Attachments**: upload a rendered map PNG and set embed `image.url` to `attachment://map.png`.
- **Author/thumbnail from Discord**: `Member` already loaded in `JdaActivityAnnouncements`; avatar URL needs no Strava call.

## Option matrix

| If we want | Need from Strava | Need on Discord |
| --- | --- | --- |
| Readable sport ("HIIT" not `HighIntensityIntervalTraining`) | `sport_type` we already have | Field value rewrite; optional emoji |
| Color by sport | `sport_type` | `color` |
| Cheer / caption line | Optional `description`; otherwise copy we write | `description` or `content` |
| Show Activity name as title, member as author | Member (already) | Swap `title` / `author` |
| Discord avatar | Nothing | `author.icon_url` or `thumbnail` from `Member` |
| Strava profile photo | `GET /athlete` → `profile` | `author.icon_url` / `thumbnail` |
| Elevation, pace, calories, elapsed, indoor | Same GET, unused fields | More `fields` or description lines |
| Primary workout photo | Same GET `photos.primary.urls` | `image` or `thumbnail` |
| "Open on Strava" button | Activity id (already) | `Button.link` on the send (not only embed url) |
| Device / Garmin credit | `device_name` | `footer` |
| Gear name | `gear.name` | Field or footer |
| Route preview | `map.summary_polyline` plus a renderer we do not have; or streams `latlng` | Attachment + `setImage` |
| Heart-rate number | Sample-only on payloads; streams for a chart | Field or generated image |
| Compare to last Activity | `GET /athlete/activities` | Extra field |
| Announce Only You Activities | `activity:read_all` + reconnect every Athlete | Same card |
| Exact start map pin | `start_latlng` — **don't**; privacy zones | — |

## Non-options / traps

- **The webhook is not a stats payload.** Spice cannot come from the create event itself.
- **Throwing away the GET body is a product choice**, not an API limit. Elevation, calories, photos, map polyline, description, and gear are already in the response we fetch.
- **`activity:read` will not announce Only You Activities** and strips privacy-zone map data. Do not treat missing polyline as "Strava has no map."
- **Do not post `start_latlng` / `end_latlng`.** That is location of an Athlete.
- **Kudos/comments at webhook time are usually zero.** Fetching `/kudos` on create is wasted.
- **Polyline is not an image.** Showing a route means rendering (external static-map service or our own). Extra dependency and rate/cost.
- **Components V2 cannot keep the current embed.** Link buttons on the existing embed are the small step.
- **Interactive buttons need new bot architecture** (component listener, persistence). Link buttons do not.
- **`type` vs `sport_type`:** keep `sport_type`. HIIT exists only there.
- **Heart rate is not on the DetailedActivity schema table.** Relying on it means accepting sample-only fields or a streams call.
- **Strava rate limits** are per application. Extra GETs on every announcement compete with token refresh and retries.
- **Garmin attribution** is encouraged when `device_name` is a Garmin device.
- **Embed URL dedupe:** two embeds with the same Strava URL on one message collapse to one.

## Sources

- [Strava API reference](https://developers.strava.com/docs/reference/) — getActivityById, DetailedActivity, SportType, PhotosSummary, PolylineMap, getActivityStreams, getKudoersByActivityId, getLoggedInAthlete, getLoggedInAthleteActivities
- [Strava authentication / scopes](https://developers.strava.com/docs/authentication/)
- [Strava webhooks](https://developers.strava.com/docs/webhooks/)
- [Strava rate limits](https://developers.strava.com/docs/rate-limits/)
- [Discord embed object and limits](https://discord.com/developers/docs/resources/message#embed-object)
- [Discord create message](https://discord.com/developers/docs/resources/message#create-message)
- [Discord message formatting / timestamps](https://discord.com/developers/docs/reference#message-formatting)
- [Discord components (buttons, Components V2)](https://discord.com/developers/docs/components/reference)
- [JDA EmbedBuilder](https://docs.jda.wiki/net/dv8tion/jda/api/EmbedBuilder.html)
- This repo: `HttpStravaActivityClient`, `JdaActivityAnnouncements`, `DiscordActivityAnnouncement`, `ConnectStrava`, `HttpStravaOAuthClient`, `ActivityWebhook`
