package com.github.kyanbrix;

import com.github.kyanbrix.component.ConfessionModal;
import com.github.kyanbrix.component.RoleCreationModal;
import com.github.kyanbrix.component.StringSelectionComponent;
import com.github.kyanbrix.component.VerifyModal;
import com.github.kyanbrix.component.command.CommandManager;
import com.github.kyanbrix.component.slashcommand.CheckUserLevel;
import com.github.kyanbrix.component.slashcommand.LeaderboardProfile;
import com.github.kyanbrix.component.slashcommand.SlashManager;
import com.github.kyanbrix.database.ConnectionPool;
import com.github.kyanbrix.features.*;
import com.github.kyanbrix.features.leveling.ChatLeveling;
import com.github.kyanbrix.features.leveling.VoiceLeveling;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Caffein {

    private static final Caffein INSTANCE = new Caffein();
    private static final Logger log = LoggerFactory.getLogger(Caffein.class);
    private JDA jda;
    private ConnectionPool connectionPool;
    private final ScheduledExecutorService service = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    public JDA getJda() {
        return jda;
    }

    private Caffein() {
    }

    public Connection getConnection() {
        if (connectionPool == null) {
            throw new IllegalStateException("Connection pool is not initialized. Call start() first.");
        }
        try {
            return connectionPool.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to acquire database connection from pool.", e);
        }
    }

    public static Caffein getInstance() {
        return INSTANCE;
    }

    public static void main(String[] args) throws Exception {
        getInstance().start();
    }



    public void start() throws InterruptedException {
        this.connectionPool = new ConnectionPool();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            service.shutdown();

            if (connectionPool != null) {
                connectionPool.close();
            }

            if (jda != null) {
                jda.shutdown();
            }

        }, "Caffeine-Bot-ShutdownHook"));

        var slashmanager = new SlashManager();

        slashmanager.addCommands(new CheckUserLevel(), new LeaderboardProfile());


        jda = JDABuilder.create(
                        System.getenv("DISCORD_TOKEN"),
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_PRESENCES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_INVITES,
                        GatewayIntent.GUILD_VOICE_STATES,
                        GatewayIntent.DIRECT_MESSAGES,
                        GatewayIntent.GUILD_EXPRESSIONS,
                GatewayIntent.SCHEDULED_EVENTS
                )
                .enableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.SCHEDULED_EVENTS,CacheFlag.STICKER)
                .setMemberCachePolicy(member -> member.getGuild().getIdLong() == 1469324454470353163L)
                .setChunkingFilter(ChunkingFilter.ALL)
                .addEventListeners(new VoiceLeveling(),new ChatLeveling(),new CommandManager(), new StringSelectionComponent(), new ButtonManager(), new ServerMemberHandler(), new InviteTracker(), new Assistant(), new ServerVoiceLogs(), new ConfessionModal(), new RoleCreationModal(), new VerifyModal())
                .addEventListeners(slashmanager)
                .setEnableShutdownHook(false)
                .build().awaitReady();


    }



}
