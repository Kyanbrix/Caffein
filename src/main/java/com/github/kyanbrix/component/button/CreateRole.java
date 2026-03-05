package com.github.kyanbrix.component.button;

import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class CreateRole implements IButton{
    private static final Logger log = LoggerFactory.getLogger(CreateRole.class);

    @Override
    public String buttonId() {
        return "create_role";
    }

    @Override
    public void accept(ButtonInteractionEvent event) {

        List<Long> memberRoles = event.getMember().getRoles().stream().map(Role::getIdLong).toList();


        if (!memberRoles.contains(1477694631838875689L) && !memberRoles.contains(1474656723078746155L)) {

            event.reply("Only for server booster and sponsors/donators").setEphemeral(true).queue();
            return;
        }


        // Update Modal
        try (Connection connection = Caffein.getInstance().getConnection()) {

            try (PreparedStatement query = connection.prepareStatement("SELECT * FROM premium_role WHERE user_id = ?")) {
                query.setLong(1,event.getUser().getIdLong());

                try(ResultSet set = query.executeQuery()) {

                    if (set.next()) {

                        Modal updateRoleModal = Modal.create("updateRole","Update My Role")
                                .addComponents(Label.of("New Role Name",
                                                TextInput.create("new_role_name",TextInputStyle.SHORT).setMinLength(2).setMaxLength(10).setRequired(true)
                                                        .build()
                                        ),

                                        Label.of("Update Color",TextInput.create("new_color_hex",TextInputStyle.SHORT).setPlaceholder("Ex. #000000").setMaxLength(8).setMinLength(7).setRequired(true).build()),

                                        Label.of("Update Icon (Optional)",AttachmentUpload.create("new_role_icon")
                                                .setRequired(false)
                                                .setMaxValues(1)
                                                .build()
                                        )
                                )

                                .build();

                        event.replyModal(updateRoleModal).queue();

                    } else {
                        Modal modal =  Modal.create("createRole","Create Role")
                                .addComponents(Label.of("Role Name",
                                                TextInput.create("role_name",TextInputStyle.SHORT).setMinLength(2).setMaxLength(10).setRequired(true)
                                                        .build()
                                        ),

                                        Label.of("Color Hex Code",TextInput.create("color_hex",TextInputStyle.SHORT).setPlaceholder("Ex. #000000").setMaxLength(8).setMinLength(7).setRequired(true).build()),

                                        Label.of("Role Icon (Optional)",AttachmentUpload.create("role_icon")
                                                .setRequired(false)
                                                .setMaxValues(1)
                                                .build()
                                        )
                                )

                                .build();

                        event.replyModal(modal).queue();
                    }

                }

            }

        }catch (SQLException e) {
            log.error("Error ",e);
        }









    }
}
