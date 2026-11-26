package com.github.kyanbrix.api.lastfm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * user.getTopArtists. Last.fm no longer serves artist photos (only a placeholder),
 * so the images in this response are left out on purpose.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LastFmTopArtistsResponse {

    @JsonProperty("topartists")
    private TopArtistsContainer topArtists;

    public TopArtistsContainer getTopArtists() { return topArtists; }
    public void setTopArtists(TopArtistsContainer topArtists) { this.topArtists = topArtists; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TopArtistsContainer {
        @JsonProperty("artist")
        private List<Artist> artists;

        @JsonProperty("@attr")
        private PageAttributes attributes;

        public List<Artist> getArtists() { return artists; }
        public void setArtists(List<Artist> artists) { this.artists = artists; }

        public PageAttributes getAttributes() { return attributes; }
        public void setAttributes(PageAttributes attributes) { this.attributes = attributes; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Artist {
        private String name;
        private String mbid;
        private String playcount;
        private String url;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        /** The MusicBrainz ID, which pins down exactly which artist this is; empty for many smaller artists. */
        public String getMbid() { return mbid; }
        public void setMbid(String mbid) { this.mbid = mbid; }

        public String getPlaycount() { return playcount; }
        public void setPlaycount(String playcount) { this.playcount = playcount; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public long getPlayCount() { return LastFmClient.parseLong(playcount); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageAttributes {
        private String page;
        private String totalPages;
        private String total;

        public String getPage() { return page; }
        public void setPage(String page) { this.page = page; }

        public String getTotalPages() { return totalPages; }
        public void setTotalPages(String totalPages) { this.totalPages = totalPages; }

        public String getTotal() { return total; }
        public void setTotal(String total) { this.total = total; }
    }
}
