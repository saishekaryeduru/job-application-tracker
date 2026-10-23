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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void weeklyDigestCoversSubmissionsRoundsOutcomesAndFollowUps() throws Exception {
        String token = register("digest@example.com");
        Instant inWeek = Instant.now();
        Instant lastWeek = Instant.now().minus(8, ChronoUnit.DAYS);

        long heldApp = createApplicationReturningId(token, "Digest Held Co", "Backend Engineer");
        long quietApp = createApplicationReturningId(token, "Digest Quiet Co", "Platform Engineer");
        createApplication(token, "Digest Fresh Co", "Frontend Engineer", "Remote - US", "APPLIED");

        // Two rounds were held this week, one is still scheduled, one was
        // cancelled, and one held round from last week is out of scope.
        createInterviewReturningId(token, heldApp, "Recruiter screen", inWeek, "PASSED");
        createInterviewReturningId(token, heldApp, "Technical deep dive", inWeek, "FAILED");
        createInterviewReturningId(token, heldApp, "Onsite loop", inWeek, null);
        createInterviewReturningId(token, heldApp, "Hiring manager", inWeek, "CANCELLED");
        createInterviewReturningId(token, heldApp, "Last week screen", lastWeek, "PASSED");

        // Quiet Co has had no movement in 15 days, so it is a follow-up;
        // the other applications were touched today and are not.
        var owner = userRepository.findByEmail("digest@example.com").orElseThrow();
        Field updatedAt = com.saishekar.jobtracker.model.JobApplication.class
                .getDeclaredField("updatedAt");
        updatedAt.setAccessible(true);
        for (var app : applicationRepository.findAllByOwnerOrderByAppliedDateDescIdDesc(owner)) {
            if (app.getCompany().equals("Digest Quiet Co")) {
                updatedAt.set(app, Instant.now().minus(15, ChronoUnit.DAYS));
                applicationRepository.save(app);
            }
        }

        mockMvc.perform(get("/api/analytics/weekly-digest")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").exists())
                .andExpect(jsonPath("$.weekEnd").exists())
                .andExpect(jsonPath("$.applicationsSubmitted").value(3))
                .andExpect(jsonPath("$.submitted.length()").value(3))
                .andExpect(jsonPath("$.roundsHeldCount").value(2))
                .andExpect(jsonPath("$.roundsHeld.length()").value(2))
                .andExpect(jsonPath("$.roundsHeld[0].company").value("Digest Held Co"))
                .andExpect(jsonPath("$.outcomes.PASSED").value(1))
                .andExpect(jsonPath("$.outcomes.FAILED").value(1))
                .andExpect(jsonPath("$.outcomes.SCHEDULED").value(1))
                .andExpect(jsonPath("$.outcomes.CANCELLED").value(1))
                .andExpect(jsonPath("$.outcomes.NO_SHOW").value(0))
                .andExpect(jsonPath("$.followUpsDueCount").value(1))
                .andExpect(jsonPath("$.followUpsDue.length()").value(1))
                .andExpect(jsonPath("$.followUpsDue[0].company").value("Digest Quiet Co"))
                .andExpect(jsonPath("$.followUpsDue[0].daysStale").value(15));

        // The digest is owner-scoped: another user sees an empty week.
        String other = register("digest-other@example.com");
        mockMvc.perform(get("/api/analytics/weekly-digest")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationsSubmitted").value(0))
                .andExpect(jsonPath("$.roundsHeldCount").value(0))
                .andExpect(jsonPath("$.followUpsDueCount").value(0));
    }

    @Test
    void weeklyDigestCanLookBackAtAPastWeek() throws Exception {
        String token = register("digest-past@example.com");
        // Monday of last week and a moment inside it. Dates are derived
        // from today so the test holds on any weekday, including Monday
        // itself, when "last week" is still seven days back.
        LocalDate lastMonday = LocalDate.now().with(java.time.DayOfWeek.MONDAY).minusWeeks(1);
        Instant lastWeekMoment = lastMonday.plusDays(2)
                .atTime(15, 0).toInstant(ZoneOffset.UTC);

        // One application applied last week, one applied today.
        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Past Week Co\",\"title\":\"Backend Engineer\","
                                + "\"appliedDate\":\"" + lastMonday + "\"}"))
                .andExpect(status().isCreated());
        long currentApp = createApplicationReturningId(token, "Current Week Co", "Backend Engineer");

        createInterviewReturningId(token, currentApp, "Last week screen", lastWeekMoment, "PASSED");
        createInterviewReturningId(token, currentApp, "This week screen", Instant.now(), "PASSED");

        // Without a parameter the digest is still the current week only.
        mockMvc.perform(get("/api/analytics/weekly-digest")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationsSubmitted").value(1))
                .andExpect(jsonPath("$.submitted[0].company").value("Current Week Co"))
                .andExpect(jsonPath("$.roundsHeldCount").value(1));

        // Any date inside last week selects that whole Monday-Sunday week.
        for (String anyDay : new String[] {lastMonday.toString(), lastMonday.plusDays(3).toString()}) {
            mockMvc.perform(get("/api/analytics/weekly-digest?week=" + anyDay)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.weekStart").value(lastMonday.toString()))
                    .andExpect(jsonPath("$.weekEnd").value(lastMonday.plusDays(6).toString()))
                    .andExpect(jsonPath("$.applicationsSubmitted").value(1))
                    .andExpect(jsonPath("$.submitted[0].company").value("Past Week Co"))
                    .andExpect(jsonPath("$.roundsHeldCount").value(1))
                    .andExpect(jsonPath("$.roundsHeld[0].round").value("Last week screen"))
                    .andExpect(jsonPath("$.outcomes.PASSED").value(1));
        }

        // A week that has not started yet and a malformed date are both
        // client errors in the standard error shape, not empty digests.
        String nextMonday = LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(1).toString();
        mockMvc.perform(get("/api/analytics/weekly-digest?week=" + nextMonday)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("future")));
        mockMvc.perform(get("/api/analytics/weekly-digest?week=not-a-date")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ISO date")));
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

    @Test
    void interviewPrepNotesRoundTripAndUpdate() throws Exception {
        String token = register("prep@example.com");

        MvcResult created = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Prep Co\",\"title\":\"Backend Engineer\",\"appliedDate\":\""
                                + LocalDate.now() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long appId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        // Prep notes are stored as Markdown source and returned verbatim.
        String prep = "## Focus\\n- Review **caching** tradeoffs\\n- Rehearse the migration story";
        MvcResult interview = mockMvc.perform(post("/api/applications/" + appId + "/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round\":\"Technical deep dive\",\"scheduledAt\":\"2030-02-01T15:00:00Z\","
                                + "\"prepNotes\":\"" + prep + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prepNotes").value(prep.replace("\\n", "\n")))
                .andReturn();
        long interviewId = objectMapper.readTree(interview.getResponse().getContentAsString())
                .get("id").asLong();

        // Update rewrites the prep notes and records the outcome; an update
        // that omits the outcome must not reset it afterwards.
        String revised = "# Revised\\nShip the **second** draft";
        mockMvc.perform(put("/api/applications/" + appId + "/interviews/" + interviewId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round\":\"Technical deep dive\",\"scheduledAt\":\"2030-02-01T16:00:00Z\","
                                + "\"outcome\":\"PASSED\",\"prepNotes\":\"" + revised + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PASSED"))
                .andExpect(jsonPath("$.prepNotes").value(revised.replace("\\n", "\n")));
        mockMvc.perform(put("/api/applications/" + appId + "/interviews/" + interviewId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round\":\"Technical deep dive\",\"scheduledAt\":\"2030-02-01T16:00:00Z\","
                                + "\"prepNotes\":\"" + revised + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PASSED"));

        // The list endpoint serves the stored notes to the owner.
        mockMvc.perform(get("/api/applications/" + appId + "/interviews")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prepNotes").value(revised.replace("\\n", "\n")));

        // Another user gets a 404, not the interview: no existence leak.
        String other = register("prep-other@example.com");
        mockMvc.perform(put("/api/applications/" + appId + "/interviews/" + interviewId)
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round\":\"Hijack\",\"scheduledAt\":\"2030-02-01T16:00:00Z\"}"))
                .andExpect(status().isNotFound());

        // Oversized prep notes are a validation error, not a silent truncation.
        String tooLong = "x".repeat(20_001);
        mockMvc.perform(post("/api/applications/" + appId + "/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round\":\"Onsite\",\"scheduledAt\":\"2030-02-02T15:00:00Z\","
                                + "\"prepNotes\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.prepNotes").exists());
    }

    @Test
    void interviewDurationDefaultsValidatesAndDrivesCalendarEnd() throws Exception {
        String token = register("duration@example.com");
        long appId = createApplicationReturningId(token, "Duration Co", "Backend Engineer");
        Instant start = Instant.parse("2030-03-04T15:00:00Z");
        String interviewsUrl = "/api/applications/" + appId + "/interviews";

        // No duration stated: the one-hour default the calendar export
        // used to assume for every round stands.
        mockMvc.perform(post(interviewsUrl)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round\":\"Hiring manager\",\"scheduledAt\":\"" + start + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(60));

        // A stated duration is stored and returned.
        MvcResult created = mockMvc.perform(post(interviewsUrl)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round\":\"Recruiter screen\",\"scheduledAt\":\"" + start
                                + "\",\"durationMinutes\":30}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andReturn();
        long screenId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        // The calendar export ends the event after the round's own
        // duration, not after a fixed hour.
        MvcResult single = mockMvc.perform(
                        get(interviewsUrl + "/" + screenId + "/calendar.ics")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String ics = single.getResponse().getContentAsString(StandardCharsets.UTF_8);
        DateTimeFormatter utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC);
        org.junit.jupiter.api.Assertions.assertTrue(
                ics.contains("DTSTART:" + utc.format(start)), ics);
        org.junit.jupiter.api.Assertions.assertTrue(
                ics.contains("DTEND:" + utc.format(start.plus(30, ChronoUnit.MINUTES))), ics);

        // Out-of-range durations are validation errors with per-field
        // detail, not silent clamping: too short to be a real round, and
        // longer than a full-day onsite block.
        for (int bad : new int[] {10, 481}) {
            mockMvc.perform(post(interviewsUrl)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"round\":\"Onsite\",\"scheduledAt\":\"" + start
                                    + "\",\"durationMinutes\":" + bad + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.durationMinutes").exists());
        }

        // An update that omits the duration keeps the recorded length —
        // the same null-means-unstated rule the outcome already follows,
        // so editing prep notes cannot reset a 30-minute screen to an hour.
        mockMvc.perform(put(interviewsUrl + "/" + screenId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round\":\"Recruiter screen\",\"scheduledAt\":\"" + start
                                + "\",\"prepNotes\":\"Rehearse the caching story\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.prepNotes").value("Rehearse the caching story"));
    }

    @Test
    void upcomingInterviewsAndCalendarExport() throws Exception {
        String token = register("upcoming@example.com");
        Instant soon = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant later = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);

        long firstApp = createApplicationReturningId(token, "Acme, Inc.; West", "Backend Engineer");
        long secondApp = createApplicationReturningId(token, "Beta Health", "Frontend Engineer");

        // Soon counts as upcoming; a round ten days out is outside a 48h
        // window but inside a 30-day one; a past round and a cancelled
        // round never count, on either application.
        long soonId = createInterviewReturningId(token, firstApp, "Recruiter screen", soon, null);
        createInterviewReturningId(token, firstApp, "Onsite loop", later, null);
        createInterviewReturningId(token, secondApp, "Technical deep dive", past, null);
        createInterviewReturningId(token, secondApp, "Hiring manager", soon, "CANCELLED");

        mockMvc.perform(get("/api/interviews/upcoming").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].round").value("Recruiter screen"))
                .andExpect(jsonPath("$[0].company").value("Acme, Inc.; West"))
                .andExpect(jsonPath("$[0].applicationId").value(firstApp));
        mockMvc.perform(get("/api/interviews/upcoming?withinHours=720")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].round").value("Onsite loop"));

        // Out-of-range windows are a client error, not a 500.
        mockMvc.perform(get("/api/interviews/upcoming?withinHours=0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/interviews/upcoming?withinHours=721")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        // Upcoming is owner-scoped.
        String other = register("upcoming-other@example.com");
        mockMvc.perform(get("/api/interviews/upcoming").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // The single-round export is a well-formed calendar: CRLF endings,
        // a stable UID, UTC start/end one hour apart, both reminder alarms,
        // and RFC 5545 escaping of the comma and semicolon in the company.
        MvcResult single = mockMvc.perform(
                        get("/api/applications/" + firstApp + "/interviews/" + soonId + "/calendar.ics")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(header().string("Content-Disposition",
                        containsString("interview-" + soonId + ".ics")))
                .andReturn();
        String ics = single.getResponse().getContentAsString(StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"), ics);
        org.junit.jupiter.api.Assertions.assertTrue(ics.contains("UID:interview-" + soonId + "@job-tracker"), ics);
        org.junit.jupiter.api.Assertions.assertTrue(
                ics.contains("SUMMARY:Recruiter screen - Backend Engineer at Acme\\, Inc.\\; West"), ics);
        org.junit.jupiter.api.Assertions.assertTrue(ics.contains("TRIGGER:-P1D"), ics);
        org.junit.jupiter.api.Assertions.assertTrue(ics.contains("TRIGGER:-PT1H"), ics);
        org.junit.jupiter.api.Assertions.assertFalse(ics.contains("\n\n"), ics);
        for (String line : ics.split("\r\n")) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    line.getBytes(StandardCharsets.UTF_8).length <= 75,
                    "line exceeds 75 octets: " + line);
        }

        // The combined export holds both future rounds and neither the
        // past one nor the cancelled one.
        MvcResult all = mockMvc.perform(get("/api/interviews/calendar.ics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("job-tracker-interviews.ics")))
                .andReturn();
        String combined = all.getResponse().getContentAsString(StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertEquals(2,
                combined.split("BEGIN:VEVENT").length - 1, combined);
        org.junit.jupiter.api.Assertions.assertFalse(combined.contains("Hiring manager"), combined);

        // Another user cannot export someone else's round: 404, no leak.
        mockMvc.perform(get("/api/applications/" + firstApp + "/interviews/" + soonId + "/calendar.ics")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    private long createApplicationReturningId(String token, String company, String title)
            throws Exception {
        MvcResult created = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"" + company + "\",\"title\":\"" + title
                                + "\",\"appliedDate\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private long createInterviewReturningId(String token, long applicationId, String round,
                                            Instant scheduledAt, String outcome) throws Exception {
        String outcomeField = outcome == null ? "" : ",\"outcome\":\"" + outcome + "\"";
        MvcResult created = mockMvc.perform(post("/api/applications/" + applicationId + "/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"round\":\"" + round + "\",\"scheduledAt\":\"" + scheduledAt
                                + "\"" + outcomeField + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
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
