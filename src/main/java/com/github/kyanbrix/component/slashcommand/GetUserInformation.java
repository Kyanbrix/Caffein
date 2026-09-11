package com.github.kyanbrix.component.slashcommand;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.dv8tion.jda.api.utils.ImageFormat;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.apache.pdfbox.rendering.ImageType;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class GetUserInformation implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(GetUserInformation.class);

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {

        final String userId = event.getOption("id", OptionMapping::getAsString);
        JDA jda = event.getJDA();

        jda.retrieveUserById(userId).queue(user -> user.retrieveProfile().queue(userProfile -> {

            Container container;

            String header = String.format("# [%s](https://discord.com/users/%s)",user.getEffectiveName(),userId);
            String userInfo = String.format("""
                           **ID:** ``%s``
                           **Username:** %s
                           **Server Tag:** %s
                           **Account Created:** %s (%s)                                             \s
                           \s""",user.getId(),user.getName(), (user.getPrimaryGuild() == null ? "None" : user.getPrimaryGuild().getTag()),TimeFormat.DATE_TIME_LONG.format(user.getTimeCreated()),TimeFormat.RELATIVE.format(user.getTimeCreated()));



            if (userProfile.getBanner() != null) {

                container = Container.of(

                        TextDisplay.of(header),
                        Separator.createInvisible(Separator.Spacing.SMALL),
                        TextDisplay.of(userInfo),
                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("### Avatar"),
                        MediaGallery.of(MediaGalleryItem.fromUrl((user.getAvatar() != null ? user.getAvatar().getUrl(600) : user.getEffectiveAvatar().getUrl(600)))),
                        ActionRow.of(Button.link(user.getEffectiveAvatar().getUrl(600),"View Avatar in browser")),

                        Separator.createInvisible(Separator.Spacing.LARGE),
                        TextDisplay.of("### Banner"),
                        MediaGallery.of(MediaGalleryItem.fromUrl(userProfile.getBanner().getUrl(600))),
                        ActionRow.of(Button.link(userProfile.getBanner().getUrl(600),"View Banner in browser"))

                ).withAccentColor(userProfile.getAccentColor());

            }else {
                container = Container.of(

                        TextDisplay.of(header),
                        Separator.createInvisible(Separator.Spacing.SMALL),
                        TextDisplay.of(userInfo),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("### Avatar"),
                        MediaGallery.of(MediaGalleryItem.fromUrl((user.getAvatar() != null ? user.getAvatar().getUrl(600) : user.getEffectiveAvatar().getUrl(600)))),
                        ActionRow.of(Button.link(user.getEffectiveAvatar().getUrl(600),"View Avatar in browser"),Button.of(ButtonStyle.DANGER,"deleteAvatar", Emoji.fromUnicode("U+1F5D1")))

                ).withAccentColor(userProfile.getAccentColor());
            }

            event.replyComponents(container).useComponentsV2().queue();


        }),new ErrorHandler().handle(ErrorResponse.UNKNOWN_USER, e -> event.reply("user not found!").setEphemeral(true).queue()));




    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("user","Display User Avatar, Banner, Information").setIntegrationTypes(IntegrationType.USER_INSTALL,IntegrationType.GUILD_INSTALL).addOption(OptionType.STRING,"id","User ID",true);
    }
}
