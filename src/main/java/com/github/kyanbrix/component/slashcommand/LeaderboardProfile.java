package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.features.leveling.utilities.LeaderBoardCard;
import com.github.kyanbrix.features.leveling.utilities.Entry;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardProfile implements ISlash{
    private static final Logger log = LoggerFactory.getLogger(LeaderboardProfile.class);

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {

        Guild guild = event.getGuild();

        event.deferReply().queue();

        try {

            byte[] image = LeaderBoardCard.generateBytes(getLeaderBoard(guild));

            Container container = Container.of(
                    TextDisplay.of("# "+guild.getName() +" Leaderboard"),
                    Separator.createInvisible(Separator.Spacing.SMALL),
                    MediaGallery.of(MediaGalleryItem.fromFile(FileUpload.fromData(image,"leaderboard.png")))

            );

            event.getHook().sendMessageComponents(container).useComponentsV2().queue();

        }catch (IOException e) {
            log.error("Failed to generate leaderboard",e);
        }


    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("leaderboard","Check server leaderboard");
    }


    private List<Entry> getLeaderBoard(Guild guild) {
        String sql = """
                SELECT m.user_id, m.user_xp, m.user_level, COALESCE(a.xp, 0) AS xp
                FROM leveling m
                LEFT JOIN voice_leveling a ON m.user_id = a.user_id
                ORDER BY m.user_level DESC, m.user_xp + COALESCE(a.xp, 0) DESC
                LIMIT 10""";

        List<Entry> entries = new ArrayList<>();

        try (Connection connection = Caffein.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            try (ResultSet set = ps.executeQuery()) {

                int rank = 1;
                while (set.next()) {
                    int level = set.getInt("user_level");
                    if (level == 0 ) continue;

                    long user_id = set.getLong("user_id");

                    int chatXp = set.getInt("user_xp");
                    int voice_xp = set.getInt("xp");
                    int totalXp = chatXp + voice_xp;



                    String username = String.valueOf(user_id);
                    String avatar = null;


                    try {
                        Member member = guild.getMemberById(user_id);
                        username = member.getUser().getName();
                        avatar = member.getUser().getAvatar().getUrl(64);

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
}
