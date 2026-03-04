package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class SetupConfession implements ICommand {


    @Override
    public void accept(MessageReceivedEvent event) {

        if (event.getAuthor().getIdLong() != Constant.KIAN_ID) return;




        Container container = Container.of(

                TextDisplay.of("## Create a Confession"),

                ActionRow.of(
                        Button.of(ButtonStyle.SUCCESS,"confess","Create Confession", Emoji.fromUnicode("U+2709"))
                )

        );

        event.getChannel().sendMessageComponents(container).useComponentsV2().queue();



    }

    @Override
    public String commandName() {
        return "!setup-confess";
    }
}
