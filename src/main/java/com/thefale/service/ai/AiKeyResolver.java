package com.thefale.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Resolves AI provider API keys from Spring/env values with optional .env file lookup.
 */
final class AiKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(AiKeyResolver.class);

    private AiKeyResolver() {}

    static String resolveKey(String configuredValue, String... envKeys) {
        String key = configuredValue != null ? configuredValue.trim() : "";
        if (isValidKey(key)) {
            return key;
        }
        Path[] searchPaths = new Path[] {
                Paths.get(".env"),
                Paths.get("../.env"),
                Paths.get("f:/theFaleOne-Backend/.env")
        };
        for (Path path : searchPaths) {
            try {
                if (!Files.exists(path)) continue;
                List<String> lines = Files.readAllLines(path);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    for (String envKey : envKeys) {
                        String prefix = envKey + "=";
                        if (trimmed.startsWith(prefix)) {
                            String val = trimmed.substring(prefix.length()).trim();
                            if (isValidKey(val)) {
                                return val;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Could not read AI key from {}: {}", path, e.getMessage());
            }
        }
        return "";
    }

    static boolean isValidKey(String value) {
        return value != null && !value.isBlank() && !"placeholder".equalsIgnoreCase(value.trim());
    }
}

