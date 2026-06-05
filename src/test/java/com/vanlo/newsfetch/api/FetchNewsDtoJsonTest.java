package com.vanlo.newsfetch.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanlo.newsfetch.domain.FetchStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FetchNewsDtoJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesFetchNewsResponseSkeleton() throws Exception {
        FetchNewsResponse response = new FetchNewsResponse(FetchStatus.OK, List.of(), List.of());

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"status\":\"OK\"");
        assertThat(json).contains("\"items\":[]");
        assertThat(json).contains("\"errors\":[]");
    }
}
