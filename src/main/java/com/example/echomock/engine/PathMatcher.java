package com.example.echomock.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Matches a configured path pattern (with {@code {var}} segments) against an
 * actual request path, extracting any path variables. Trailing slashes are
 * ignored. Returns {@code null} when the path does not match.
 */
public final class PathMatcher {

    private PathMatcher() {
    }

    public static Map<String, String> match(String pattern, String actual) {
        String[] patternParts = split(pattern);
        String[] actualParts = split(actual);

        if (patternParts.length != actualParts.length) {
            return null;
        }

        Map<String, String> vars = new LinkedHashMap<>();
        for (int i = 0; i < patternParts.length; i++) {
            String p = patternParts[i];
            String a = actualParts[i];
            if (p.startsWith("{") && p.endsWith("}")) {
                vars.put(p.substring(1, p.length() - 1), a);
            } else if (!p.equals(a)) {
                return null;
            }
        }
        return vars;
    }

    private static String[] split(String path) {
        if (path == null) {
            return new String[0];
        }
        String trimmed = path;
        // strip leading/trailing slashes
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("/");
    }
}
