# Strava Discord Bot

Shared language for a production MVP that announces a user's completed Strava workouts in a configured Discord channel.

## People and places

**Athlete**:
The Strava user who authorizes this bot to access their activities.
_Avoid_: user, account holder

**Discord server**:
The user's Discord community where the bot is installed.
_Avoid_: guild, workspace

**Announcement channel**:
The single configured Discord channel that receives workout notifications.
_Avoid_: target channel, feed

## Fitness events

**Activity**:
A workout recorded by an Athlete in Strava.
_Avoid_: workout record, exercise

**Completed activity**:
An Activity that Strava has recorded as finished and that qualifies for an announcement.
_Avoid_: completion event, finished workout

**Activity announcement**:
The Discord message sent to the Announcement channel for a Completed activity. It is stats-first: sport and Announcement stats, not celebratory copy.
_Avoid_: notification, post, alert, workout message, cheer line

**Announcement stats**:
The numbers shown on an Activity announcement, drawn from the Completed activity.
_Avoid_: extra stats, metrics dump
