package com.example.demo.game.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImpostorHintService {

    private static final Logger log = LoggerFactory.getLogger(ImpostorHintService.class);
    private static final String FALLBACK_HINT = "no tienes pistas :(";
    private static final Pattern WORD_PATTERN = Pattern.compile("\\\"word\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final boolean enabled;
    private final String datamuseBaseUrl;
    private final int timeoutMillis;
    private final int maxHints;
    private final HttpClient httpClient;

    @Autowired
    public ImpostorHintService(
            @Value("${app.impostor-hints.enabled:true}") boolean enabled,
            @Value("${app.impostor-hints.datamuse-base-url:https://api.datamuse.com}") String datamuseBaseUrl,
            @Value("${app.impostor-hints.timeout-millis:1200}") int timeoutMillis,
            @Value("${app.impostor-hints.max-results:3}") int maxHints) {
        this(
                enabled,
                datamuseBaseUrl,
                timeoutMillis,
                maxHints,
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis)).build());
    }

    ImpostorHintService(
            boolean enabled,
            String datamuseBaseUrl,
            int timeoutMillis,
            int maxHints,
            HttpClient httpClient) {
        this.enabled = enabled;
        this.datamuseBaseUrl = datamuseBaseUrl;
        this.timeoutMillis = timeoutMillis;
        this.maxHints = Math.max(1, maxHints);
        this.httpClient = httpClient;
    }

    public List<String> generateHints(String seed) {
        if (!enabled || seed == null || seed.isBlank()) {
            return fallbackHints();
        }

        try {
            List<String> words = fetchRelatedWords(seed.trim());
            List<String> normalized = normalize(words, seed.trim());
            if (normalized.size() < maxHints) {
                return fallbackHints();
            }
            return normalized;
        } catch (Exception e) {
            log.warn("Datamuse hints unavailable for seed '{}': {}", seed, e.getMessage());
            return fallbackHints();
        }
    }

    private List<String> fetchRelatedWords(String seed) throws Exception {
        String encodedSeed = URLEncoder.encode(seed, StandardCharsets.UTF_8);
        URI uri = URI.create(datamuseBaseUrl + "/words?rel_trg=" + encodedSeed);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofMillis(timeoutMillis))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return List.of();
        }
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(response.body());
        while (matcher.find()) {
            words.add(matcher.group(1));
        }
        return words;
    }

    private List<String> normalize(List<String> candidates, String seed) {
        String seedNormalized = seed.trim().toLowerCase(Locale.ROOT);
        Map<String, String> unique = new LinkedHashMap<>();

        for (String candidate : candidates) {
            String trimmed = candidate == null ? "" : candidate.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String key = trimmed.toLowerCase(Locale.ROOT);
            if (key.equals(seedNormalized)) {
                continue;
            }
            unique.putIfAbsent(key, trimmed);
            if (unique.size() == maxHints) {
                break;
            }
        }

        return new ArrayList<>(unique.values());
    }

    private List<String> fallbackHints() {
        return List.of(FALLBACK_HINT);
    }
}
