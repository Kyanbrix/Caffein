package com.github.kyanbrix.features.chatfilter;

import java.util.List;
import java.util.Map;

public class FilterWords {


    private static final Map<String, String> LEET_MAP = Map.ofEntries(
            Map.entry("@",  "a"),
            Map.entry("4",  "a"),
            Map.entry("3",  "e"),
            Map.entry("1",  "i"),
            Map.entry("!",  "i"),
            Map.entry("|",  "i"),
            Map.entry("0",  "o"),
            Map.entry("5",  "s"),
            Map.entry("$",  "s"),
            Map.entry("7",  "t"),
            Map.entry("+",  "t"),
            Map.entry("9",  "g"),
            Map.entry("6",  "b"),
            Map.entry("8",  "b")
    );


    public static String normalize(String input) {
        String text = input.toLowerCase();

        // 1. Remove character separators like f.u.c.k or f-u-c-k
        text = removeSeparators(text);

        // 2. Apply leetspeak substitutions
        for (var entry : LEET_MAP.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }

        // 3. ph → f  (phuck → fuck)
        text = text.replaceAll("ph", "f");

        // 4. Collapse repeated characters: fuuuuck → fuk
        text = text.replaceAll("(.)\\1+", "$1");

        // 5. Remove non-alphabetic characters (after substitutions)
        text = text.replaceAll("[^a-z\\s]", "");

        return text.strip();
    }

    // Removes separators between single characters: f.u.c.k → fuck
    private static String removeSeparators(String text) {
        return text.replaceAll("(?<=\\b\\w)[.\\-_*~,\\s](?=\\w)", "");
    }

    // Reverses a single word
    public static String reverseWord(String word) {
        return new StringBuilder(word).reverse().toString();
    }
}
