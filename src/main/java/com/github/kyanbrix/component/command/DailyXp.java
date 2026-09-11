package com.github.kyanbrix.component.command;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.features.leveling.utilities.DailyXpCard;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;


public class DailyXp implements ICommand{


    @Override
    public void accept(MessageReceivedEvent event) {


        Caffein.getInstance().getExecutorService().submit(()-> {





        });








    }

    @Override
    public String commandName() {
        return "daily";
    }


    private int dropXp()
    {
        Map<Integer, Integer> items = new HashMap<>();
        items.put(1000,80);
        items.put(2000,60);
        items.put(10000,5);
        items.put(30000,1);

        int totalWeight = 0;

        for (int weight : items.values()) {
            totalWeight+=weight;
        }

        Random random = new Random();
        int roll = random.nextInt(totalWeight);

        int dropItem = 0;
        int currentWeight = 0;


        for (Map.Entry<Integer,Integer> entry: items.entrySet()) {

            currentWeight += entry.getValue();

            if (roll < currentWeight) {
                dropItem = entry.getKey();
                break;
            }

        }


        return dropItem;

    }


    private int getTotalXp(long userId) {

        try (Connection connection = Caffein.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT total_xp FROM server_xp WHERE user_id = ?")) {
            ps.setLong(1,userId);

            try (ResultSet set = ps.executeQuery()) {

                if (set.next()) {
                    return set.getInt("total_xp");
                }

            }

            return 0;

        }catch (SQLException e) {

            return -1;
        }

    }
}
