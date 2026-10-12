package com.saishekar.jobtracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end API tests on the H2-backed "test" profile. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobTrackerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerCreateAndAnalyze() throws Exception {
        String token = register("ada@example.com");

        // Unauthenticated access is refused.
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized());

        // Create an application.
        String createBody = """
                {
                  "company": "Example Corp",
                  "title": "Software Engineer II",
                  "location": "Remote - US",
                  "status": "APPLIED",
                  "appliedDate": "%s",
                  "salaryMin": 110000,
                  "salaryMax": 140000,
                  "notes": "Referral from a former teammate"
                }
                """.formatted(LocalDate.now());
        MvcResult created = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company").value("Example Corp"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // It shows up in the list, and the status filter works.
        mockMvc.perform(get("/api/applications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/applications?status=REJECTED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Move it to SCREENING: that counts as an employer response.
        mockMvc.perform(patch("/api/applications/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SCREENING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCREENING"));

        // Schedule an interview round.
        mockMvc.perform(post("/api/applications/" + id + "/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round\":\"Recruiter screen\",\"scheduledAt\":\"2030-01-01T15:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.round").value("Recruiter screen"));

        // Analytics reflect all of the above.
        mockMvc.perform(get("/api/analytics/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.SCREENING").value(1))
                .andExpect(jsonPath("$.responseRate.applied").value(1))
                .andExpect(jsonPath("$.responseRate.responded").value(1))
                .andExpect(jsonPath("$.responseRate.rate").value(1.0));
    }

    @Test
    void usersCannotSeeEachOthersApplications() throws Exception {
        String first = register("first@example.com");
        String second = register("second@example.com");

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Hidden Co\",\"title\":\"Engineer\",\"appliedDate\":\""
                                + LocalDate.now() + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/applications").header("Authorization", "Bearer " + second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void duplicateRegistrationAndBadLoginAreRejected() throws Exception {
        register("dupe@example.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dupe@example.com\",\"password\":\"password123\",\"displayName\":\"Dupe\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dupe@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validationErrorsCarryFieldDetails() throws Exception {
        String token = register("valid8@example.com");
        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"\",\"title\":\"Engineer\",\"appliedDate\":\""
                                + LocalDate.now() + "\",\"salaryMin\":200000,\"salaryMax\":100000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.company").exists());
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"displayName\":\"Test User\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }
}
