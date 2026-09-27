package com.github.kyanbrix.component.slashcommand.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.kyanbrix.component.slashcommand.data.CatBreed;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public record CatBreedResponse(
        @JsonProperty("data") List<CatBreed> data
) {
}
