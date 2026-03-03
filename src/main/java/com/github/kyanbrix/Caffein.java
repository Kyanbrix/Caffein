package com.github.kyanbrix;

import com.github.kyanbrix.component.ModalComponent;
import com.github.kyanbrix.component.StringSelectionComponent;
import com.github.kyanbrix.component.command.CommandManager;
import com.github.kyanbrix.database.ConnectionPool;
import com.github.kyanbrix.features.InviteTracker;
import com.github.kyanbrix.features.RantMessages;
import com.github.kyanbrix.features.ServerMemberHandler;
import com.github.kyanbrix.features.ServerVoiceLogs;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Caffein {

    private static final Caffein INSTANCE = new Caffein();
    private static final Logger log = LoggerFactory.getLogger(Caffein.class);
    private JDA jda;
    private ConnectionPool connectionPool;
    private final ScheduledExecutorService service = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());


    public ConnectionPool getConnectionPool() {
        return connectionPool;
    }

    public ScheduledExecutorService getService() {
        return service;
    }

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



    public void start() throws IOException, InterruptedException {
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

        jda = JDABuilder.create(
                        getToken(),
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_PRESENCES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_INVITES,
                        GatewayIntent.GUILD_VOICE_STATES,
                        GatewayIntent.DIRECT_MESSAGES
                )
                .enableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.SCHEDULED_EVENTS,CacheFlag.STICKER)
                .setMemberCachePolicy(member -> member.getGuild().getIdLong() == 1469324454470353163L)
                .setChunkingFilter(ChunkingFilter.ALL)
                .addEventListeners(new CommandManager(), new StringSelectionComponent(), new ButtonManager(), new ServerMemberHandler(), new InviteTracker(), new ModalComponent(), new RantMessages(), new ServerVoiceLogs())
                .setEnableShutdownHook(false)
                .build().awaitReady();



    }

    private String getToken() throws IOException {
        String tokenFromEnv = System.getenv("DISCORD_TOKEN");
        if (tokenFromEnv != null && !tokenFromEnv.isBlank()) {
            return tokenFromEnv;
        }

        Properties properties = new Properties();
        try (InputStream input = Caffein.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                properties.load(input);
                String tokenFromResource = properties.getProperty("token");
                if (tokenFromResource != null && !tokenFromResource.isBlank()) {
                    return tokenFromResource;
                }
            }
        }

        try (FileInputStream input = new FileInputStream("config.properties")) {
            properties.load(input);
            String tokenFromFile = properties.getProperty("token");
            if (tokenFromFile != null && !tokenFromFile.isBlank()) {
                return tokenFromFile;
            }
        } catch (IOException ignored){}

        throw new IllegalStateException("No Discord token found. Set DISCORD_TOKEN environment variable.");
    }


    private void checkIfRegularMembersIsInactive() {


        try (Connection connection = getConnection()) {

            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM regulars")) {

                try (ResultSet set = ps.executeQuery()) {

                    while (set.next()) {

                        int total_message = set.getInt("total_message");
                        long userId = set.getLong("user_id");

                        if (total_message < 50) {
                            Guild guild = getJda().getGuildById(1469324454470353163L);

                            if (guild != null) {
                                Role role = guild.getRoleById(1475742792092487851L);

                                if (role != null) guild.removeRoleFromMember(UserSnowflake.fromId(userId),role).queue();
                                else log.error("Regular role is null, probably got deleted!");

                            }

                        }


                    }

                }

            }

        }catch (SQLException e) {
            log.error(e.getMessage());
        }

    }


    private void checkIfMemberIsGoingToRegular() {


        try (Connection connection = getConnection()) {

            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM user_messages")) {

                try (ResultSet set = ps.executeQuery()) {

                    while (set.next()) {

                        int total_message = set.getInt("total_message");
                        long userId = set.getLong("userid");

                        if (total_message >= 50) {



                        }

                    }


                }


            }



        }catch (SQLException e) {
            log.error(e.getMessage());
        }



    }

    private void resetMessages() {

        //per day

        try (Connection connection = getConnection()) {

            try (PreparedStatement ps = connection.prepareStatement("UPDATE user_messages SET total_messages = ?")) {
                ps.setObject(1,0);
                ps.executeUpdate();

                log.info("All user_messages table has been updated its total messages to 0");

            }


        }catch (SQLException e) {

            log.error(e.getMessage(),e.fillInStackTrace());

        }







    }


}
