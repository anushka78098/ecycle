package com.ecycle.backend.controller;

import com.ecycle.backend.config.Sec;
import com.ecycle.backend.service.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.Pathway;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Eco-points, dashboards, challenges, badges, leaderboard, rewards (admin management lives under /api/admin/eco). */
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class EcoController {
    private final EcoService eco; private final SanitizationWorkflowService wipes;

    @GetMapping("/eco/dashboard") public EcoDashboardDto dashboard() { return eco.dashboard(Sec.me()); }
    @GetMapping("/eco/history") public List<LedgerDto> history() { return eco.history(Sec.me()); }
    @GetMapping("/eco/badges") public List<BadgeDto> badges() { return eco.myBadges(Sec.me()); }
    @GetMapping("/eco/challenges") public List<ChallengeDto> challenges() { return eco.myChallenges(Sec.me()); }
    @GetMapping("/eco/leaderboard") public List<LeaderboardEntryDto> leaderboard(@RequestParam(defaultValue = "20") int limit) { return eco.leaderboard(Sec.me(), Math.max(1, Math.min(100, limit))); }
    @PutMapping("/eco/leaderboard/visibility") public Msg visibility(@RequestParam boolean visible) { eco.setLeaderboardVisible(Sec.me(), visible); return new Msg(visible ? "You appear on the leaderboard" : "You are hidden from the leaderboard"); }
    @GetMapping("/eco/rewards") public List<RewardDto> rewards() { return eco.rewards(Sec.me(), false); }
    @PostMapping("/eco/rewards/{id}/redeem") @ResponseStatus(HttpStatus.CREATED) public RedemptionDto redeem(@PathVariable Long id) { return eco.redeem(Sec.me(), id); }
    @GetMapping("/eco/redemptions") public List<RedemptionDto> redemptions() { return eco.redemptions(Sec.me()); }
    @GetMapping("/eco/estimate")
    public EstimateDto estimate(@RequestParam Long categoryId, @RequestParam BigDecimal weightKg, @RequestParam(defaultValue = "1") int quantity, @RequestParam(defaultValue = "RECYCLE") Pathway pathway) {
        return eco.estimate(categoryId, weightKg, Math.max(1, Math.min(1000, quantity)), pathway);
    }

    // ---- admin
    @GetMapping("/admin/eco/rewards") public List<RewardDto> allRewards() { return eco.rewards(Sec.me(), true); }
    @PostMapping("/admin/eco/rewards") @ResponseStatus(HttpStatus.CREATED) public RewardDto addReward(@Valid @RequestBody RewardRequest r) { return eco.saveReward(Sec.me().id(), null, r); }
    @PutMapping("/admin/eco/rewards/{id}") public RewardDto updateReward(@PathVariable Long id, @Valid @RequestBody RewardRequest r) { return eco.saveReward(Sec.me().id(), id, r); }
    @GetMapping("/admin/eco/challenges") public List<ChallengeDto> allChallenges() { return eco.allChallenges(Sec.me()); }
    @PostMapping("/admin/eco/challenges") @ResponseStatus(HttpStatus.CREATED) public ChallengeDto addChallenge(@Valid @RequestBody ChallengeRequest r) { return eco.saveChallenge(Sec.me().id(), null, r); }
    @PutMapping("/admin/eco/challenges/{id}") public ChallengeDto updateChallenge(@PathVariable Long id, @Valid @RequestBody ChallengeRequest r) { return eco.saveChallenge(Sec.me().id(), id, r); }

    // ---- wipe checklists (data sanitization workflow)
    @GetMapping("/listings/{id}/wipe-checklist") public ChecklistTemplateDto wipeTemplate(@PathVariable Long id) { return wipes.template(id); }
    @GetMapping("/listings/{id}/wipe-checklists") public List<ChecklistDto> wipeHistory(@PathVariable Long id) { return wipes.forListing(Sec.me(), id); }
    @PostMapping("/listings/{id}/wipe-checklist") @org.springframework.security.access.prepost.PreAuthorize("hasRole('SELLER')") @ResponseStatus(HttpStatus.CREATED)
    public ChecklistDto submitWipe(@PathVariable Long id, @Valid @RequestBody ChecklistSubmitRequest r) { return wipes.submitForListing(Sec.me(), id, r); }
    @GetMapping("/categories/{id}/wipe-checklist") public ChecklistTemplateDto categoryTemplate(@PathVariable Long id) { return wipes.templateFor(null, id); }
}
