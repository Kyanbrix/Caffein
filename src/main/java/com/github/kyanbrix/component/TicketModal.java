package com.github.kyanbrix.component;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jspecify.annotations.NonNull;

public class TicketModal extends ListenerAdapter {
    private static final String MODAL_ID = "ticketModal";

    @Override
    public void onModalInteraction(@NonNull ModalInteractionEvent event) {

        final String id = event.getModalId();

        if (!id.equals(MODAL_ID)) return;

        //DB (ticket) architecture user_id PK, channel_id, time_stamp

        User user = event.getUser();

        String concern = event.getValue("concern").getAsString();


        user.openPrivateChannel().flatMap(privateChannel -> privateChannel.sendMessageEmbeds(privateMessageEmbed(user,concern)))
                .queue(message -> {

                    //Basically create a channel in a different guild/server

                },new ErrorHandler().handle(ErrorResponse.CANNOT_SEND_TO_USER,e -> event.reply("⚠️ It looks like your DMs are closed. Please open them so we can continue assisting you.").setEphemeral(true)
                        .queue()));


    }

    private MessageEmbed privateMessageEmbed(User user,String concern) {

        return new EmbedBuilder()
                .setAuthor("You created a ticket",null,user.getEffectiveAvatarUrl())
                .setColor(0xEEE8AA)
                .addField("Your Concern",concern,false)
                .addField("Note","Please wait for an admin to respond. Thank you for your patience \uD83D\uDE4F",false)
                .build();
    }
}
