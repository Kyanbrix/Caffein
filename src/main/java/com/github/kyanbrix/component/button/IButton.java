package com.github.kyanbrix.component.button;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public interface IButton {

    void accept(ButtonInteractionEvent event);

    String buttonId();


    default void deleteMessage(ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
    }


    default void sendReplyEphemeral(ButtonInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

}
