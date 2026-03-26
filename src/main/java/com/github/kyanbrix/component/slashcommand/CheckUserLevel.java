package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.features.leveling.utilities.Data;
import com.github.kyanbrix.features.leveling.utilities.ProfileCard;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CheckUserLevel implements ISlash {
    private static final Logger log = LoggerFactory.getLogger(CheckUserLevel.class);

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {

        Guild guild = event.getGuild();
        long userId = event.getUser().getIdLong();

        event.deferReply().queue();

        String sql = """
                SELECT user_level, user_xp, voice_xp, rank
                        FROM (
                            SELECT m.user_id, m.user_level, m.user_xp,
                               COALESCE(v.xp, 0) AS voice_xp,
                               RANK() OVER (ORDER BY m.user_level DESC, m.user_xp + COALESCE(v.xp,0) DESC) AS rank
                            FROM leveling m
                            LEFT JOIN voice_leveling v ON m.user_id = v.user_id
                        ) ranked
                        WHERE user_id = ?
                """;

        try (Connection connection = Caffein.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1,event.getUser().getIdLong());

            try (ResultSet set = ps.executeQuery()) {

                if (set.next()) {
                    int level    = set.getInt("user_level");
                    int chatXP   = set.getInt("user_xp");
                    int voiceXP  = set.getInt("voice_xp");
                    int totalXP  = chatXP + voiceXP;
                    int rank     = set.getInt("rank");

                    int xpCurrentLevel = (int) Math.pow(level, 2) * 100;
                    int xpNextLevel    = (int) Math.pow(level + 1, 2) * 100;
                    int xpProgress     = totalXP - xpCurrentLevel;
                    int xpNeeded       = xpNextLevel - xpCurrentLevel;

                    Member member    = guild.getMemberById(userId);
                    String username  = member.getUser().getName();
                    String avatarUrl = member.getUser().getEffectiveAvatarUrl() + "?size=128";

                    Data data = new Data(username,level,xpProgress,xpNeeded,rank,avatarUrl);

                    try {
                        byte[] image = ProfileCard.generate(data);
                        event.getHook().sendFiles(FileUpload.fromData(image,"profile.png"))
                                .queue();

                    }catch (Exception e) {
                        log.error(e.getMessage());
                    }


                }

            }




        }catch (SQLException e) {
            log.error("Failed to generate profile card ",e);
        }



    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("level","View your server level and xp progress").setContexts(InteractionContextType.GUILD);
    }
}
