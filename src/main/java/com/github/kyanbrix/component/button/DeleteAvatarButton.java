package com.github.kyanbrix.component.button;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public class DeleteAvatarButton implements IButton {
    @Override
    public void accept(ButtonInteractionEvent event) {

        event.reply("Delete Avatar").setEphemeral(true).queue();

        event.getMessage().delete().queue();

    }

    @Override
    public String buttonId() {
        return "deleteAvatar";
    }
}
