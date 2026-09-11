package com.github.kyanbrix.component.slashcommand.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Date;


public class InviteInfo {

    public int type;
    public String code;
    public Inviter inviter;
    public Object expires_at;
    public String id;
    public Guild guild;
    public String guild_id;
    public Channel channel;
    public Profile profile;
    public Liveliness liveliness;


    public static class Profile {

        public String id;
        public String name;
        public String icon_hash;
        public int member_count;
        public int online_count;
        public String description;
        public String brand_color_primary;
        public Object banner_hash;
        public ArrayList<String> game_application_ids;
        public String tag;
        public int badge;
        public String badge_color_primary;
        public String badge_color_secondary;
        public String badge_hash;
        public ArrayList<String> features;
        public int visibility;
        public Object custom_banner_hash;
        public int premium_subscription_count;
        public int premium_tier;

    }

    public static class PrimaryGuild{
        public Object identity_guild_id;
        public boolean identity_enabled;
        public Object tag;
        public Object badge;
    }

    public static class Liveliness{
        public ArrayList<Integer> msg_activity_bins;
        public Date last_updated_ts;
    }

    public static class Inviter{
        public String id;
        public String username;
        public String avatar;
        public String discriminator;
        public int public_flags;
        public int flags;
        public Object banner;
        public int accent_color;
        public String global_name;
        public AvatarDecorationData avatar_decoration_data;
        public Collectibles collectibles;
        public Object display_name_styles;
        public String banner_color;
        public Clan clan;
        public PrimaryGuild primary_guild;
    }

    public static class Guild{
        public String id;
        public String name;
        public String splash;
        public String banner;
        public String description;
        public String icon;
        public ArrayList<String> features;
        public int verification_level;
        public Object vanity_url_code;
        public int nsfw_level;
        public boolean nsfw;
        public int premium_subscription_count;
        public int premium_tier;
    }


    public static class Channel {
        public String id;
        public int type;
        public String name;
    }

    public static  class AvatarDecorationData{
        public String asset;
        public String sku_id;
        public Object expires_at;
    }

    public static class Clan {
        public Object identity_guild_id;
        public boolean identity_enabled;
        public Object tag;
        public Object badge;
    }

    public static class Collectibles{
        public Nameplate nameplate;
    }

    public static class Nameplate{
        public String sku_id;
        public String asset;
        public String label;
        public String palette;
    }


}
