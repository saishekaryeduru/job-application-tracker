package com.saishekar.jobtracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Autowired
    private com.saishekar.jobtracker.repository.JobApplicationRepository applicationRepository;

    @Autowired
    private com.saishekar.jobtracker.repository.UserRepository userRepository;

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
    void csvExportImportRoundTrip() throws Exception {
        String exporter = register("exporter@example.com");
        createApplication(exporter, "Acme, Inc.", "Backend Engineer", "Austin, TX", "APPLIED");
        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + exporter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Beta Health\",\"title\":\"Frontend Engineer\","
                                + "\"status\":\"SCREENING\",\"appliedDate\":\"" + LocalDate.now()
                                + "\",\"salaryMin\":100000,\"salaryMax\":130000,"
                                + "\"notes\":\"Referred by \\\"Sam\\\", follow up Friday\"}"))
                .andExpect(status().isCreated());

        MvcResult exported = mockMvc.perform(get("/api/applications/export")
                        .header("Authorization", "Bearer " + exporter))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        containsString("job-applications.csv")))
                .andReturn();
        String csv = exported.getResponse().getContentAsString(StandardCharsets.UTF_8);

        // Fields with commas and quotes are RFC 4180-escaped in the export.
        org.junit.jupiter.api.Assertions.assertTrue(csv.startsWith(
                "company,title,location,jobUrl,status,appliedDate,salaryMin,salaryMax,notes"), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"Acme, Inc.\""), csv);
        org.junit.jupiter.api.Assertions.assertTrue(
                csv.contains("\"Referred by \"\"Sam\"\", follow up Friday\""), csv);

        // The export imports cleanly into another account, values intact.
        String importer = register("importer@example.com");
        MockMultipartFile file = new MockMultipartFile(
                "file", "job-applications.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/applications/import")
                        .file(file)
                        .header("Authorization", "Bearer " + importer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2));
        mockMvc.perform(get("/api/applications?q=acme").header("Authorization", "Bearer " + importer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].company").value("Acme, Inc."));
        mockMvc.perform(get("/api/applications?q=beta").header("Authorization", "Bearer " + importer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("SCREENING"))
                .andExpect(jsonPath("$.content[0].salaryMin").value(100000))
                .andExpect(jsonPath("$.content[0].notes")
                        .value("Referred by \"Sam\", follow up Friday"));
    }

    @Test
    void csvImportRejectsBadFilesWithoutSavingAnything() throws Exception {
        String token = register("csvbad@example.com");
        String badRows = """
                company,title,location,jobUrl,status,appliedDate,salaryMin,salaryMax,notes
                Good Co,Engineer,,,,APPLIED,%s,,,
                ,Engineer,,,,NOT_A_STATUS,not-a-date,,,
                """.formatted(LocalDate.now());
        mockMvc.perform(multipart("/api/applications/import")
                        .file(new MockMultipartFile("file", "bad.csv", "text/csv",
                                badRows.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("row 3")))
                .andExpect(jsonPath("$.message").value(containsString("company is required")));

        // A header that is not the export format is refused as well.
        mockMvc.perform(multipart("/api/applications/import")
                        .file(new MockMultipartFile("file", "wrong.csv", "text/csv",
                                "name,role\nAcme,Engineer\n".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("missing column")));

        // Both failures are atomic: even the valid row was not saved.
        mockMvc.perform(get("/api/applications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void staleApplicationsFlagQuietActiveApplicationsOnly() throws Exception {
        String token = register("stale@example.com");
        createApplication(token, "Quiet Co", "Backend Engineer", "Austin, TX", "APPLIED");
        createApplication(token, "Fresh Co", "Backend Engineer", "Denver, CO", "APPLIED");
        createApplication(token, "Closed Co", "Backend Engineer", "Remote - US", "REJECTED");
        createApplication(token, "Saved Co", "Backend Engineer", "Remote - US", "SAVED");

        // Age Quiet Co and Closed Co past the default threshold by moving
        // their updatedAt back; Fresh Co and Saved Co keep today's timestamp.
        var owner = userRepository.findByEmail("stale@example.com").orElseThrow();
        Field updatedAt = com.saishekar.jobtracker.model.JobApplication.class
                .getDeclaredField("updatedAt");
        updatedAt.setAccessible(true);
        for (var app : applicationRepository.findAllByOwnerOrderByAppliedDateDescIdDesc(owner)) {
            if (app.getCompany().equals("Quiet Co") || app.getCompany().equals("Closed Co")) {
                updatedAt.set(app, Instant.now().minus(15, ChronoUnit.DAYS));
                applicationRepository.save(app);
            }
        }

        // Only the quiet, still-active application is flagged, with its age.
        mockMvc.perform(get("/api/applications/stale").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].company").value("Quiet Co"))
                .andExpect(jsonPath("$[0].status").value("APPLIED"))
                .andExpect(jsonPath("$[0].daysStale").value(15));

        // A shorter window catches the same application; the fresh one is
        // still excluded because it was touched today.
        mockMvc.perform(get("/api/applications/stale?days=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // A window longer than the application's age returns nothing.
        mockMvc.perform(get("/api/applications/stale?days=30")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Out-of-range thresholds are a client error, not a 500.
        mockMvc.perform(get("/api/applications/stale?days=0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/applications/stale?days=91")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        // Stale results are owner-scoped.
        String other = register("stale-other@example.com");
        mockMvc.perform(get("/api/applications/stale").header("Authorization", "Bearer " + other))
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
