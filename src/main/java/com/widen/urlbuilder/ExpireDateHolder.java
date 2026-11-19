package com.widen.urlbuilder;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * The expiry of a signed URL: either a duration measured from the moment the URL is generated, or a fixed instant.
 * Setting one replaces the other.
 */
class ExpireDateHolder
{
    private long duration;

    private TimeUnit unit;

    private Instant instant;

    void expireIn(long duration, TimeUnit unit)
    {
        if (duration <= 0)
        {
            throw new IllegalArgumentException("duration must be positive");
        }
        InternalUtils.checkNotNull(unit, "unit");

        this.duration = duration;
        this.unit = unit;
        this.instant = null;
    }

    void expireAt(Instant instant)
    {
        InternalUtils.checkNotNull(instant, "instant");

        this.instant = instant;
        this.unit = null;
    }

    boolean isSet()
    {
        return instant != null || unit != null;
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

        if (unit == null)
        {
            return null;
        }

        return Instant.ofEpochMilli(unit.toMillis(duration) + System.currentTimeMillis());
    }
}
