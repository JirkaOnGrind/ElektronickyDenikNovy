package com.example.authdemo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthdemoApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointIsPublicAndHasSecurityHeaders() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Security-Policy",
                        Matchers.containsString("default-src 'self'")
                ))
                .andExpect(header().string(
                        "Referrer-Policy",
                        "strict-origin-when-cross-origin"
                ));
    }

    @Test
    void anonymousOfflineSyncIsRedirectedToLogin() throws Exception {
        mockMvc.perform(post("/api/sync/daily-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": 42,
                                  "checkDate": "2026-07-30",
                                  "overallResult": "BEZ_ZAVAD"
                                }
                                """))
                .andExpect(status().is3xxRedirection());
    }
}
