package com.swiftdrop.common.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.common.config.CorsConfig;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.common.security.SecurityConfig;
import com.swiftdrop.common.security.SecurityErrorHandler;
import com.swiftdrop.common.security.jwt.JwtProperties;
import com.swiftdrop.common.security.jwt.JwtService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Smoke test for the web + security layer, no database or broker needed. */
@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class, CorsConfig.class})
@ActiveProfiles("test")
class HealthControllerSmokeTest {

    @TestConfiguration
    @EnableConfigurationProperties(JwtProperties.class)
    static class Config {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedRouteWithoutTokenReturnsStandard401() throws Exception {
        mockMvc.perform(get("/api/v1/parcels"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/parcels"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void validJwtIsAcceptedAndUnknownRouteReturnsStandard404() throws Exception {
        String token = jwtService.generateToken("user-1", List.of(Role.CUSTOMER));
        mockMvc.perform(get("/api/v1/does-not-exist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void invalidJwtIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/parcels").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanCallPing() throws Exception {
        mockMvc.perform(get("/api/v1/health").with(user("someone").roles("COURIER")))
                .andExpect(status().isOk());
    }
}
