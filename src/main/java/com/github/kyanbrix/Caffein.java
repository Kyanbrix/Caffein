package com.github.kyanbrix;

import com.github.kyanbrix.component.ModalComponent;
import com.github.kyanbrix.component.StringSelectionComponent;
import com.github.kyanbrix.component.command.CommandManager;
import com.github.kyanbrix.database.ConnectionPool;
import com.github.kyanbrix.features.GuildMessagesHandler;
import com.github.kyanbrix.features.InviteTracker;
import com.github.kyanbrix.features.MemberJoined;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Caffein {

    private static final Caffein INSTANCE = new Caffein();
    private static final Logger log = LoggerFactory.getLogger(Caffein.class);
    private JDA jda;
    private ConnectionPool connectionPool;
    private final ScheduledExecutorService service = Executors.newScheduledThreadPool(2);


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
        }, "db-pool-shutdown"));

        jda = JDABuilder.createLight(
                        getToken(),
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_PRESENCES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_INVITES
                )
                .disableCache(EnumSet.allOf(CacheFlag.class))
                .addEventListeners(new CommandManager(), new StringSelectionComponent(), new ButtonManager(), new MemberJoined(), new InviteTracker(), new GuildMessagesHandler(), new ModalComponent())
                .setEnableShutdownHook(false)
                .build().awaitReady();

        service.scheduleAtFixedRate(() -> {
            try {
                checking();
            } catch (Throwable t) {
                log.error("Unhandled error in checking()", t);
            }
        },0, 1,TimeUnit.HOURS);

        service.scheduleAtFixedRate(() -> {
            try {
                checkUserMessages();
            } catch (Throwable t) {
                log.error("Unhandled error in checkUserMessages()", t);
            }
        },10,30,TimeUnit.MINUTES);

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



    public void checkUserMessages() {
        String selectSql = "SELECT userid, last_message FROM user_messages WHERE total_messages < ?";
        String deleteSql = "DELETE FROM user_messages WHERE userid = ?";

        try (Connection connection = getConnection();
             PreparedStatement selectPs = connection.prepareStatement(selectSql);
             PreparedStatement deletePs = connection.prepareStatement(deleteSql)) {

            selectPs.setInt(1, 120);

            try (ResultSet set = selectPs.executeQuery()) {
                LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Manila"));

                while (set.next()) {
                    long userId = set.getLong("userid");
                    Timestamp timestamp = set.getTimestamp("last_message");

                    Duration duration = Duration.between(timestamp.toLocalDateTime(), now);

                    if (duration.toDays() >= 1) {
                        deletePs.setLong(1, userId);
                        int affectedRows = deletePs.executeUpdate();

                        if (affectedRows > 0) {
                            log.info("User ID {} deleted due to inactivity", userId);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to check and clean up user_messages", e);
        }
    }



    private void checking() {
        String selectSql = "SELECT user_id, last_message FROM regulars";
        String deleteRegularSql = "DELETE FROM regulars WHERE user_id = ?";
        String deleteMessagesSql = "DELETE FROM user_messages WHERE userid = ?";

        try (Connection connection = getConnection();
             PreparedStatement query = connection.prepareStatement(selectSql);
             ResultSet set = query.executeQuery()) {

            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Manila"));

            while (set.next()) {
                long userId = set.getLong("user_id");
                Timestamp timestamp = set.getTimestamp("last_message");

                if (timestamp == null) {
                    log.warn("Skipping user_id {} because last_message is null", userId);
                    continue;
                }

                Duration duration = Duration.between(timestamp.toLocalDateTime(), now);
                log.debug("Inactivity for user_id {}: {} hours ({} days)", userId, duration.toHours(), duration.toDays());


                if (duration.toDays() < 3) continue;

                Guild guild = getJda().getGuildById(1469324454470353163L);
                if (guild == null) {
                    log.warn("Guild not found while checking inactive regulars");
                    continue;
                }

                guild.retrieveMemberById(userId).queue(member -> {
                    Role role = guild.getRoleById(1475742792092487851L);
                    if (role == null) {
                        log.warn("Regular role not found in guild {}", guild.getId());
                        return;
                    }

                    if (!member.getRoles().contains(role)) {
                        log.debug("User {} does not have regular role; skipping role removal", member.getIdLong());
                        return;
                    }

                    guild.removeRoleFromMember(member, role).queue(
                            success -> {
                                try (Connection connection1 = getConnection();
                                     PreparedStatement deleteRegular = connection1.prepareStatement(deleteRegularSql);
                                     PreparedStatement deleteMessages = connection1.prepareStatement(deleteMessagesSql)) {

                                    long memberId = member.getIdLong();

                                    deleteRegular.setLong(1, memberId);
                                    deleteRegular.executeUpdate();

                                    deleteMessages.setLong(1, memberId);
                                    deleteMessages.executeUpdate();

                                    log.info("Removed inactive regular user {} from regulars and user_messages", memberId);
                                } catch (SQLException e) {
                                    log.error("Failed to clean up database rows for user {}", member.getIdLong(), e);
                                }
                            },
                            error -> log.error("Failed to remove regular role from user {}", member.getIdLong(), error)
                    );
                }, error -> log.warn("Failed to retrieve member {}: {}", userId, error.getMessage()));
            }
        } catch (SQLException e) {
            log.error("Failed to check regulars table", e);
        }
    }
}
