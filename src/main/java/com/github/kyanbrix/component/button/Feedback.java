package com.github.kyanbrix.component.button;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;

public class Feedback implements IButton{
    @Override
    public String buttonId() {
        return "feedback";
    }

    @Override
    public void accept(ButtonInteractionEvent event) {

        if (event.getMember().hasPermission(Permission.ADMINISTRATOR)){
            event.reply("https://media3.giphy.com/media/v1.Y2lkPTc5MGI3NjExZjJyYmRrbHM1NmFzN2V1ejJ2dGYzZ2xyMm5yZXhxN2psNm03b3RhZyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/a4TIHaDMGADgTnEZqd/giphy.gif").setEphemeral(true).queue();
            return;
        }


        Modal modal =  Modal.create("feedbackModal","Your Feedback")
                .addComponents(Label.of("",
                        TextInput.create("feed",TextInputStyle.PARAGRAPH).setRequired(true)
                                .build()
                        ),

                        Label.of("Attachment (Optional)",AttachmentUpload.create("attachment")
                                .setRequired(false)
                                .setMaxValues(2)
                                .build()
                        )

                )

                .build();


        event.replyModal(modal).queue();








    }
}
