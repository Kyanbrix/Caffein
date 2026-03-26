package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class VerificationSetup implements ICommand{
    @Override
    public void accept(MessageReceivedEvent event) {

        if (event.getAuthor().getIdLong() != Constant.KIAN_ID) return;


        Container container = Container.of(

                MediaGallery.of(MediaGalleryItem.fromUrl("https://media.discordapp.net/attachments/1480958059755864235/1484920771905065040/IMG_0571.jpg?ex=69bffbbc&is=69beaa3c&hm=b2c4c2f74f68d8b4760659c087c6b44430175a2770376591448ef5696f4df091&=&format=webp")),
                Separator.createInvisible(Separator.Spacing.SMALL),
                TextDisplay.of("# Welcome to Café au Chat"),
                TextDisplay.of("Welcome to the community! To get started, please verify your account by clicking the **Verify button** below. We also ask that you kindly follow our <#1470147677546090728> so we can keep this space enjoyable and safe for everyone."),
                Separator.createInvisible(Separator.Spacing.SMALL),
                ActionRow.of(Button.of(ButtonStyle.SUCCESS,"verify","Verify"))
        );

        event.getChannel().sendMessageComponents(container).useComponentsV2().queue();

    }

    @Override
    public String commandName() {
        return "verify";
    }
}
