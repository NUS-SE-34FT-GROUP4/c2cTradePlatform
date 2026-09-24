package sg.edu.nus.iss.c2csectrade.service;

import java.util.regex.Pattern;

/**
 * Listing descriptions are rich text typed by sellers, so they are stripped of
 * anything executable before they are stored. Kept deliberately small: we
 * remove markup rather than trying to allow a safe subset of it.
 */
public final class HtmlSanitizer {

    private static final Pattern SCRIPT_OR_STYLE =
            Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]+>");

    private HtmlSanitizer() {
    }

    public static String clean(String input) {
        if (input == null) {
            return null;
        }
        String withoutBlocks = SCRIPT_OR_STYLE.matcher(input).replaceAll("");
        String withoutTags = TAG.matcher(withoutBlocks).replaceAll("");
        return withoutTags.strip();
    }
}
