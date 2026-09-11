package com.github.kyanbrix.features;

import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.TimeUnit;

public class BumpListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {

        Message message = event.getMessage();


        if (message.getType().equals(MessageType.SLASH_COMMAND)) {

            String command = message.getInteraction().getName();

            if (command.equals("burntext")) {

                Caffein.getInstance().getService().schedule(()-> {

                    MessageEmbed embed = new EmbedBuilder()
                            .setTitle("Café au Chat | Bump Reminder ☕ ","https://disboard.org/server/1469324454470353163")
                            .setDescription("Server is ready to bump, simply type or clicked </bump:947088344167366698>.\n\n\uD83D\uDCDD Don't forget to leave a review it help keep the café active and visible")
                            .setThumbnail("https://cdn3.emoji.gg/emojis/40164-bump.gif")
                            .setColor(0xDEB887)
                            .build();

                    message.getChannel().sendMessageEmbeds(embed).addComponents(ActionRow.of(Button.link("https://disboard.org/server/1469324454470353163","Rate Us"))).queue();


                },7200, TimeUnit.SECONDS);


            }


        }


    }
}
