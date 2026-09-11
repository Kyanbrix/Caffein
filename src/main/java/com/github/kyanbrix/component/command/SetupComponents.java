package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class SetupComponents implements ICommand {
    @Override
    public void accept(MessageReceivedEvent event) {

        if (event.getAuthor().getIdLong() != Constant.KIAN_ID) return;
        final String message = event.getMessage().getContentRaw().toLowerCase();
        String command = removePrefixCommand(message);

        Guild guild = event.getGuild();


        switch (command) {

            case "verify" -> {
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

            case "confession" -> event.getChannel().sendMessageComponents(ActionRow.of(Button.of(ButtonStyle.SUCCESS,"confess","Create Confession", Emoji.fromUnicode("U+2709")))).queue();

            case "ticket" -> {

                MessageEmbed embed = new EmbedBuilder()
                        .setColor(0xFFDAB9)
                        .setAuthor("Café au Chat Support Ticket",null,guild.getIconUrl())
                        .setDescription("Have a question or need to report something? Click the button below to create a ticket and our team will assist you. Please be patient while waiting for a response.\n\n⚠️ **Note:** Please make sure your concern is valid before creating a ticket. Misuse of the system may result in a mute.")
                        .build();


                event.getChannel().sendMessageEmbeds(embed)
                        .addComponents(ActionRow.of(
                                Button.of(ButtonStyle.SECONDARY,"ticket","Create Ticket",Emoji.fromFormatted("<a:ticket:1492729247482122431>"))))
                        .queue();



            }

            case "rules" -> {


                Container main = Container.of(

                        Section.of(
                                Thumbnail.fromUrl("https://cdn3.emoji.gg/emojis/63796-rulesenglish.png"),
                                TextDisplay.of("# Rules & Guidelines"),
                                TextDisplay.of("Welcome to our cozy café! To keep the vibes calm and enjoyable for everyone, please follow these rules:")
                        ),
                        Separator.createInvisible(Separator.Spacing.LARGE),

                        TextDisplay.of("""
                        ### 🌿 1. Be Kind & Respectful
                        Treat everyone with respect. No harassment, hate speech, bullying, or discrimination of any kind.
        
                        ### :coffee: 2. Keep the Vibes Chill
                        This is a relaxed space. Avoid unnecessary drama, arguments, or aggressive behavior.
                        
                        ### 💬 3. Use the Right Channels
                        Please keep conversations in their appropriate channels so the café stays organized and cozy.
                        
                        ### 🚫 4. No Spam or Self-Promo
                        No spamming, flooding chats, or advertising without permission from staff.
                        
                        ### 🔞 5. Keep Content Appropriate
                        No NSFW, explicit, or disturbing content. Keep things safe and comfortable for everyone.
                        
                        ### 🛡️ 6. Respect the Staff
                        Mods and admins are here to help keep the café peaceful. Please follow their guidance
                        
                        """),
                        Separator.createInvisible(Separator.Spacing.LARGE),
                        Section.of(
                                Button.link("https://discord.com/terms", "Discord TOS"),
                                TextDisplay.of("### \uD83D\uDCDC 7. Follow Discord TOS"),
                                TextDisplay.of("All Discord Terms of Service and Community Guidelines apply here.")
                        )

                );


            }
        }



    }

    @Override
    public String commandName() {
        return "setup";
    }


}
