package com.discordstrava.connection;

/** A database uniqueness conflict proving that a different member owns this Athlete. */
public final class AthleteAlreadyLinkedException extends RuntimeException {
    public AthleteAlreadyLinkedException(Throwable cause) {
        super(cause);
    }
}
