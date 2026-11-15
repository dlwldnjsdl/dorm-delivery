package com.example.dormdelivery.security;

import com.example.dormdelivery.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"push.enabled=false","app.password-reset.enabled=false"})
class DemoSecurityTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    MockMvc mvc;
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).build();}
    User user(){var u=new User();u.setName("demo");u.setPhone(UUID.randomUUID().toString());u.setPassword("test-only");u.setDormitory("웅비홀");return users.save(u);}
    @Test void userLookupRequiresOwnSession() throws Exception {
        var user=user(); var session=new MockHttpSession();session.setAttribute("userId",user.getId());
        mvc.perform(get("/users/"+user.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/users/"+(user.getId()+1)).session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/users/"+user.getId()).session(session)).andExpect(status().isOk());
    }
    @Test void resetIsDisabledAndDoesNotModifyAccount() throws Exception {
        var user=user();
        mvc.perform(post("/users/reset-password").contentType("application/json").content("{\"name\":\"demo\",\"phone\":\""+user.getPhone()+"\",\"dormitory\":\"웅비홀\",\"newPassword\":\"test-replacement\"}")).andExpect(status().isForbidden());
        assertEquals("test-only",users.findById(user.getId()).orElseThrow().getPassword());
        mvc.perform(get("/users/capabilities")).andExpect(status().isOk()).andExpect(jsonPath("$.passwordResetAvailable").value(false));
    }
    @Test void embeddedUserDoesNotExposePhoneOrPassword(){
        var json=new tools.jackson.databind.ObjectMapper().writeValueAsString(user());
        assertFalse(json.contains("\"phone\""));assertFalse(json.contains("\"password\""));
    }
}
