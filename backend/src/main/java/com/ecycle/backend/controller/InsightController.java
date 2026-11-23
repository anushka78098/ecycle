package com.ecycle.backend.controller;

import com.ecycle.backend.config.Sec;
import com.ecycle.backend.service.*;
import com.ecycle.shared.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Dashboards, impact, recommendations, passports, notifications and chat. */
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class InsightController {
    private final DashboardService dashboards; private final EnvironmentalService env; private final RecommendationService recs; private final PassportService passports;
    private final NotificationService notifications; private final ChatService chat;

    @GetMapping("/dashboard/{role}")
    public DashboardDto dashboard(@PathVariable String role) { return dashboards.forRole(Sec.me(), role); }
    @GetMapping("/impact/mine")
    public ImpactDto myImpact(@RequestParam(defaultValue = "buyer") String as) { return env.summary("seller".equalsIgnoreCase(as) ? "seller" : "buyer", Sec.me().id()); }
    @GetMapping("/impact/global")
    public ImpactDto globalImpact() { return env.summary("global", null); }

    @GetMapping("/recommendations") @PreAuthorize("hasRole('BUYER')")
    public List<RecommendationDto> recommendations(@RequestParam(defaultValue = "12") int limit) { return recs.recommend(Sec.me(), Math.max(1, Math.min(30, limit))); }
    @GetMapping("/requirements") @PreAuthorize("hasRole('BUYER')")
    public List<RequirementDto> requirements() { return recs.mine(Sec.me()); }
    @PostMapping("/requirements") @PreAuthorize("hasRole('BUYER')") @ResponseStatus(HttpStatus.CREATED)
    public RequirementDto addRequirement(@Valid @RequestBody RequirementRequest r) { return recs.save(Sec.me(), null, r); }
    @PutMapping("/requirements/{id}") @PreAuthorize("hasRole('BUYER')")
    public RequirementDto updateRequirement(@PathVariable Long id, @Valid @RequestBody RequirementRequest r) { return recs.save(Sec.me(), id, r); }
    @DeleteMapping("/requirements/{id}") @PreAuthorize("hasRole('BUYER')")
    public Msg deleteRequirement(@PathVariable Long id) { recs.delete(Sec.me(), id); return new Msg("Deleted"); }

    // digital passport
    @GetMapping("/passports/mine")
    public List<PassportDto> myPassports() { return passports.mine(Sec.me()); }
    @GetMapping("/passports/{code}")
    public PassportDto passport(@PathVariable String code) { return passports.view(Sec.me(), code); }
    @GetMapping(value = "/passports/{code}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qr(@PathVariable String code, @RequestParam(defaultValue = "320") int size) { return passports.qrPng(code, size); }
    @PostMapping("/passports/{code}/dismantle")
    public PassportDto dismantle(@PathVariable String code, @Valid @RequestBody DismantleRequest r) { return passports.dismantle(Sec.me(), code, r); }
    @PostMapping("/passports/{code}/component-action")
    public PassportDto componentAction(@PathVariable String code, @Valid @RequestBody ComponentActionRequest r) { return passports.componentAction(Sec.me(), code, r); }
    @PostMapping("/passports/{code}/outcome")
    public PassportDto outcome(@PathVariable String code, @Valid @RequestBody OutcomeRequest r) { return passports.outcome(Sec.me(), code, r); }

    // notifications
    @GetMapping("/notifications")
    public List<NotificationDto> notifications() { return notifications.mine(Sec.me().id()); }
    @GetMapping("/notifications/unread-count")
    public long unread() { return notifications.unread(Sec.me().id()); }
    @PostMapping("/notifications/{id}/read")
    public Msg read(@PathVariable String id) { notifications.markRead(Sec.me().id(), id); return new Msg("ok"); }
    @PostMapping("/notifications/read-all")
    public Msg readAll() { notifications.markAllRead(Sec.me().id()); return new Msg("ok"); }

    // chat
    @PostMapping("/chat") @ResponseStatus(HttpStatus.CREATED)
    public ChatDto send(@Valid @RequestBody ChatRequest r) { return chat.send(Sec.me(), r); }
    @GetMapping("/chat/thread")
    public List<ChatDto> thread(@RequestParam Long listingId, @RequestParam Long with) { return chat.thread(Sec.me(), listingId, with); }
    @GetMapping("/chat/conversations")
    public List<ChatDto> conversations() { return chat.conversations(Sec.me()); }
}
