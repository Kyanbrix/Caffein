package com.github.kyanbrix.component.button;

import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;

public class CreateConfession implements IButton{
    @Override
    public void accept(ButtonInteractionEvent event) {




        Modal modal = Modal.create("confession","My Confession")
                .addComponents(
                        Label.of("Confession", TextInput.create("confess", TextInputStyle.PARAGRAPH).setMaxLength(300).setRequired(true).build())
                ).build();

        event.replyModal(modal).queue();






    }

    @Override
    public String buttonId() {
        return "confess";
    }
}
