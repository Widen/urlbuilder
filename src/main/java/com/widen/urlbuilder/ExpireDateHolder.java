package com.widen.urlbuilder;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

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

    void expireIn(long duration, TimeUnit unit)
    {
        InternalUtils.checkNotNull(unit, "unit");
        if (duration <= 0)
        {
            throw new IllegalArgumentException("duration must be positive");
        }

        // Converted through whole seconds: TimeUnit.toNanos stops at Long.MAX_VALUE nanoseconds, about 292 years.
        long seconds = unit.toSeconds(duration);
        if (seconds == Long.MAX_VALUE)
        {
            // TimeUnit saturates instead of overflowing, and Long.MAX_VALUE seconds is past Instant.MAX anyway
            throw new IllegalArgumentException("duration is too long: " + duration + " " + unit);
        }
        long remainder = duration - unit.convert(seconds, TimeUnit.SECONDS);
        expireIn(Duration.ofSeconds(seconds, unit.toNanos(remainder)));
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
        return getExpireDate(Instant.now());
    }

    /**
     * @param now the moment a duration counts from
     * @return the expiry, computed from {@code now} when it is a duration, or null when no expiry is set
     * @throws IllegalArgumentException if {@code now} plus the duration is past {@link Instant#MAX}
     */
    Instant getExpireDate(Instant now)
    {
        if (instant != null)
        {
            return instant;
        }

        if (duration == null)
        {
            return null;
        }

        try
        {
            return now.plus(duration);
        }
        catch (ArithmeticException | DateTimeException e)
        {
            throw new IllegalArgumentException("expiration is past " + Instant.MAX + ": " + duration, e);
        }
    }
}
