package com.github.kyanbrix.component.slashcommand.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatBreed(
        @JsonProperty("breed") String breed,
        @JsonProperty("country") String country,
        @JsonProperty("origin") String origin,
        @JsonProperty("coat") String coat,
        @JsonProperty("pattern") String pattern
) {}
