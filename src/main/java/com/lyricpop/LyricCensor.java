package com.lyricpop;

import java.util.*;
import java.util.regex.*;

/**
 * Censors common curse words in lyrics.
 * Style: keep first + last letter, replace the middle with asterisks.
 * Examples: "nigga" → "n***a", "fuck" → "f**k", "fucking" → "f*****g"
 */
public final class LyricCensor {
    // Lowercase stems / full words we block. Order longer first so "fucking" beats "fuck".
    private static final String[] BAD = {
        "motherfucker", "motherfuckin", "motherfucking",
        "nigger", "niggas", "nigga",
        "fuckin", "fucking", "fucked", "fucker", "fucks", "fuck",
        "shit", "shits", "shitty", "bullshit",
        "bitch", "bitches", "bitchy",
        "asshole", "assholes",
        "cunt", "cunts",
        "dick", "dicks", "dickhead",
        "pussy", "pussies",
        "cock", "cocks",
        "whore", "whores",
        "slut", "sluts",
        "bastard", "bastards",
        "damn", "damned", "goddamn", "goddamnit",
        "piss", "pissed",
        "retard", "retarded",
        "faggot", "faggots", "fag",
        "twat", "twats"
    };

    private static final Pattern WORD = Pattern.compile("\\b[\\p{L}']+\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private LyricCensor() {}

    /** Censor every bad word in the given lyric line. */
    public static String censor(String text) {
        if (text == null || text.isBlank()) return text;

        Matcher m = WORD.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String original = m.group();
            String lower = original.toLowerCase(Locale.ROOT);
            String replacement = original;

            for (String bad : BAD) {
                if (lower.equals(bad) || lower.startsWith(bad) && lower.length() <= bad.length() + 2) {
                    // exact match or very short suffix (e.g. "fucks", "niggas")
                    replacement = censorWord(original);
                    break;
                }
                // also catch common "ing" / "ed" / "er" forms when the stem is listed
                if (lower.startsWith(bad) && (lower.endsWith("ing") || lower.endsWith("ed") || lower.endsWith("er") || lower.endsWith("s"))) {
                    replacement = censorWord(original);
                    break;
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** "nigga" → "n***a", "fuck" → "f**k", "F" → "F" */
    private static String censorWord(String word) {
        int len = word.length();
        if (len <= 2) return word;

        char first = word.charAt(0);
        char last = word.charAt(len - 1);
        return first + "*".repeat(len - 2) + last;
    }
}
