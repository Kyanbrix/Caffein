package com.github.kyanbrix.features.leveling;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.features.leveling.utilities.LevelUpCard;
import com.github.kyanbrix.features.leveling.utilities.LevelUpData;
import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatLeveling extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ChatLeveling.class);

    private final Map<Long, Long> cachedUserMessageCooldown = new ConcurrentHashMap<>();
    private static final long COOLDOWN = 30000;

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {

        if (event.getGuild().getIdLong() != Constant.SERVER_CAFE_ID) return;

        if (!event.isFromGuild() || event.getAuthor().isBot() || event.getAuthor().getIdLong() == Constant.KIAN_ID) return;

        final long channelId = event.getChannel().getIdLong();

        if (channelExcluded().contains(channelId)) return;

        final long userId = event.getAuthor().getIdLong();
        final long currentMillis = System.currentTimeMillis();
        final long addCooldown = currentMillis + COOLDOWN;
        Guild guild = event.getGuild();



        if (cachedUserMessageCooldown.containsKey(userId)) {
            long getUserCooldown = cachedUserMessageCooldown.get(userId);

            if (getUserCooldown < currentMillis) {
                cachedUserMessageCooldown.put(userId,addCooldown);
                handleXp(userId,guild);
            }

            return;

        }

        cachedUserMessageCooldown.putIfAbsent(userId, addCooldown);

        handleXp(userId,guild);




    }

    private void handleXp(long user_id,Guild guild) {

        Member member = guild.getMemberById(user_id);


        if (member == null) {
            log.warn("Cannot get user roles because it's null");
            return;
        }

        int addXp = xpGain();

        if (member.getRoles().contains(guild.getBoostRole())) {
            log.info("user is a booster, i added 20 xp boost");
            addXp = addXp + 10;
        }

        String insertTotalXp = """
            INSERT INTO server_xp (user_id,total_xp) VALUES(?,?)
            ON CONFLICT (user_id) DO UPDATE SET total_xp = server_xp.total_xp + EXCLUDED.total_xp
            """;

        try (Connection connection = Caffein.getInstance().getConnection();
        PreparedStatement ps = connection.prepareStatement("INSERT INTO leveling (user_id, user_xp) VALUES (?,?) ON CONFLICT (user_id) DO UPDATE SET user_xp = leveling.user_xp + EXCLUDED.user_xp ");
        PreparedStatement psTotal = connection.prepareStatement(insertTotalXp)) {
            ps.setLong(1,user_id);
            ps.setInt(2,addXp);
            ps.executeUpdate();


            psTotal.setLong(1,user_id);
            psTotal.setInt(2,addXp);
            int rows = psTotal.executeUpdate();

            log.info("Insert into total Xp, rows affected {} ",rows);

            checkAndApplyLevelUp(connection,user_id,guild);

        }catch (SQLException e) {
            log.error("Error chat xp insertion ",e);
        }


    }

    private void checkAndApplyLevelUp(Connection connection, long userId, Guild guild) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT m.user_id, m.user_xp, m.user_level , a.xp FROM leveling m LEFT JOIN voice_leveling a ON m.user_id = a.user_id WHERE m.user_id = ?"
        )) {
            query.setLong(1, userId);

            try (ResultSet set = query.executeQuery()) {
                if (!set.next()) return;

                int level = set.getInt("user_level");
                int currentXp = set.getInt("user_xp");
                int voice_xp = set.getInt("xp");
                long user_id = set.getLong("user_id");
                int totalXp = voice_xp + currentXp;

                int calculateLevel = calculateLevel(totalXp);

                if (calculateLevel > level) {

                    try (PreparedStatement updateLevel = connection.prepareStatement("UPDATE leveling SET user_level = ? WHERE user_id = ?")) {
                        updateLevel.setInt(1,calculateLevel);
                        updateLevel.setLong(2,user_id);
                        updateLevel.executeUpdate();

                    }

                    Member member = guild.getMemberById(user_id);


                    int xpCurrentLevel = (int) Math.pow(calculateLevel, 2) * 100;
                    int xpNextLevel    = (int) Math.pow(calculateLevel + 1, 2) * 100;
                    int xpProgress     = totalXp - xpCurrentLevel;
                    int xpNeeded       = xpNextLevel - xpCurrentLevel;

                    if (member == null) {
                        log.error("Member is null");
                        return;
                    }


                    String rankSql = """
                        SELECT rank FROM (
                            SELECT m.user_id,
                                   RANK() OVER (ORDER BY m.user_level DESC, m.user_xp + COALESCE(v.xp, 0) DESC) AS rank
                            FROM leveling m
                            LEFT JOIN voice_leveling v ON m.user_id = v.user_id
                        ) ranked WHERE user_id = ?
                    """;

                    try (PreparedStatement psRank = connection.prepareStatement(rankSql)) {
                        psRank.setLong(1, user_id);

                        try (ResultSet rs = psRank.executeQuery()) {
                            if (!rs.next()) return;

                            int rank = rs.getInt("rank");

                            LevelUpData data = new LevelUpData(
                                    member.getUser().getName(),
                                    level,
                                    calculateLevel,
                                    xpProgress,
                                    xpNeeded,
                                    rank,
                                    member.getUser().getEffectiveAvatarUrl() + "?size=128"
                            );

                            try {
                                byte[] image = LevelUpCard.generate(data);

                                // ✅ 5. Null check on channel
                                TextChannel channel = guild.getTextChannelById(1483359632075260024L);

                                if (channel != null) channel.sendMessageFormat("%s you’ve reached the next level !!",member.getAsMention()).addFiles(FileUpload.fromData(image,"levelup.png")).queue();
                                else log.warn("Level-up channel not found!");

                            }catch (Exception e) {
                                log.error("Cannot create level up card in chat leveling",e);
                            }


                        }


                    }




                    log.info("User {} is leveled up to {} !",user_id,calculateLevel);
                }


            }
        }
    }

    private int xpGain() {
        return 20;
    }

    public int calculateLevel(int totalXP) {
        return (int) Math.sqrt(totalXP / 100.0);
    }

    private List<Long> channelExcluded() {

        return List.of(1474664358393942158L,1481253289403220008L,1474669488723988625L,1475035056514007082L,1469373119830556803L);

    }

}
