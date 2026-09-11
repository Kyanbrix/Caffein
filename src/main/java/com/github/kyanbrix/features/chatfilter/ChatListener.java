package com.github.kyanbrix.features.chatfilter;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class ChatListener extends ListenerAdapter {

    private static final List<String> BAD_WORDS = List.of(
            "bisakol", "potangina mo","tangalog","nigga","negra","negro",
            "titi","tite","itis","etits","suso","pekpek","burat","borat","sex"// replace with actual slurs you want to catch
    );

    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {


    }


    private void analyze(Message message) {

        if (message.getContentRaw().isBlank()) return;

        String[] words = message.getContentRaw().toLowerCase().split("\\\\s+");

        for (String rawWord: words) {

            String normalized = FilterWords.normalize(rawWord);
            String matched = findMatch(normalized);

            if (matched != null) {

                message.delete().queue();

                return;
            }

            String reversedMessage = FilterWords.reverseWord(normalized);
            String reverseMatch = findMatch(reversedMessage);

            if (reverseMatch != null) {

                message.delete().queue();

                return;
            }


            String subMatch = findSubstringMatch(normalized);
            if (subMatch != null) {
                message.delete().queue();
            }

        }

        String fullNormalized = FilterWords.normalize(message.getContentRaw().toLowerCase().replaceAll("\\s+", ""));
        String fullMatch = findMatch(fullNormalized);
        if (fullMatch != null ) {
            message.delete().queue();
        }



    }

    private String findMatch(String normalizedWord) {
        return BAD_WORDS.stream()
                .filter(normalizedWord::equals)
                .findFirst()
                .orElse(null);
    }

    private String findSubstringMatch(String normalizedWord) {
        return BAD_WORDS.stream()
                .filter(bad -> normalizedWord.contains(bad) && normalizedWord.length() > bad.length())
                .findFirst()
                .orElse(null);
    }
}
