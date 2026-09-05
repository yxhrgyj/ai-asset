package com.company.aiasset.security;

import com.company.aiasset.bootstrap.TestDataController;
import com.company.aiasset.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TestDataSecurityTest.ProbeController.class)
@Import({SecurityConfig.class, TestDataSecurityTest.ProbeController.class})
class TestDataSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @Test
    void testDataEndpointsRejectAnonymousRequests() throws Exception {
        mockMvc.perform(post("/api/test-data/clean"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testDataEndpointsRejectAuthenticatedNonAdministrators() throws Exception {
        mockMvc.perform(post("/api/test-data/clean")
                        .with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDataEndpointsAllowAdministratorsWhenTheProfileIsEnabled() throws Exception {
        mockMvc.perform(post("/api/test-data/clean")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void testDataControllerRequiresAnExplicitProfile() {
        Profile profile = TestDataController.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("test-data");
    }

    @Test
    void databaseDiagnosticReadsCredentialsFromTheEnvironment() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/company/aiasset/CheckArchived.java"
        ));

        assertThat(source).doesNotContain("String password = \"");
        assertThat(source).contains("AI_ASSET_DB_URL", "AI_ASSET_DB_USER", "AI_ASSET_DB_PASSWORD");
    }

    @RestController
    public static class ProbeController {
        @PostMapping("/api/test-data/clean")
        String clean() {
            return "ok";
        }
    }
}
