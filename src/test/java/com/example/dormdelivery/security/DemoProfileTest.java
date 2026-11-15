package com.example.dormdelivery.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("demo")
@SpringBootTest(properties={"DEMO_DB_URL=jdbc:h2:mem:railway-profile;MODE=MySQL;DB_CLOSE_DELAY=-1", "DEMO_DB_USERNAME=sa", "DEMO_DB_PASSWORD=", "APP_ALLOWED_ORIGINS=https://demo.example.test", "PORT=9099", "push.enabled=false"})
class DemoProfileTest {
    @Autowired Environment environment;
    @Autowired WebApplicationContext context;
    @Test void demoUsesExplicitDatabasePortAndSecureCookies() throws Exception {
        assertTrue(environment.getProperty("spring.datasource.url").startsWith("jdbc:h2:mem:railway-profile"));
        assertEquals("9099",environment.getProperty("server.port"));
        assertEquals("framework",environment.getProperty("server.forward-headers-strategy"));
        assertEquals("true",environment.getProperty("server.servlet.session.cookie.secure"));
        assertEquals("lax",environment.getProperty("server.servlet.session.cookie.same-site"));
        assertEquals("false",environment.getProperty("app.password-reset.enabled"));
        var mvc=MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        mvc.perform(get("/users/capabilities")).andExpect(jsonPath("$.passwordResetAvailable").value(false));
    }
}
