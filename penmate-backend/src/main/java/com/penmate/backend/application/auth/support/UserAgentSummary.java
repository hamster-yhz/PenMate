package com.penmate.backend.application.auth.support;

import java.util.Locale;

public record UserAgentSummary(String deviceName, String browserName, String operatingSystem, String raw) {

    public static UserAgentSummary parse(String value) {
        String raw = value == null || value.isBlank() ? "Unknown" : truncate(value.trim(), 500);
        String lower = raw.toLowerCase(Locale.ROOT);
        String browser = lower.contains("edg/") ? "Microsoft Edge"
                : lower.contains("chrome/") || lower.contains("crios/") ? "Chrome"
                : lower.contains("firefox/") || lower.contains("fxios/") ? "Firefox"
                : lower.contains("safari/") ? "Safari" : "Unknown browser";
        String os = lower.contains("windows") ? "Windows"
                : lower.contains("iphone") || lower.contains("ipad") ? "iOS"
                : lower.contains("android") ? "Android"
                : lower.contains("mac os") || lower.contains("macintosh") ? "macOS"
                : lower.contains("linux") ? "Linux" : "Unknown OS";
        boolean mobile = lower.contains("mobile") || lower.contains("iphone") || lower.contains("android");
        return new UserAgentSummary(mobile ? "Mobile device" : "Desktop device", browser, os, raw);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
