package com.yuvrajsingh.documentsearch;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/** Lightweight standard-library regression suite runnable without external test dependencies. */
public final class TestSuite {
    private static int checks;

    private TestSuite() {
    }

    public static void main(String[] arguments) {
        testSearch();
        testCache();
        testRateLimiting();
        System.out.println("Passed " + checks + " Java checks");
    }

    private static void testSearch() {
        SearchEngine engine = new SearchEngine();
        check(engine.search("binary").score() == 0, "empty engine has no match");
        check(engine.loadDocuments(Path.of("tests/documents.txt")), "load search fixture");
        check(engine.documentCount() == 3, "ignore lines without searchable words");

        SearchEngine.SearchResult result = engine.search("BINARY, search TREE!");
        check(result.document().equals("Binary search tree") && result.score() == 3,
                "normalize case and punctuation, then rank by matching terms");
        check(engine.search("binary binary").score() == 1, "deduplicate query terms");
        check(engine.search("cache").score() == 1, "deduplicate document terms");
        check(engine.search("binary search").document().equals("Binary search tree"),
                "break ties using document order");
        for (String query : new String[] {"", "!!!", "unknownword"}) {
            result = engine.search(query);
            check(result.document().isEmpty() && result.score() == 0, "no-match result");
        }
        check(engine.loadDocuments(Path.of("tests/documents.txt")) && engine.documentCount() == 3,
                "reload replaces the collection");
        check(!engine.loadDocuments(Path.of("tests/missing/documents.txt")), "missing file fails");
        check(engine.documentCount() == 3 && engine.search("tree").score() == 1,
                "failed load preserves the existing collection");
    }

    private static void testCache() {
        rejects(() -> new LruCache<String, String>(0), "reject zero capacity");
        rejects(() -> new LruCache<String, String>(-1), "reject negative capacity");

        LruCache<String, String> cache = new LruCache<>(2);
        check(cache.get("missing").isEmpty(), "cache miss");
        cache.put("a", "first");
        cache.put("b", "second");
        check(cache.get("a").orElseThrow().equals("first"), "cache hit promotes entry");
        cache.put("c", "third");
        check(cache.get("b").isEmpty(), "evict least-recently-used entry");
        cache.put("a", "updated");
        cache.put("d", "fourth");
        check(cache.get("c").isEmpty(), "updating an entry refreshes recency");
        check(cache.get("a").orElseThrow().equals("updated"), "updated value retained");

        LruCache<String, String> single = new LruCache<>(1);
        single.put("x", "one");
        single.put("y", "two");
        check(single.get("x").isEmpty() && single.get("y").orElseThrow().equals("two"),
                "capacity-one eviction");
    }

    private static void testRateLimiting() {
        for (RateLimiter.Algorithm algorithm : RateLimiter.Algorithm.values()) {
            AtomicLong now = new AtomicLong();
            double rate = algorithm == RateLimiter.Algorithm.FIXED_WINDOW ? 5.0 : 1.0;
            RateLimiter limiter = new RateLimiter(algorithm, 3, rate, now::get);
            check(limiter.allowRequest("alice"), "allow first request");
            check(limiter.allowRequest("alice"), "allow second request");
            check(limiter.allowRequest("alice"), "allow third request");
            check(!limiter.allowRequest("alice"), "reject exhausted capacity");
            check(limiter.allowRequest("bob"), "independent allowance per user");

            now.addAndGet(500_000_000L);
            check(!limiter.allowRequest("alice"), "partial interval does not allow a request");
            if (algorithm == RateLimiter.Algorithm.FIXED_WINDOW) {
                now.addAndGet(4_499_000_000L);
                check(!limiter.allowRequest("alice"), "window has not ended");
                now.addAndGet(1_000_000L);
                check(limiter.allowRequest("alice"), "reset at exact window boundary");
                check(limiter.allowRequest("alice") && limiter.allowRequest("alice"),
                        "new window restores full allowance");
                check(!limiter.allowRequest("alice"), "new window still enforces capacity");
            } else {
                now.addAndGet(500_000_000L);
                check(limiter.allowRequest("alice"), "one second restores one request");
                check(!limiter.allowRequest("alice"), "recovery restores only earned capacity");
            }
            now.addAndGet(100_000_000_000L);
            for (int index = 0; index < 3; index++) {
                check(limiter.allowRequest("alice"), "idle recovery restores capacity");
            }
            check(!limiter.allowRequest("alice"), "idle recovery is capped");
        }

        rejects(() -> new RateLimiter(RateLimiter.Algorithm.TOKEN_BUCKET, 0, 1.0),
                "reject zero capacity");
        rejects(() -> new RateLimiter(RateLimiter.Algorithm.TOKEN_BUCKET, -1, 1.0),
                "reject negative capacity");
        for (double rate : new double[] {0.0, -1.0, Double.POSITIVE_INFINITY, Double.NaN}) {
            rejects(() -> new RateLimiter(RateLimiter.Algorithm.FIXED_WINDOW, 3, rate),
                    "reject invalid rate or duration");
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void rejects(Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }
        check(rejected, message);
    }
}
