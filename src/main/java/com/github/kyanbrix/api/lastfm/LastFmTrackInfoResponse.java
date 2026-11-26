package com.github.kyanbrix.api.lastfm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** The parts of track.getInfo the bot uses; the user fields are only there when a username was passed. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LastFmTrackInfoResponse {

    private Track track;

    public Track getTrack() { return track; }
    public void setTrack(Track track) { this.track = track; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Track {
        private String name;
        private String url;
        private String duration;
        private String userplaycount;
        private String userloved;
        private Artist artist;
        private Album album;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }

        public String getUserplaycount() { return userplaycount; }
        public void setUserplaycount(String userplaycount) { this.userplaycount = userplaycount; }

        public String getUserloved() { return userloved; }
        public void setUserloved(String userloved) { this.userloved = userloved; }

        public Artist getArtist() { return artist; }
        public void setArtist(Artist artist) { this.artist = artist; }

        public Album getAlbum() { return album; }
        public void setAlbum(Album album) { this.album = album; }

        /** Length in milliseconds, or 0 when Last.fm doesn't know it. */
        public long getDurationMs() {
            return LastFmClient.parseLong(duration);
        }

        /** How many times the user scrobbled this track, or -1 when no user was asked about. */
        public long getUserPlayCount() {
            return userplaycount == null ? -1 : LastFmClient.parseLong(userplaycount);
        }

        public boolean isLoved() {
            return "1".equals(userloved);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Artist {
        private String name;
        private String url;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Album {
        private String title;
        private String url;
        private List<Image> image;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public List<Image> getImage() { return image; }
        public void setImage(List<Image> image) { this.image = image; }

        public String getLargestImageUrl() {
            return LastFmClient.largestImage(image == null ? null : image.stream().map(Image::getUrl).toList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Image {
        private String size;

        @JsonProperty("#text")
        private String url;

        public String getSize() { return size; }
        public void setSize(String size) { this.size = size; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
