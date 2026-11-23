package com.ecycle.backend.controller;

import com.ecycle.backend.assistant.AssistantService;
import com.ecycle.backend.config.Sec;
import com.ecycle.shared.Dtos.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/assistant") @RequiredArgsConstructor
public class AssistantController {
    private final AssistantService svc;
    @PostMapping("/chat") public AssistantReplyDto chat(@Valid @RequestBody AssistantRequest r) { return svc.chat(Sec.me(), r); }
    @GetMapping("/status") public Map<String, Object> status() { return svc.status(); }
    @GetMapping("/conversations") public List<ConversationDto> conversations() { return svc.conversations(Sec.me()); }
    @GetMapping("/conversations/{id}") public List<AssistantMessageDto> history(@PathVariable String id) { return svc.history(Sec.me(), id); }
    @DeleteMapping("/conversations/{id}") public Msg delete(@PathVariable String id) { svc.delete(Sec.me(), id); return new Msg("Deleted"); }
    @GetMapping("/actions") public List<PendingActionDto> pending() { return svc.pendingActions(Sec.me()); }
    @PostMapping("/actions/{id}/confirm") public ActionResultDto confirm(@PathVariable String id) { return svc.confirm(Sec.me(), id); }
    @PostMapping("/actions/{id}/cancel") public ActionResultDto cancel(@PathVariable String id) { return svc.cancel(Sec.me(), id); }
}
