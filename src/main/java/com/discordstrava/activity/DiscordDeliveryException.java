package com.discordstrava.activity;

/** Classified Discord send failures; callers never need to inspect JDA exceptions. */
public final class DiscordDeliveryException extends RuntimeException {
    public enum Kind { MEMBER_GONE, CHANNEL_UNAVAILABLE, TRANSIENT }
    private final Kind kind;

    public DiscordDeliveryException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
