package com.widen.urlbuilder;

import java.time.Duration;
import java.time.Instant;

/**
 * The expiry of a signed URL: either a duration measured from the moment the URL is generated, or a fixed instant.
 * Setting one replaces the other.
 */
class ExpireDateHolder
{
    private Duration duration;

    private Instant instant;

    void expireIn(Duration duration)
    {
        InternalUtils.checkNotNull(duration, "duration");
        if (duration.isZero() || duration.isNegative())
        {
            throw new IllegalArgumentException("duration must be positive");
        }

        this.duration = duration;
        this.instant = null;
    }

    void expireAt(Instant instant)
    {
        InternalUtils.checkNotNull(instant, "instant");

        this.instant = instant;
        this.duration = null;
    }

    boolean isSet()
    {
        return instant != null || duration != null;
    }

    /**
     * @return the expiry, computed from the current time when it is a duration, or null when no expiry is set
     */
    Instant getExpireDate()
    {
        if (instant != null)
        {
            return instant;
        }

        if (duration == null)
        {
            return null;
        }

        return Instant.now().plus(duration);
    }
}
