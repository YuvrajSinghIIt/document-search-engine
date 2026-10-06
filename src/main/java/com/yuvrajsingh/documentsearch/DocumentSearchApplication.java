package com.yuvrajsingh.documentsearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

/** Interactive demonstration of document retrieval, caching, and rate limiting. */
public final class DocumentSearchApplication {
    private static final int CACHE_CAPACITY = 5;
    private static final int REQUEST_CAPACITY = 3;

    private DocumentSearchApplication() {
    }

    public static void main(String[] arguments) throws IOException {
        SearchEngine searchEngine = new SearchEngine();
        if (!searchEngine.loadDocuments(Path.of("documents.txt"))) {
            System.out.println("Error: could not open documents.txt");
            return;
        }

        LruCache<String, String> cache = new LruCache<>(CACHE_CAPACITY);
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        printWelcome(searchEngine.documentCount());

        if (!runDemoStage(input, searchEngine, cache, RateLimiter.Algorithm.TOKEN_BUCKET,
                "1. Token Bucket (capacity=3, refill=1/sec)",
                "Allows bursts up to capacity, then refills over time.", 1.0)
                || !runDemoStage(input, searchEngine, cache, RateLimiter.Algorithm.LEAKY_BUCKET,
                "2. Leaky Bucket (capacity=3, leak=1/sec)",
                "Allows bursts up to capacity while the bucket drains over time.", 1.0)
                || !runDemoStage(input, searchEngine, cache, RateLimiter.Algorithm.FIXED_WINDOW,
                "3. Fixed Window (capacity=3, window=5sec)",
                "Counts requests per window and resets when the window expires.", 5.0)) {
            System.out.println("Goodbye!");
            return;
        }

        System.out.println("========================================");
        System.out.println("  Demo complete. Entering normal search mode.");
        System.out.println("  Type 'exit' to quit.");
        System.out.println("========================================");

        RateLimiter limiter = new RateLimiter(RateLimiter.Algorithm.TOKEN_BUCKET,
                REQUEST_CAPACITY, 1.0);
        while (true) {
            System.out.print("Enter query: ");
            String query = input.readLine();
            if (query == null || query.equals("exit")) {
                break;
            }
            if (query.isBlank()) {
                continue;
            }
            if (!limiter.allowRequest("user1")) {
                System.out.println("Rate limit exceeded. Please wait.\n");
                continue;
            }
            printResult(searchEngine, cache, query, "", "");
        }
        System.out.println("Goodbye!");
    }

    private static void printWelcome(int documentCount) {
        System.out.println("\n========================================");
        System.out.println("    Smart Document Search Engine");
        System.out.println("    Java Retrieval System");
        System.out.println("========================================");
        System.out.println("  Loaded " + documentCount + " documents.");
        System.out.println("  Rate Limiter Demo: 3 algorithms, capacity=3\n");
        System.out.println("  Try sending 4+ queries quickly to see rate limiting.");
        System.out.println("  Type 'next' to switch to the next algorithm.\n");
    }

    private static boolean runDemoStage(BufferedReader input, SearchEngine searchEngine,
            LruCache<String, String> cache, RateLimiter.Algorithm algorithm, String name,
            String description, double rateOrWindowSeconds) throws IOException {
        System.out.println("========================================");
        System.out.println("  " + name);
        System.out.println("  " + description);
        System.out.println("========================================");
        System.out.println("  Type 'next' to switch algorithm or 'exit' to quit.\n");

        RateLimiter limiter = new RateLimiter(algorithm, REQUEST_CAPACITY, rateOrWindowSeconds);
        int requestCount = 0;
        while (true) {
            System.out.print("  Enter query: ");
            String query = input.readLine();
            if (query == null || query.equals("exit")) {
                return false;
            }
            if (query.equals("next")) {
                System.out.println();
                return true;
            }
            if (query.isBlank()) {
                continue;
            }

            requestCount++;
            System.out.print("  Request " + requestCount + ": ");
            if (!limiter.allowRequest("user1")) {
                System.out.println("REJECTED - rate limit hit\n");
                continue;
            }
            printResult(searchEngine, cache, query, "ALLOWED ", "  -> ");
        }
    }

    private static void printResult(SearchEngine searchEngine, LruCache<String, String> cache,
            String query, String statusPrefix, String resultPrefix) {
        String cachedResult = cache.get(query).orElse(null);
        if (cachedResult != null) {
            System.out.println(statusPrefix + "[CACHE HIT]");
            System.out.println(resultPrefix + cachedResult + "\n");
            return;
        }

        SearchEngine.SearchResult result = searchEngine.search(query);
        String finalResult = result.score() == 0
                ? "No relevant document found."
                : result.document();
        cache.put(query, finalResult);
        System.out.println(statusPrefix + "[CACHE MISS]");
        System.out.println(resultPrefix + finalResult + "\n");
    }
}
