package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class SetupConfession implements ICommand {


    @Override
    public void accept(MessageReceivedEvent event) {

        if (event.getAuthor().getIdLong() != Constant.KIAN_ID) return;

        event.getChannel().sendMessageComponents(ActionRow.of(Button.of(ButtonStyle.SUCCESS,"confess","Create Confession", Emoji.fromUnicode("U+2709")))).queue();



    }

    @Override
    public String commandName() {
        return "!setup-confess";
    }


}
