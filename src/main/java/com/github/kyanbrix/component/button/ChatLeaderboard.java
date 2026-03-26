package com.github.kyanbrix.component.button;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.features.leveling.renderer.ChatXpLeaderBoardRenderer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ChatLeaderboard implements IButton{
    private static final Logger log = LoggerFactory.getLogger(ChatLeaderboard.class);

    @Override
    public void accept(ButtonInteractionEvent event) {


        event.deferEdit().queue();

        try {
            JDA jda = event.getJDA();

            byte[] images = ChatXpLeaderBoardRenderer.generateBytes(entries(jda));

            Container container = Container.of(

                    TextDisplay.of("# Chat Leaderboard"),
                    Separator.createInvisible(Separator.Spacing.SMALL),
                    MediaGallery.of(MediaGalleryItem.fromFile(FileUpload.fromData(images,"chatleaderboard.png"))),
                    Separator.createInvisible(Separator.Spacing.LARGE),
                    ActionRow.of(
                            Button.of(ButtonStyle.SECONDARY,"overallLeaderboard","Overall XP"),
                            Button.of(ButtonStyle.SUCCESS,"chatLeaderboard","Chat Leaderboard", Emoji.fromFormatted("<:Chat_Icon_BrawlStars:1483732210233380975>")).asDisabled(),
                            Button.of(ButtonStyle.SECONDARY,"voiceLeaderboard","Voice Leaderboard",Emoji.fromFormatted("<:voice_channel_green:1483732458238247005>"))
                    )
            );

            event.getHook().editOriginalComponents(container).useComponentsV2().queue();

        }catch (IOException e) {
            log.error("Cannot create chatter leaderboard");
        }


    }

    @Override
    public String buttonId() {
        return "chatLeaderboard";
    }

    private List<ChatXpLeaderBoardRenderer.Entry> entries(JDA jda) {

        String sql = "SELECT * FROM leveling ORDER BY user_xp DESC LIMIT 10";

        List<ChatXpLeaderBoardRenderer.Entry> entries = new ArrayList<>();
        try (Connection connection = Caffein.getInstance().getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet set = preparedStatement.executeQuery()) {

            int rank = 1;

            while (set.next()) {
                int level = set.getInt("user_level");
                int chat_xp = set.getInt("user_xp");
                long user_id = set.getLong("user_id");

                User user = jda.retrieveUserById(user_id).complete();

                entries.add(new ChatXpLeaderBoardRenderer.Entry(rank++,user.getName(),level,chat_xp,user.getEffectiveAvatarUrl()+"?size=64"));

            }

        }catch (SQLException e) {
            log.error("Chat Leaderboard error ",e);
        }

        return entries;


    }

}
