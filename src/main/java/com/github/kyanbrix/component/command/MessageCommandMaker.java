package com.github.kyanbrix.component.command;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class MessageCommandMaker implements ICommand{
    @Override
    public void accept(MessageReceivedEvent event) {

        Guild guild = event.getGuild();

        Container banner = Container.of(
                MediaGallery.of(MediaGalleryItem.fromUrl(guild.getBanner().getUrl(600)))
        );

        Container main = Container.of(
                Section.of(
                        Thumbnail.fromUrl(guild.getIconUrl()),
                        TextDisplay.of("# Welcome to " + guild.getName() + " Discord Server"),
                        TextDisplay.of("Please check out the rules, be kind to one another, and feel free to chat, listen, or just hang out.We’re happy you’re here.")
                ),
                Separator.createInvisible(Separator.Spacing.LARGE),

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

        event.getChannel().sendMessageComponents(banner).useComponentsV2().flatMap(message -> message.getChannel().sendMessageComponents(main).useComponentsV2()).queue();



    }

    @Override
    public String commandName() {
        return "create";
    }
}
