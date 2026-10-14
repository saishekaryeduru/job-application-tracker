package com.saishekar.jobtracker.application;

import com.saishekar.jobtracker.application.ApplicationDtos.ApplicationPage;
import com.saishekar.jobtracker.application.ApplicationDtos.ApplicationRequest;
import com.saishekar.jobtracker.application.ApplicationDtos.ApplicationResponse;
import com.saishekar.jobtracker.application.ApplicationDtos.CsvImportResult;
import com.saishekar.jobtracker.application.ApplicationDtos.StatusUpdateRequest;
import com.saishekar.jobtracker.common.ApiException;
import com.saishekar.jobtracker.model.ApplicationStatus;
import com.saishekar.jobtracker.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;
    private final ApplicationCsvService csvService;
    private final CurrentUser currentUser;

    public ApplicationController(ApplicationService service, ApplicationCsvService csvService,
                                 CurrentUser currentUser) {
        this.service = service;
        this.csvService = csvService;
        this.currentUser = currentUser;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv() {
        String csv = csvService.exportCsv(currentUser.require());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("job-applications.csv", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers)
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportResult importCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiException.badRequest("CSV import failed: the uploaded file is empty");
        }
        try {
            return new CsvImportResult(csvService.importCsv(currentUser.require(), file.getInputStream()));
        } catch (IOException ex) {
            throw ApiException.badRequest("CSV import failed: the uploaded file could not be read");
        }
    }

    @GetMapping
    public ApplicationPage list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApplicationPage.from(service.list(currentUser.require(), status, q, page, size));
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable Long id) {
        return ApplicationResponse.from(service.get(currentUser.require(), id));
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody ApplicationRequest request) {
        var created = service.create(currentUser.require(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationResponse.from(created));
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@PathVariable Long id,
                                      @Valid @RequestBody ApplicationRequest request) {
        return ApplicationResponse.from(service.update(currentUser.require(), id, request));
    }

    @PatchMapping("/{id}/status")
    public ApplicationResponse updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody StatusUpdateRequest request) {
        return ApplicationResponse.from(
                service.updateStatus(currentUser.require(), id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }
}
