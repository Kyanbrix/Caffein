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
import com.github.kyanbrix.utils.cache.WikiCacheManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Caffein extends ListenerAdapter {

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
                log.info("Connection closed");
            }

            if (jda != null) {
                jda.shutdown();
            }

        }, "Caffeine-Bot-ShutdownHook"));


        connectionPool = new ConnectionPool();

        WikiCacheManager.loadCache();
        var slashmanager = new SlashManager();

        slashmanager.addCommands(new GetUserInformation(),
                new Wiki(),
                new CharPage(),
                new GetGuildInviteInfo(),
                new ChatBot(),
                new Imagine(),
                new GenerateVideo(),
                new Transcribe(),
                new Speak(),
                new TopArtists(),
                new Transcribe(),
                new RecentlyPlayingTracks(),
                new LastFm(),
                new SearchLyrics(),
                new Shop(),
                new CollectionChests()
        );
        slashmanager.addContextCommands(new Transcribe.MessageMenu());

        // The Last.fm login callback; the bot still works without it, so a failure here isn't fatal
        try {
            new CallbackServer().start();
        } catch (Exception e) {
            log.error("Failed to start the callback server, so /fm link won't work", e);
        }


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
                .setChunkingFilter(ChunkingFilter.NONE)
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .setAutoReconnect(true)
                .addEventListeners(new CommandManager(), new StringSelectionComponent(),
                        new ButtonManager(), new InviteTracker(),
                        new ServerVoiceLogs(), new ConfessionModal(),new Assistant(),
                        new DiscordInvitesChatFilter(), new BumpListener(), new ServerMemberHandler(),
                        new AntiRaid(), new AutoComplete())

                .addEventListeners(slashmanager, INSTANCE)
                .setEnableShutdownHook(false)
                .build().awaitReady();


    }


    @Override
    public void onReady(@NonNull ReadyEvent event) {

        JDA jda = event.getJDA();

        List<Guild> guilds = jda.getGuilds();


        System.out.println("Guilds size: " + guilds.size());


    }
}
