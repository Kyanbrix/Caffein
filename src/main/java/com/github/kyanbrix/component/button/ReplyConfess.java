package com.github.kyanbrix.component.button;

import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;

public class ReplyConfess implements IButton{
    @Override
    public void accept(ButtonInteractionEvent event) {



        Modal modal = Modal.create("replyConfession","Reply to Confession")
                .addComponents(Label.of("Reply", TextInput.create("replyConfess", TextInputStyle.PARAGRAPH).setMaxLength(1000).setRequired(true).build()),
                        Label.of("Confession ID",TextInput.create("replyId",TextInputStyle.SHORT).setRequired(true).setMaxLength(6).build())

                )
                .build();


        event.replyModal(modal).queue();

    }

    @Override
    public String buttonId() {
        return "replyConfess";
    }
}
