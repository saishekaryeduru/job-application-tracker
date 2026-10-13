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
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/applications?status=REJECTED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

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
                .andExpect(jsonPath("$.content.length()").value(0));

        // Search is owner-scoped too: the second user cannot find it by name.
        mockMvc.perform(get("/api/applications?q=Hidden").header("Authorization", "Bearer " + second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchAndPagination() throws Exception {
        String token = register("pager@example.com");
        createApplication(token, "Acme Robotics", "Backend Engineer", "Austin, TX", "APPLIED");
        createApplication(token, "Beta Health", "Frontend Engineer", "Remote - US", "APPLIED");
        createApplication(token, "Acme Robotics", "Platform Engineer", "Denver, CO", "SAVED");

        // Search matches company, title, and location, case-insensitively.
        mockMvc.perform(get("/api/applications?q=acme").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/applications?q=frontend").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].company").value("Beta Health"));
        mockMvc.perform(get("/api/applications?q=denver").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Search composes with the status filter.
        mockMvc.perform(get("/api/applications?q=acme&status=SAVED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Platform Engineer"));

        // Pages are disjoint and the totals describe the full result set.
        mockMvc.perform(get("/api/applications?size=2&page=0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/applications?size=2&page=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1));

        // Out-of-range paging parameters are a client error, not a 500.
        mockMvc.perform(get("/api/applications?size=0").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/applications?page=-1").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
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

    private void createApplication(String token, String company, String title, String location,
                                   String status) throws Exception {
        String body = """
                {
                  "company": "%s",
                  "title": "%s",
                  "location": "%s",
                  "status": "%s",
                  "appliedDate": "%s"
                }
                """.formatted(company, title, location, status, LocalDate.now());
        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
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
