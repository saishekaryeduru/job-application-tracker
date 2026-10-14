package com.saishekar.jobtracker.application;

import com.saishekar.jobtracker.application.ApplicationDtos.ApplicationRequest;
import com.saishekar.jobtracker.common.ApiException;
import com.saishekar.jobtracker.model.ApplicationStatus;
import com.saishekar.jobtracker.model.JobApplication;
import com.saishekar.jobtracker.model.User;
import com.saishekar.jobtracker.repository.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV export and import for applications.
 *
 * The format is the export format: the header row exported here is the header
 * row the importer requires, so a file produced by one account can be imported
 * into another. Import is all-or-nothing — every row is validated before any
 * row is saved, and a bad file changes nothing.
 */
@Service
public class ApplicationCsvService {

    /** Sanity cap for a hand-maintained tracker; also bounds a bad upload. */
    static final int MAX_IMPORT_ROWS = 1000;

    static final List<String> COLUMNS = List.of(
            "company", "title", "location", "jobUrl", "status",
            "appliedDate", "salaryMin", "salaryMax", "notes");

    private final JobApplicationRepository applications;
    private final ApplicationService applicationService;

    public ApplicationCsvService(JobApplicationRepository applications,
                                 ApplicationService applicationService) {
        this.applications = applications;
        this.applicationService = applicationService;
    }

    @Transactional(readOnly = true)
    public String exportCsv(User owner) {
        StringBuilder out = new StringBuilder();
        out.append(String.join(",", COLUMNS)).append("\r\n");
        for (JobApplication app : applications.findAllByOwnerOrderByAppliedDateDescIdDesc(owner)) {
            out.append(row(
                    app.getCompany(),
                    app.getTitle(),
                    app.getLocation(),
                    app.getJobUrl(),
                    app.getStatus().name(),
                    app.getAppliedDate().toString(),
                    app.getSalaryMin() == null ? "" : app.getSalaryMin().toString(),
                    app.getSalaryMax() == null ? "" : app.getSalaryMax().toString(),
                    app.getNotes()))
                    .append("\r\n");
        }
        return out.toString();
    }

