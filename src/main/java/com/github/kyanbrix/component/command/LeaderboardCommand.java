package com.github.kyanbrix.component.command;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.features.leveling.utilities.Entry;
import com.github.kyanbrix.features.leveling.utilities.LeaderBoardCard;
import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
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

public class LeaderboardCommand implements ICommand{
    private static final Logger log = LoggerFactory.getLogger(LeaderboardCommand.class);
    @Override
    public void accept(MessageReceivedEvent event) {


        JDA jda = event.getJDA();


        Caffein.getInstance().getExecutorService().submit(()-> {
            event.getChannel().sendTyping().queue();

            try {
                byte[] image = LeaderBoardCard.generateBytes(getLeaderBoard(jda));

                Container container = Container.of(
                        TextDisplay.of("# "+jda.getGuildById(Constant.SERVER_CAFE_ID).getName()),
                        MediaGallery.of(MediaGalleryItem.fromFile(FileUpload.fromData(image,"leaderboard.png"))),

                        ActionRow.of(
                                Button.of(ButtonStyle.SUCCESS,"overallLeaderboard","Overall XP").asDisabled(),
                                Button.of(ButtonStyle.SECONDARY,"chatLeaderboard","Chat Leaderboard", Emoji.fromFormatted("<:Chat_Icon_BrawlStars:1483732210233380975>")),
                                Button.of(ButtonStyle.SECONDARY,"voiceLeaderboard","Voice Leaderboard",Emoji.fromFormatted("<:voice_channel_green:1483732458238247005>"))
                        )
                );

                event.getChannel().sendMessageComponents(container).useComponentsV2().queue();

            }catch (IOException e) {
                log.error("Cannot generate leaderboard image");
            }

        });



    }

    private List<Entry> getLeaderBoard(JDA jda) {

        String sql = """
                SELECT m.user_id, m.user_xp, m.user_level, COALESCE(a.xp, 0) AS xp
                FROM leveling m
                LEFT JOIN voice_leveling a ON m.user_id = a.user_id
                ORDER BY m.user_level DESC, m.user_xp + COALESCE(a.xp, 0) DESC
                LIMIT 10
                """;

        List<Entry> entries = new ArrayList<>();

        try (Connection connection = Caffein.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            try (ResultSet set = ps.executeQuery()) {

                int rank = 1;
                while (set.next()) {
                    int level = set.getInt("user_level");
                    if (level == 0 ) continue;

                    long user_id = set.getLong("user_id");

                    int totalXp = getTotalXp(user_id);


                    String username = String.valueOf(user_id);
                    String avatar = null;


                    try {
                        User member = jda.retrieveUserById(user_id).complete();
                        username = member.getName();
                        avatar = member.getEffectiveAvatarUrl() + "?size=64";

                    }catch (Exception e) {
                        log.error("Couldn't fetch this member",e);
                    }

                    int xpCurrentLevel = (int) Math.pow(level, 2) * 100;       // XP where this level started
                    int xpNextLevel    = (int) Math.pow(level + 1, 2) * 100;

                    int xpProgress = totalXp - xpCurrentLevel;
                    int xpNeeded   = xpNextLevel - xpCurrentLevel;

                    entries.add(new Entry(rank++,username,level,xpProgress,xpNeeded,avatar));

                }

            }

        }catch (SQLException e) {
            log.error("Error getting data from leaderboard",e);

        }

        return entries;




    }


    @Override
    public String commandName() {
        return "lb";
    }

    @Override
    public String[] aliases() {
        return new String[]{"leaderboard"};
    }

    private int getTotalXp(long userId) {

        try (Connection connection = Caffein.getInstance().getConnection();
        PreparedStatement ps = connection.prepareStatement("SELECT total_xp FROM server_xp WHERE user_id = ?")) {
            ps.setLong(1,userId);

            try (ResultSet set = ps.executeQuery()) {

                if (set.next()) {
                    return set.getInt("total_xp");
                }

            }

            return 0;

        }catch (SQLException e) {

            return -1;
        }

    }
}
