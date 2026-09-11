package com.github.kyanbrix;

import com.github.kyanbrix.component.ConfessionModal;
import com.github.kyanbrix.component.StringSelectionComponent;
import com.github.kyanbrix.component.command.CommandManager;
import com.github.kyanbrix.component.slashcommand.*;
import com.github.kyanbrix.config.DockerManager;
import com.github.kyanbrix.config.database.ConnectionPool;
import com.github.kyanbrix.features.*;
import com.github.kyanbrix.features.chatfilter.DiscordInvitesChatFilter;
import com.github.kyanbrix.utils.CallbackServer;
import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Caffein {

    private static final Caffein INSTANCE = new Caffein();
    private static final Logger log = LoggerFactory.getLogger(Caffein.class);
    private JDA jda;
    private ConnectionPool connectionPool;
    private final ScheduledExecutorService service = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    public JDA getJda() {
        return jda;
    }
    private DockerManager dockerManager;

    private Caffein() {}

    public ScheduledExecutorService getService() {
        return service;
    }

    public ExecutorService getExecutorService() {
        return executorService;
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

    public DockerManager getDockerManager() {
        return dockerManager;
    }

    public void start() throws InterruptedException {

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            log.info("Bot is shutdown");

            service.shutdownNow();
            executorService.shutdownNow();

            if (connectionPool != null) {
                connectionPool.close();
            }

            if (jda != null) {
                jda.shutdown();
            }

        }, "Caffeine-Bot-ShutdownHook"));


        connectionPool = new ConnectionPool();



        try (Connection connection = connectionPool.getConnection()){

            System.out.println("Connected to the database" + connection.isValid(1));

        }catch (SQLException e) {
            e.printStackTrace();
        }


        var slashmanager = new SlashManager();

        slashmanager.addCommands(new GetUserInformation(),
                new GetGuildInviteInfo(),
                new TopTracks(),
                new CurrentPlaying(),
                new RecentlyPlayingTracks(),
                new TopArtists());


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
                        GatewayIntent.SCHEDULED_EVENTS,
                        GatewayIntent.GUILD_MODERATION,
                        GatewayIntent.DIRECT_MESSAGE_REACTIONS
                )
                .enableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.SCHEDULED_EVENTS,CacheFlag.STICKER)
                .setMemberCachePolicy(member -> member.getGuild().getIdLong() == Constant.SERVER_CAFE_ID)
                .setAutoReconnect(true)
                .setStatus(OnlineStatus.DO_NOT_DISTURB)
                .setChunkingFilter(ChunkingFilter.ALL)
                .setActivity(Activity.watching("My langga "))
                .addEventListeners(new CommandManager(), new StringSelectionComponent(),
                        new ButtonManager(), new InviteTracker(),
                        new ServerVoiceLogs(), new ConfessionModal(),new Assistant(),
                        new DiscordInvitesChatFilter(), new BumpListener(), new ServerMemberHandler(),
                        new AntiRaid())

                .addEventListeners(slashmanager)
                .setEnableShutdownHook(false)
                .build().awaitReady();


        CallbackServer callbackServer = new CallbackServer();

        callbackServer.start();






    }

}
