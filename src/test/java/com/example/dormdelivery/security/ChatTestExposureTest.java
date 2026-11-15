package com.example.dormdelivery.security;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.nio.file.*;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="push.enabled=false")
class ChatTestExposureTest {
    @Autowired WebApplicationContext context;

    @Test void legacyPageIsNotPubliclyAccessible() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBeansOfType(Filter.class).values().toArray(Filter[]::new)).build();
        for(String path:new String[]{"/chat-test.html","/chat-test.html?demo=true","/%63hat-test.html"}) {
            mvc.perform(get(URI.create(path))).andExpect(status().isNotFound());
        }
        // Ordinary authenticated API behavior remains intact.
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
    }

    @Test void obsoleteSourcePagesAreRemoved() {
        assertFalse(Files.exists(Path.of("src/main/resources/static/chat-test.html")));
        assertFalse(Files.exists(Path.of("src/main/resources/chat-test.html")));
    }
}
