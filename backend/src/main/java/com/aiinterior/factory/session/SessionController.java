package com.aiinterior.factory.session;

import com.aiinterior.factory.agents.Payloads.MaterialsResult;
import com.aiinterior.factory.common.ApiException;
import com.aiinterior.factory.domain.DesignBrief;
import com.aiinterior.factory.export.ExportWorkspace;
import com.aiinterior.factory.materials.BoqCsvWriter;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    public record SessionSummary(String id, String projectName, String roomName, String roomType, SessionStatus status,
                                 String statusMessage, int designVersion, Instant createdAt) {
    }

    private final SessionService service;
    private final SessionEvents events;
    private final ExportWorkspace workspace;

    public SessionController(SessionService service, SessionEvents events, ExportWorkspace workspace) {
        this.service = service;
        this.events = events;
        this.workspace = workspace;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DesignSession start(@Valid @RequestBody DesignBrief brief) {
        return service.start(brief);
    }

    @GetMapping
    public List<SessionSummary> list() {
        return service.list().stream().map(s -> new SessionSummary(s.getId(), s.getBrief().projectName(), s.getBrief().roomName(),
                s.getBrief().roomType().label(), s.getStatus(), s.getStatusMessage(), s.getDesignVersion(), s.getCreatedAt())).toList();
    }

    @GetMapping("/{id}")
    public DesignSession get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id) {
        service.get(id);
        return events.subscribe(id);
    }

    @PostMapping("/{id}/approval")
    public DesignSession approve(@PathVariable String id, @Valid @RequestBody ApprovalDecision decision) {
        return service.decide(id, decision);
    }

    @PostMapping("/{id}/cancel")
    public DesignSession cancel(@PathVariable String id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/export/verify")
    public DesignSession verify(@PathVariable String id) {
        return service.verifyExport(id);
    }

    @GetMapping("/{id}/boq.csv")
    public ResponseEntity<byte[]> boq(@PathVariable String id) {
        DesignSession s = service.get(id);
        MaterialsResult m = s.output("materials", MaterialsResult.class);
        if (m == null) {
            throw ApiException.notFound("BOQ for session", id);
        }
        byte[] csv = BoqCsvWriter.toCsv(s.getBrief().roomName(), m.boq()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("boq-" + id + ".csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @GetMapping("/{id}/files/{name}")
    public ResponseEntity<Resource> file(@PathVariable String id, @PathVariable String name,
                                         @RequestParam(required = false) Integer version) throws IOException {
        DesignSession s = service.get(id);
        Path p = workspace.resolveDownload(s.getId(), version == null ? s.getDesignVersion() : version, name);
        if (!Files.isRegularFile(p)) {
            throw ApiException.notFound("File", name);
        }
        MediaType type = name.endsWith(".svg") ? MediaType.valueOf("image/svg+xml")
                : name.endsWith(".json") ? MediaType.APPLICATION_JSON
                : name.endsWith(".csv") ? new MediaType("text", "csv") : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(type)
                .body(new FileSystemResource(p));
    }
}