    @Transactional
    public int importCsv(User owner, InputStream input) {
        List<List<String>> rows = parse(readAll(input));
        if (rows.isEmpty()) {
            throw ApiException.badRequest("CSV import failed: the file is empty");
        }
        Map<String, Integer> columns = headerIndex(rows.get(0));
        List<List<String>> dataRows = rows.subList(1, rows.size()).stream()
                .filter(row -> row.stream().anyMatch(value -> !value.isBlank()))
                .toList();
        if (dataRows.isEmpty()) {
            throw ApiException.badRequest("CSV import failed: the file has a header but no data rows");
        }
        if (dataRows.size() > MAX_IMPORT_ROWS) {
            throw ApiException.badRequest(
                    "CSV import failed: at most " + MAX_IMPORT_ROWS + " rows can be imported at once");
        }

        List<String> errors = new ArrayList<>();
        List<ApplicationRequest> requests = new ArrayList<>();
        for (int i = 0; i < dataRows.size(); i++) {
            // Row numbers count the header, so the first data row is row 2 —
            // the same numbers a spreadsheet shows the user.
            ApplicationRequest request = toRequest(dataRows.get(i), columns, i + 2, errors);
            if (request != null) {
                requests.add(request);
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("CSV import failed: " + String.join("; ", errors));
        }
        requests.forEach(request -> applicationService.create(owner, request));
        return requests.size();
    }

    private ApplicationRequest toRequest(List<String> row, Map<String, Integer> columns,
                                         int rowNumber, List<String> errors) {
        int errorsBefore = errors.size();
        String company = value(row, columns, "company");
        String title = value(row, columns, "title");
        if (company.isBlank()) {
            errors.add("row " + rowNumber + ": company is required");
        } else if (company.length() > 160) {
            errors.add("row " + rowNumber + ": company must be at most 160 characters");
        }
        if (title.isBlank()) {
            errors.add("row " + rowNumber + ": title is required");
        } else if (title.length() > 160) {
            errors.add("row " + rowNumber + ": title must be at most 160 characters");
        }

        String location = value(row, columns, "location");
        if (location.length() > 160) {
            errors.add("row " + rowNumber + ": location must be at most 160 characters");
        }
        String jobUrl = value(row, columns, "jobUrl");
        if (jobUrl.length() > 500) {
            errors.add("row " + rowNumber + ": jobUrl must be at most 500 characters");
        }

        ApplicationStatus status = ApplicationStatus.APPLIED;
        String statusText = value(row, columns, "status");
        if (!statusText.isBlank()) {
            try {
                status = ApplicationStatus.valueOf(statusText.toUpperCase());
            } catch (IllegalArgumentException ex) {
                errors.add("row " + rowNumber + ": status '" + statusText + "' is not a valid status");
            }
        }

        LocalDate appliedDate = null;
        String dateText = value(row, columns, "appliedDate");
        if (dateText.isBlank()) {
            errors.add("row " + rowNumber + ": appliedDate is required");
        } else {
            try {
                appliedDate = LocalDate.parse(dateText);
            } catch (DateTimeParseException ex) {
                errors.add("row " + rowNumber + ": appliedDate '" + dateText
                        + "' must be an ISO date (yyyy-MM-dd)");
            }
        }

        Integer salaryMin = parseSalary(value(row, columns, "salaryMin"), "salaryMin", rowNumber, errors);
        Integer salaryMax = parseSalary(value(row, columns, "salaryMax"), "salaryMax", rowNumber, errors);
        if (salaryMin != null && salaryMax != null && salaryMin > salaryMax) {
            errors.add("row " + rowNumber + ": salaryMin must not exceed salaryMax");
        }

        if (errors.size() > errorsBefore) {
            return null;
        }
        return new ApplicationRequest(company, title,
                location.isBlank() ? null : location,
                jobUrl.isBlank() ? null : jobUrl,
                status, appliedDate, salaryMin, salaryMax,
                value(row, columns, "notes").isBlank() ? null : value(row, columns, "notes"));
    }

    private Integer parseSalary(String text, String field, int rowNumber, List<String> errors) {
        if (text.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(text);
            if (parsed < 0) {
                errors.add("row " + rowNumber + ": " + field + " must not be negative");
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            errors.add("row " + rowNumber + ": " + field + " '" + text + "' must be a whole number");
            return null;
        }
    }

    /** Maps normalized header names to column positions; the export headers are required. */
    private Map<String, Integer> headerIndex(List<String> header) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            index.put(normalize(header.get(i)), i);
        }
        List<String> missing = new ArrayList<>();
        for (String column : COLUMNS) {
            if (!index.containsKey(normalize(column))) {
                missing.add(column);
            }
        }
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("CSV import failed: missing column(s) "
                    + String.join(", ", missing) + " — use the export format");
        }
        return index;
    }

    private String value(List<String> row, Map<String, Integer> columns, String column) {
        int position = columns.get(normalize(column));
        return position < row.size() ? row.get(position).trim() : "";
    }

    /** Header matching ignores case, underscores, and spaces: applied_date == appliedDate. */
    private String normalize(String header) {
        return header.replace("\uFEFF", "").replace("_", "").replace(" ", "")
                .trim().toLowerCase();
    }

    private String readAll(InputStream input) {
        try {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw ApiException.badRequest("CSV import failed: the uploaded file could not be read");
        }
    }

    private String row(String... fields) {
        List<String> escaped = new ArrayList<>(fields.length);
        for (String field : fields) {
            escaped.add(escape(field));
        }
        return String.join(",", escaped);
    }

    /** RFC 4180: quote fields containing a comma, quote, or newline; double embedded quotes. */
    private String escape(String field) {
        if (field == null || field.isEmpty()) {
            return "";
        }
        if (field.contains(",") || field.contains("\"")
                || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    /**
     * Minimal RFC 4180 parser: quoted fields may contain commas, newlines,
     * and doubled quotes. A quote only opens a quoted field at the start of
     * a field, so an apostrophe-style quote mid-value stays literal.
     */
    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && !fieldStarted) {
                inQuotes = true;
                fieldStarted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        if (inQuotes) {
            throw ApiException.badRequest("CSV import failed: unterminated quoted field");
        }
        if (fieldStarted || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
