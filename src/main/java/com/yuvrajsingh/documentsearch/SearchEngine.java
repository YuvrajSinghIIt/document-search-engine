package com.yuvrajsingh.documentsearch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads line-based documents and retrieves the best keyword-overlap match. */
public final class SearchEngine {
    private final List<String> documents = new ArrayList<>();
    private final Map<String, List<Integer>> invertedIndex = new HashMap<>();

    /**
     * Loads one document per searchable line. A failed load keeps the active collection unchanged.
     */
    public boolean loadDocuments(Path filePath) {
        final List<String> lines;
        try {
            lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return false;
        }

        List<String> loadedDocuments = new ArrayList<>();
        Map<String, List<Integer>> loadedIndex = new HashMap<>();

        for (String line : lines) {
            Set<String> words = tokenize(line);
            if (words.isEmpty()) {
                continue;
            }

            int documentId = loadedDocuments.size();
            loadedDocuments.add(line);
            for (String word : words) {
                loadedIndex.computeIfAbsent(word, ignored -> new ArrayList<>()).add(documentId);
            }
        }

        documents.clear();
        documents.addAll(loadedDocuments);
        invertedIndex.clear();
        invertedIndex.putAll(loadedIndex);
        return true;
    }

    /** Returns the highest-scoring document for the query, or a score of zero when no term matches. */
    public SearchResult search(String query) {
        Map<Integer, Integer> scores = new HashMap<>();
        for (String word : tokenize(query)) {
            for (int documentId : invertedIndex.getOrDefault(word, List.of())) {
                scores.merge(documentId, 1, Integer::sum);
            }
        }

        String bestDocument = "";
        int bestScore = 0;
        int bestDocumentId = -1;

        for (Map.Entry<Integer, Integer> candidate : scores.entrySet()) {
            int documentId = candidate.getKey();
            int score = candidate.getValue();
            if (score > bestScore || (score == bestScore &&
                    (bestDocumentId == -1 || documentId < bestDocumentId))) {
                bestScore = score;
                bestDocumentId = documentId;
                bestDocument = documents.get(documentId);
            }
        }

        return new SearchResult(bestDocument, bestScore);
    }

    public int documentCount() {
        return documents.size();
    }

    private static Set<String> tokenize(String text) {
        Set<String> words = new HashSet<>();
        StringBuilder currentWord = new StringBuilder();

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                currentWord.append(Character.toLowerCase(character));
            } else if (!currentWord.isEmpty()) {
                words.add(currentWord.toString());
                currentWord.setLength(0);
            }
        }

        if (!currentWord.isEmpty()) {
            words.add(currentWord.toString());
        }

        return words;
    }

    public record SearchResult(String document, int score) {
    }
}
