package com.vanlo.newsfetch.api;

import com.vanlo.newsfetch.application.FetchNewsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsFetchController.class)
@Import(FetchNewsUseCase.class)
class NewsFetchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fetchReturnsPlaceholderEmptyResponse() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourceIds": ["demo"],
                                  "category": "technology",
                                  "language": "zh",
                                  "region": "CN",
                                  "limit": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void fetchAllowsOptionalFilters() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void fetchRejectsInvalidLimit() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("""
                                {
                                  "limit": 101
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void fetchRejectsBlankSourceId() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourceIds": [" "]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("sourceIds[0]"));
    }

    @Test
    void fetchRejectsInvalidLanguageAndRegionFormat() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("""
                                {
                                  "language": "ZH",
                                  "region": "cn"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("language"))
                .andExpect(jsonPath("$.errors[1].field").value("region"));
    }

    @Test
    void fetchRejectsMalformedBody() throws Exception {
        mockMvc.perform(post("/v1/news/fetch")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }
}
