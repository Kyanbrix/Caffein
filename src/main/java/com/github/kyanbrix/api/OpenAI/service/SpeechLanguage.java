package com.github.kyanbrix.api.OpenAI.service;

/**
 * The languages {@code /transcribe} offers, both as the spoken-language hint and as translation targets.
 * Discord allows at most 25 choices or select options, so the list has to stay at 25 or fewer.
 */
public enum SpeechLanguage {

    ENGLISH("en", "English"),
    TAGALOG("tl", "Tagalog / Filipino"),
    SPANISH("es", "Spanish"),
    FRENCH("fr", "French"),
    GERMAN("de", "German"),
    ITALIAN("it", "Italian"),
    PORTUGUESE("pt", "Portuguese"),
    DUTCH("nl", "Dutch"),
    RUSSIAN("ru", "Russian"),
    UKRAINIAN("uk", "Ukrainian"),
    POLISH("pl", "Polish"),
    TURKISH("tr", "Turkish"),
    ARABIC("ar", "Arabic"),
    HINDI("hi", "Hindi"),
    INDONESIAN("id", "Indonesian"),
    MALAY("ms", "Malay"),
    VIETNAMESE("vi", "Vietnamese"),
    THAI("th", "Thai"),
    JAPANESE("ja", "Japanese"),
    KOREAN("ko", "Korean"),
    CHINESE("zh", "Chinese");

    private final String code;
    private final String label;

    SpeechLanguage(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** The ISO-639-1 code OpenAI takes as the {@code language} hint. */
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** The language with this ISO-639-1 code, or null for an unknown or missing code. */
    public static SpeechLanguage fromCode(String code) {
        if (code == null) return null;
        for (SpeechLanguage language : values()) {
            if (language.code.equals(code)) return language;
        }
        return null;
    }
}
