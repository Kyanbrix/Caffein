package com.github.kyanbrix.features.leveling;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.features.leveling.utilities.LevelUpCard;
import com.github.kyanbrix.features.leveling.utilities.LevelUpData;
import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class VoiceLeveling extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(VoiceLeveling.class);
    private static final Map<Long, Long> activeVoiceUsers = new ConcurrentHashMap<>();
    @Override
    public void onGuildReady(@NonNull GuildReadyEvent event) {

        Guild guild = event.getGuild();

        if (guild.getIdLong() == Constant.SERVER_CAFE_ID) {

            //Bot status


            List<VoiceChannel> voiceChannels = guild.getVoiceChannels();

            for (VoiceChannel voice: voiceChannels) {

                if (voice.getMembers().isEmpty()) continue;

                for (Member member : voice.getMembers()) {
                    long currentMillis = System.currentTimeMillis();
                    if (member.getUser().isBot()) continue;
                    if (member.getUser().getIdLong() == Constant.KIAN_ID) continue;
                    activeVoiceUsers.put(member.getIdLong(),currentMillis);
                }


            }

            startXpScheduler(guild);
            reScanVoiceChannelMembers(guild);

            log.info("All voice are cached!");

        }



    }

    @Override
    public void onGuildVoiceUpdate(@NonNull GuildVoiceUpdateEvent event) {

        if (event.getGuild().getIdLong() != Constant.SERVER_CAFE_ID) return;


        AudioChannelUnion joinedChannel = event.getChannelJoined();
        AudioChannelUnion leftChannel = event.getChannelLeft();

        long user_id = event.getMember().getIdLong();
        //User joined  vc
        if (joinedChannel != null && leftChannel == null) {
            if (event.getMember().getUser().isBot()) return;
            if (event.getMember().getIdLong() == Constant.KIAN_ID) return;

            log.info("{} added to active voice users cached!",user_id);
            activeVoiceUsers.put(user_id,System.currentTimeMillis() + 60000);

        }

        // user left vc
        if (leftChannel != null && joinedChannel == null) {
            activeVoiceUsers.remove(user_id);
        }




    }

    private void reScanVoiceChannelMembers(Guild guild) {

        Caffein.getInstance().getService().scheduleAtFixedRate(() -> {

            List<VoiceChannel> voiceChannels = guild.getVoiceChannels();

            for (VoiceChannel voice: voiceChannels) {

                if (voice.getMembers().isEmpty()) continue;

                for (Member member : voice.getMembers()) {

                    if (member.getUser().isBot()) continue;
                    if (member.getIdLong() == Constant.KIAN_ID) continue;

                    if (activeVoiceUsers.containsKey(member.getIdLong())) continue;

                    activeVoiceUsers.remove(member.getIdLong());

                    log.warn("{} has not found in voice channels, removed to activeVoiceUsers cached!",member.getEffectiveName());

                }


            }



        },30,30,TimeUnit.SECONDS);



    }


    private void startXpScheduler(Guild guild) {


        log.info("Scheduler is Starting");


        Caffein.getInstance().getService().scheduleAtFixedRate(() -> {
            if (activeVoiceUsers.isEmpty()) return;

            for (Long userId : activeVoiceUsers.keySet()) {

                long cooldown = activeVoiceUsers.get(userId);

                if (cooldown > System.currentTimeMillis()) continue;

                insertXp(userId,guild); // always runs first

                try (Connection connection = Caffein.getInstance().getConnection();
                     PreparedStatement ps = connection.prepareStatement(
                             "SELECT m.user_id, m.user_xp, m.user_level, a.xp " +
                             "FROM leveling m LEFT JOIN voice_leveling a ON m.user_id = a.user_id " +
                             "WHERE m.user_id = ?")) {

                    ps.setLong(1, userId);

                    try (ResultSet set = ps.executeQuery()) {
                        if (!set.next()) {
                            log.warn("No data found for user {}", userId);
                            continue;
                        }

                        long user_id  = set.getLong("user_id");
                        int  voice_xp = set.getInt("xp");
                        int  chat_xp  = set.getInt("user_xp");
                        int  level    = set.getInt("user_level");
                        int  totalXP  = chat_xp + voice_xp;

                        int calculateLevel = calculateLevel(totalXP);

                        if (calculateLevel > level) {

                            // ✅ 1. Update level first — always, regardless of card success
                            try (PreparedStatement updateLevel = connection.prepareStatement(
                                    "UPDATE leveling SET user_level = ? WHERE user_id = ?")) {
                                updateLevel.setInt(1, calculateLevel);
                                updateLevel.setLong(2, user_id);
                                updateLevel.executeUpdate();
                            }

                            // ✅ 2. xpProgress based on NEW level
                            int xpCurrentLevel = (int) Math.pow(calculateLevel, 2) * 100;
                            int xpNextLevel    = (int) Math.pow(calculateLevel + 1, 2) * 100;
                            int xpProgress     = totalXP - xpCurrentLevel;
                            int xpNeeded       = xpNextLevel - xpCurrentLevel;

                            // ✅ 3. Null check on member
                            Member member = guild.getMemberById(userId);
                            if (member == null) {
                                log.warn("Member {} not in cache, level updated but card skipped", userId);
                                continue;
                            }

                            // ✅ 4. Rank query
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
                                    if (!rs.next()) continue;

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

                                    byte[] image = LevelUpCard.generate(data);

                                    // ✅ 5. Null check on channel
                                    TextChannel channel = guild.getTextChannelById(1483359632075260024L);
                                    if (channel != null) {
                                        channel.sendMessageFormat("%s you’ve reached the next level !!",member.getAsMention()).addFiles(FileUpload.fromData(image,"levelup.png")).queue();
                                    } else {
                                        log.warn("Level-up channel not found!");
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Failed to generate level-up card for {}", userId, e);
                            }
                        }
                    }

                } catch (SQLException e) {
                    log.error("Error in XP scheduler for user {}", userId, e);
                }
            }

        }, 0, 1, TimeUnit.MINUTES);


    }

    private int addXp() {
        return 40;
    }

    public int calculateLevel(int totalXP) {
        return (int) Math.sqrt(totalXP / 100.0);
    }


    private void insertXp (long user_id,Guild guild) {
        String ensureUser = """
         INSERT INTO leveling (user_id, user_xp, user_level)
         VALUES (?, 0, 0)
         ON CONFLICT (user_id) DO NOTHING
        """;

        String insertVoiceXp = """
         INSERT INTO voice_leveling (user_id, xp)
         VALUES (?, ?)
         ON CONFLICT (user_id) DO UPDATE SET xp = voice_leveling.xp + EXCLUDED.xp
        """;

        String insertTotalXp = """
            INSERT INTO server_xp (user_id,total_xp) VALUES(?,?)
            ON CONFLICT (user_id) DO UPDATE SET total_xp = server_xp.total_xp + EXCLUDED.total_xp
            """;


        Member member = guild.getMemberById(user_id);

        if (member == null) {
            log.warn("Member is null cannot view if it is a booster");
            return;
        }

        int xp = addXp();

        if (member.getRoles().contains(guild.getBoostRole())) {
            xp = xp + 8;
            log.info("{} is a booster so i added {} xp boost",member.getEffectiveName(),xp);

        }

        try (Connection connection = Caffein.getInstance().getConnection();
        PreparedStatement ps = connection.prepareStatement(insertVoiceXp);
        PreparedStatement psEnsure = connection.prepareStatement(ensureUser);
        PreparedStatement psTotal = connection.prepareStatement(insertTotalXp)) {

            psEnsure.setLong(1,user_id);
            psEnsure.executeUpdate();

            ps.setLong(1,user_id);
            ps.setInt(2,xp);
            ps.executeUpdate();

            psTotal.setLong(1,user_id);
            psTotal.setInt(2,xp);
            int rows = psTotal.executeUpdate();

            log.info("Total Xp affected rows {}",rows);

            if (activeVoiceUsers.containsKey(user_id)) {
                activeVoiceUsers.replace(user_id, System.currentTimeMillis() + 60000);

            }else activeVoiceUsers.put(user_id, System.currentTimeMillis() + 60000);


        }catch (SQLException e) {
            log.error("Error inserting data to voice leveling",e);
        }




    }


}
