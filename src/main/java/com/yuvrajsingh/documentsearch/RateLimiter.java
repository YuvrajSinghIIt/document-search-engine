package com.yuvrajsingh.documentsearch;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Per-user token bucket, leaky bucket, and fixed-window admission control. */
public final class RateLimiter {
    public enum Algorithm {
        TOKEN_BUCKET,
        LEAKY_BUCKET,
        FIXED_WINDOW
    }

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final Algorithm algorithm;
    private final int capacity;
    private final double rateOrWindowSeconds;
    private final LongSupplier nanoClock;
    private final Map<String, TokenBucketState> tokenBuckets = new HashMap<>();
    private final Map<String, LeakyBucketState> leakyBuckets = new HashMap<>();
    private final Map<String, FixedWindowState> fixedWindows = new HashMap<>();

    /**
     * @param rateOrWindowSeconds refill/leak rate in requests per second for buckets, or window size in seconds for fixed windows
     */
    public RateLimiter(Algorithm algorithm, int capacity, double rateOrWindowSeconds) {
        this(algorithm, capacity, rateOrWindowSeconds, System::nanoTime);
    }

    RateLimiter(Algorithm algorithm, int capacity, double rateOrWindowSeconds, LongSupplier nanoClock) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm is required");
        if (capacity <= 0) {
            throw new IllegalArgumentException("Rate limiter capacity must be positive");
        }
        if (!Double.isFinite(rateOrWindowSeconds) || rateOrWindowSeconds <= 0) {
            throw new IllegalArgumentException("Rate or window duration must be positive and finite");
        }
        this.capacity = capacity;
        this.rateOrWindowSeconds = rateOrWindowSeconds;
        this.nanoClock = Objects.requireNonNull(nanoClock, "a clock provider is required");
    }

    public boolean allowRequest(String userId) {
        Objects.requireNonNull(userId, "userId is required");
        return switch (algorithm) {
            case TOKEN_BUCKET -> allowTokenBucket(userId);
            case LEAKY_BUCKET -> allowLeakyBucket(userId);
            case FIXED_WINDOW -> allowFixedWindow(userId);
        };
    }

    private boolean allowTokenBucket(String userId) {
        long now = nanoClock.getAsLong();
        TokenBucketState state = tokenBuckets.computeIfAbsent(userId,
                ignored -> new TokenBucketState(capacity, now));
        double elapsedSeconds = elapsedSeconds(now, state.lastUpdatedNanos);
        state.tokens = Math.min(capacity, state.tokens + elapsedSeconds * rateOrWindowSeconds);
        state.lastUpdatedNanos = now;

        if (state.tokens < 1.0) {
            return false;
        }
        state.tokens--;
        return true;
    }

    private boolean allowLeakyBucket(String userId) {
        long now = nanoClock.getAsLong();
        LeakyBucketState state = leakyBuckets.computeIfAbsent(userId,
                ignored -> new LeakyBucketState(0.0, now));
        double elapsedSeconds = elapsedSeconds(now, state.lastUpdatedNanos);
        state.level = Math.max(0.0, state.level - elapsedSeconds * rateOrWindowSeconds);
        state.lastUpdatedNanos = now;

        if (state.level + 1.0 > capacity) {
            return false;
        }
        state.level++;
        return true;
    }

    private boolean allowFixedWindow(String userId) {
        long now = nanoClock.getAsLong();
        FixedWindowState state = fixedWindows.computeIfAbsent(userId,
                ignored -> new FixedWindowState(0, now));
        double elapsedSeconds = elapsedSeconds(now, state.windowStartNanos);

        if (elapsedSeconds >= rateOrWindowSeconds) {
            state.count = 0;
            state.windowStartNanos = now;
        }
        if (state.count >= capacity) {
            return false;
        }
        state.count++;
        return true;
    }

    private static double elapsedSeconds(long now, long previous) {
        return Math.max(0L, now - previous) / NANOS_PER_SECOND;
    }

    private static final class TokenBucketState {
        private double tokens;
        private long lastUpdatedNanos;

        private TokenBucketState(double tokens, long lastUpdatedNanos) {
            this.tokens = tokens;
            this.lastUpdatedNanos = lastUpdatedNanos;
        }
    }

    private static final class LeakyBucketState {
        private double level;
        private long lastUpdatedNanos;

        private LeakyBucketState(double level, long lastUpdatedNanos) {
            this.level = level;
            this.lastUpdatedNanos = lastUpdatedNanos;
        }
    }

    private static final class FixedWindowState {
        private int count;
        private long windowStartNanos;

        private FixedWindowState(int count, long windowStartNanos) {
            this.count = count;
            this.windowStartNanos = windowStartNanos;
        }
    }
}
