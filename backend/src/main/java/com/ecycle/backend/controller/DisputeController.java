package com.ecycle.backend.controller;

import com.ecycle.backend.config.Sec;
import com.ecycle.backend.service.DisputeService;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class DisputeController {
    private final DisputeService disputes;

    @PostMapping("/disputes") @PreAuthorize("hasAnyRole('BUYER','SELLER')") @ResponseStatus(HttpStatus.CREATED)
    public DisputeDto create(@Valid @RequestBody DisputeRequest r) { return disputes.create(Sec.me(), r); }
    @GetMapping("/disputes/mine")
    public List<DisputeDto> mine() { return disputes.mine(Sec.me()); }
    @GetMapping("/disputes/{id}")
    public DisputeDetailDto detail(@PathVariable Long id) { return disputes.detail(Sec.me(), id); }
    @PostMapping(value = "/disputes/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    public EvidenceDto evidence(@PathVariable Long id, @RequestParam("file") MultipartFile file, @RequestParam EvidenceCategory category, @RequestParam(required = false) String description) {
        return disputes.uploadEvidence(Sec.me(), id, file, category, description);
    }
    @GetMapping("/disputes/{id}/evidence/{evidenceId}/file")
    public ResponseEntity<org.springframework.core.io.Resource> evidenceFile(@PathVariable Long id, @PathVariable Long evidenceId) {
        var f = disputes.evidenceFile(Sec.me(), id, evidenceId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType())).header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + f.name().replace("\"", "") + "\"").body(f.resource());
    }
    @PostMapping("/disputes/{id}/messages")
    public MessageDto message(@PathVariable Long id, @Valid @RequestBody MessageRequest r) { return disputes.post(Sec.me(), id, r); }

    // resolver workspace (SecurityConfig restricts /api/resolver/** to RESOLVER)
    @GetMapping("/resolver/disputes")
    public List<DisputeDto> queue(@RequestParam(required = false) DisputeStatus status, @RequestParam(required = false) DisputePriority priority) { return disputes.queue(status, priority); }
    @GetMapping("/resolver/history")
    public List<DisputeDto> history() { return disputes.history(Sec.me()); }
    @PostMapping("/resolver/disputes/{id}/assign")
    public DisputeDto assign(@PathVariable Long id, @RequestBody(required = false) AssignRequest r) { return disputes.assign(Sec.me(), id, r == null ? null : r.resolverId()); }
    @PutMapping("/resolver/disputes/{id}/priority")
    public DisputeDto priority(@PathVariable Long id, @Valid @RequestBody PriorityRequest r) { return disputes.setPriority(Sec.me(), id, r.priority()); }
    @PostMapping("/resolver/disputes/{id}/resolve")
    public DisputeDetailDto resolve(@PathVariable Long id, @Valid @RequestBody ResolutionRequest r) { return disputes.resolve(Sec.me(), id, r); }
    @PostMapping("/resolver/disputes/{id}/reject")
    public DisputeDetailDto reject(@PathVariable Long id, @Valid @RequestBody RejectRequest r) { return disputes.reject(Sec.me(), id, r.reason()); }
}
