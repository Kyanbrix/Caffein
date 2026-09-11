package com.github.kyanbrix.component.button;

import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Ticket implements IButton {
    private static final Logger log = LoggerFactory.getLogger(Ticket.class);

    @Override
    public void accept(ButtonInteractionEvent event) {

        User user = event.getUser();

        //DB (ticket) architecture user_id PK, channel_id, time_stamp

//        if (checkUserIfHasExistingTicket(user.getIdLong())) {
//
//            event.reply("You still have an existing ticket!").setEphemeral(true).queue();
//            return;
//        }


        Modal modal = Modal.create("ticketModal","Ticket Support")
                .addComponents(Label.of("Concern","Please make sure your concern is valid before creating a ticket, as misuse may result in a mute.",TextInput.create("concern", TextInputStyle.PARAGRAPH).setRequiredRange(10,300).build()))
                .build();


        event.replyModal(modal).queue();


    }

    @Override
    public String buttonId() {
        return "ticket";
    }

    private boolean checkUserIfHasExistingTicket(long userId) {

        try (Connection connection = Caffein.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT user_id FROM ticket WHERE user_id = ?")) {
            ps.setLong(1,userId);

            try (ResultSet set = ps.executeQuery()) {

                return set.next();
            }


        }catch (SQLException e) {
            log.error("Error getting user on ticket",e);
            return false;
        }

    }


}
