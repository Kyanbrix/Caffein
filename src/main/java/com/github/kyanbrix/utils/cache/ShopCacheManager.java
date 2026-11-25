package com.github.kyanbrix.utils.cache;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.utils.cache.WikiCacheManager.WikiEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shop names from the aqwshop table, held in the same search-ready form as the
 * wiki items so /shop autocomplete can reuse the /wiki ranking.
 */
public class ShopCacheManager {

    private static final Logger log = LoggerFactory.getLogger(ShopCacheManager.class);

    private static volatile List<WikiEntry> entries = Collections.emptyList();

    public static void loadCache() {

        try (Connection connection = Caffein.getInstance().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT shop_name FROM aqwshop ORDER BY shop_name ASC")) {

            List<WikiEntry> updatedEntries = new ArrayList<>();
            while (resultSet.next()) {
                String name = resultSet.getString("shop_name");
                if (name == null || name.isBlank()) {
                    continue;
                }

                String normalized = WikiCacheManager.normalize(name);
                updatedEntries.add(new WikiEntry(name, normalized, normalized.replace(" ", ""),
                        WikiCacheManager.buildAcronym(name)));
            }

            entries = Collections.unmodifiableList(updatedEntries);
            log.info("Shop Cache Loaded {} shops", updatedEntries.size());

        } catch (SQLException e) {
            log.error("Failed to load Shop Cache", e);
        }
    }

    public static List<WikiEntry> getEntries() {
        return entries;
    }
}
