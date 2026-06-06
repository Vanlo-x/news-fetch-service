package com.vanlo.newsfetch.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FetchNewsRequest(
        @Size(max = 50)
        List<@NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must contain only letters, numbers, underscores, or hyphens") String> sourceIds,

        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must contain only letters, numbers, underscores, or hyphens")
        String category,

        @Pattern(regexp = "^[a-z]{2}$", message = "must be a lowercase ISO 639 language code")
        String language,

        @Pattern(regexp = "^[A-Z]{2}$", message = "must be an uppercase ISO 3166 region code")
        String region,

        @Min(1)
        @Max(100)
        Integer limit
) {
}
