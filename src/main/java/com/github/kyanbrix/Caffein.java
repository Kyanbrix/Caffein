package com.github.kyanbrix;

import com.github.kyanbrix.component.StringSelectionComponent;
import com.github.kyanbrix.component.command.CommandManager;
import com.github.kyanbrix.database.ConnectionPool;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
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
            if (connectionPool != null) {
                connectionPool.close();
            }

            jda.shutdown();
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
                .addEventListeners(new CommandManager(), new StringSelectionComponent(), new ButtonManager(), new MemberJoined(), new InviteTracker(), new GuildMessagesHandler())
                .setEnableShutdownHook(false)
                .build().awaitReady();

        service.scheduleAtFixedRate(this::checking,0, 1,TimeUnit.MINUTES);


        Flyway flyway = Flyway.configure()
                .dataSource(connectionPool.getDataSource())
                .load();

        flyway.migrate();

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


    private void checking() {

        try (Connection connection = getConnection()) {

            String sql = "SELECT * FROM regulars";
            PreparedStatement query = connection.prepareStatement(sql);

            try (ResultSet set = query.executeQuery()) {

                while (set.next()) {

                    long userid = set.getLong("user_id");
                    Timestamp timestamp = set.getTimestamp("last_message");

                    Duration duration = Duration.between(timestamp.toLocalDateTime(), LocalDateTime.now(ZoneId.of("Asia/Manila")));


                    System.out.println("Hours: "+duration.toHours() +": "+duration.toDays());

                    if (duration.toDays() >= 2) {

                        Guild guild = getJda().getGuildById(1469324454470353163L);

                        if (guild != null) {
                            guild.retrieveMemberById(userid).queue(member -> {

                                Role role = guild.getRoleById(1475742792092487851L);

                                List<Role> memberRoles = member.getRoles();
                                if (role != null) {

                                    if (memberRoles.contains(role)) {
                                        guild.removeRoleFromMember(member,role).queue();

                                        String deleteQuery = "DELETE FROM regulars WHERE user_id = ?";

                                        try (Connection connection1 = getConnection()) {
                                            try (PreparedStatement statement = connection1.prepareStatement(deleteQuery)){
                                                statement.setObject(1,member.getIdLong());

                                                statement.executeUpdate();

                                                System.out.println("Deleted a user on regular Table");

                                                String delete = "DELETE FROM user_messages WHERE userid = ?";
                                                try (PreparedStatement deleteData = connection1.prepareStatement(delete)){

                                                    deleteData.setObject(1,member.getIdLong());

                                                    deleteData.executeUpdate();

                                                    System.out.println("DELETED a user from user_messages table");

                                                }


                                            }
                                        }catch (SQLException e) {
                                            log.error("Error in connection1 --- {}",e.getMessage());
                                        }

                                    } else log.error("NO role or somethin'");




                                }else log.error("Role regular is null");

                            });

                        }


                    }





                }


            }



        }catch (SQLException e) {
            log.error(e.getMessage());
            e.fillInStackTrace();
        }


    }
}
