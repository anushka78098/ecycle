package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.engine.EcoEngine;
import com.ecycle.backend.engine.EnvironmentalCalculator;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eco-points and gamification. Points live in an append-only ledger (PostgreSQL) with a denormalised per-user balance guarded by a row lock.
 * Every award is idempotent per (user, activity, reference), so retries and duplicate events can never pay twice. Point rates are admin-editable (score_config eco.*).
 * Carbon and weight figures come from the existing environmental module (EnvironmentalCalculator / EnvironmentalFactor), not from a second model.
 */
@Service @RequiredArgsConstructor
public class EcoService {
    private static final Instant FAR_FUTURE = Instant.parse("2200-01-01T00:00:00Z");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String METHOD_NOTE = "Carbon figures are ESTIMATES: weight x configurable per-kg factors from the environmental module. "
            + "Equivalents (tree-years, car km, phone charges) use rough public averages and are for illustration only.";
    private final EcoAccountRepository accounts; private final EcoLedgerRepository ledger; private final BadgeRepository badges; private final UserBadgeRepository userBadges;
    private final ChallengeRepository challenges; private final ChallengeCompletionRepository completions; private final RewardRepository rewards;
    private final RewardRedemptionRepository redemptions; private final ScoreConfigService config; private final EnvironmentalFactorRepository factors;
    private final ProductCategoryRepository categories; private final EnvironmentalImpactRepository impacts; private final Lookup lookup;
    private final NotificationService notifications; private final AuditService audit;
    private final EnvironmentalCalculator calc = new EnvironmentalCalculator(); private final SecureRandom rnd = new SecureRandom();

    // ------------------------------------------------------------------ configuration
    private double cfg(String key, double def) { return config.all().getOrDefault(key, def); }
    public double ratePerKg(Pathway p) {
        return switch (p) { case REUSE -> cfg("eco.pts.per_kg.reuse", 12); case REPAIR -> cfg("eco.pts.per_kg.repair", 10); case REFURBISH -> cfg("eco.pts.per_kg.refurbish", 9);
            case RECYCLE -> cfg("eco.pts.per_kg.recycle", 6); case DISPOSE -> 0; };
    }

    // ------------------------------------------------------------------ awarding
    private EcoAccount accountForUpdate(Long uid) {
        return accounts.lockByUserId(uid).orElseGet(() -> { EcoAccount a = new EcoAccount(); a.setUserId(uid); return accounts.saveAndFlush(a); });
    }

    /** Credits points (and weight / carbon statistics) once per (user, activity, ref). Returns false when it was already awarded or nothing to award. */
    @Transactional
    public boolean award(Long userId, int points, String activity, String refType, Long refId, double weightKg, double carbonKg, String note) {
        if (userId == null || (points <= 0 && weightKg <= 0 && carbonKg <= 0)) return false;
        if (refId != null && ledger.existsByUserIdAndActivityAndRefTypeAndRefId(userId, activity, refType, refId)) return false;
        EcoAccount a = accountForUpdate(userId);
        EcoLedger l = new EcoLedger(); l.setUserId(userId); l.setDelta(Math.max(0, points)); l.setActivity(activity); l.setRefType(refType); l.setRefId(refId);
        l.setWeightKg(BigDecimal.valueOf(weightKg).setScale(3, java.math.RoundingMode.HALF_UP)); l.setCarbonKg(BigDecimal.valueOf(carbonKg).setScale(3, java.math.RoundingMode.HALF_UP));
        l.setNote(note == null ? null : (note.length() > 300 ? note.substring(0, 300) : note)); ledger.save(l);
        a.setBalance(a.getBalance() + Math.max(0, points)); a.setLifetimePoints(a.getLifetimePoints() + Math.max(0, points)); accounts.save(a);
        if (points > 0) notifications.notify(userId, "+" + points + " eco-points", note == null ? activity : note, "ECO");
        evaluateBadges(userId);
        if (!"CHALLENGE_BONUS".equals(activity)) evaluateChallenges(userId);
        return true;
    }

    /** Called for every IMPACT_RECORDED audit event (existing environmental module): pays seller and buyer per kg diverted, by pathway. */
    @Transactional
    public void onImpact(Long impactId) {
        EnvironmentalImpact i = impacts.findById(impactId).orElse(null);
        if (i == null || i.getPathway() == Pathway.DISPOSE) return;
        double kg = i.getEwasteDivertedKg().doubleValue(), co = i.getCarbonAvoidedKg().doubleValue();
        int pts = EcoEngine.pointsForWeight(ratePerKg(i.getPathway()), kg);
        String act = "OUTCOME_" + i.getPathway(), note = "Listing #" + i.getListingId() + " " + i.getPathway().name().toLowerCase(Locale.ROOT) + "d: " + round(kg) + " kg diverted";
        award(i.getSellerId(), pts, act, "IMPACT", i.getId(), kg, co, note);
        if (i.getBuyerId() != null && !i.getBuyerId().equals(i.getSellerId())) award(i.getBuyerId(), pts, act, "IMPACT", i.getId(), kg, co, note);
    }
    @Transactional
    public void onSanitizationVerified(Long submitterId, Long recordId, String certNo) {
        award(submitterId, (int) cfg("eco.pts.sanitization", 25), "SANITIZATION_VERIFIED", "SANITIZATION", recordId, 0, 0, "Data sanitization verified (" + certNo + ")");
    }
    /**
     * Pickup verified by the recycler. When the pickup is tied to a listing, the per-kg points and carbon are paid by onImpact (the environmental module), so here only the
     * pickup bonus is paid to the requester and weight/carbon is credited to the recycler; otherwise the requester also receives per-kg points and carbon.
     */
    @Transactional
    public void onPickupCompleted(Pickup p, double actualKg, double carbonKg, boolean impactCountedElsewhere) {
        int bonus = (int) cfg("eco.pts.pickup_completed", 40) + (p.isHazardous() ? (int) cfg("eco.pts.hazard_bonus", 20) : 0);
        int perKg = impactCountedElsewhere ? 0 : EcoEngine.pointsForWeight(ratePerKg(Pathway.RECYCLE), actualKg);
        int total = bonus + perKg;
        String note = "Pickup " + p.getTrackingCode() + " verified by recycler: " + round(actualKg) + " kg";
        award(p.getRequesterId(), total, "PICKUP_COMPLETED", "PICKUP", p.getId(), impactCountedElsewhere ? 0 : actualKg, impactCountedElsewhere ? 0 : carbonKg, note);
        int share = (int) Math.round(total * cfg("eco.pts.recycler_share", 0.5));
        award(p.getRecyclerId(), share, "PICKUP_COMPLETED", "PICKUP", p.getId(), actualKg, carbonKg, "Processed pickup " + p.getTrackingCode() + ": " + round(actualKg) + " kg");
    }

    // ------------------------------------------------------------------ stats, badges, challenges
    private record Stats(Map<String, double[]> byActivity, int lifetime, long challengesDone) {
        double count(String a) { double[] r = byActivity.get(a); return r == null ? 0 : r[0]; }
        double weight() { return byActivity.values().stream().mapToDouble(r -> r[1]).sum(); }
        double carbon() { return byActivity.values().stream().mapToDouble(r -> r[2]).sum(); }
        double circular() { return byActivity.entrySet().stream().filter(e -> e.getKey().startsWith("OUTCOME_")).mapToDouble(e -> e.getValue()[0]).sum(); }
        double points() { return byActivity.entrySet().stream().filter(e -> !"CHALLENGE_BONUS".equals(e.getKey())).mapToDouble(e -> e.getValue()[3]).sum(); }
    }
    private Stats stats(Long uid, Instant from, Instant to) {
        Map<String, double[]> m = new HashMap<>();
        for (Object[] r : ledger.stats(uid, from, to)) m.put((String) r[0], new double[]{((Number) r[1]).doubleValue(), ((Number) r[2]).doubleValue(), ((Number) r[3]).doubleValue(), ((Number) r[4]).doubleValue()});
        int life = accounts.findById(uid).map(EcoAccount::getLifetimePoints).orElse(0);
        return new Stats(m, life, completions.countByUserId(uid));
    }
    private static double metric(String key, Stats s) {
        return switch (key) {
            case "PICKUPS_COMPLETED" -> s.count("PICKUP_COMPLETED"); case "SANITIZATIONS" -> s.count("SANITIZATION_VERIFIED"); case "CARBON_KG" -> s.carbon();
            case "WEIGHT_KG" -> s.weight(); case "CIRCULAR_ACTIONS" -> s.circular(); case "LIFETIME_POINTS" -> s.lifetime(); case "CHALLENGES" -> s.challengesDone();
            case "POINTS_EARNED" -> s.points(); default -> 0; };
    }
    public static final Set<String> METRICS = Set.of("PICKUPS_COMPLETED", "SANITIZATIONS", "CARBON_KG", "WEIGHT_KG", "CIRCULAR_ACTIONS", "POINTS_EARNED");

    private void evaluateBadges(Long uid) {
        Stats s = stats(uid, Instant.EPOCH, FAR_FUTURE);
        for (Badge b : badges.findAllByOrderBySortOrderAsc()) {
            if (userBadges.existsByUserIdAndBadgeCode(uid, b.getCode()) || metric(b.getCriteria(), s) < b.getThreshold().doubleValue()) continue;
            UserBadge ub = new UserBadge(); ub.setUserId(uid); ub.setBadgeCode(b.getCode()); userBadges.save(ub);
            notifications.notify(uid, "Badge earned: " + b.getName(), b.getDescription(), "ECO");
        }
    }
    private void evaluateChallenges(Long uid) {
        Instant now = Instant.now();
        for (Challenge c : challenges.findByActiveTrueOrderByIdAsc()) {
            if (c.getEndsAt() != null && c.getEndsAt().isBefore(now)) continue;
            if (completions.existsByChallengeIdAndUserId(c.getId(), uid)) continue;
            Stats s = stats(uid, c.getStartsAt(), c.getEndsAt() == null ? FAR_FUTURE : c.getEndsAt());
            if (metric(c.getMetric(), s) < c.getTarget().doubleValue()) continue;
            ChallengeCompletion cc = new ChallengeCompletion(); cc.setChallengeId(c.getId()); cc.setUserId(uid); cc.setBonusPoints(c.getRewardPoints()); completions.save(cc);
            award(uid, c.getRewardPoints(), "CHALLENGE_BONUS", "CHALLENGE", c.getId(), 0, 0, "Challenge completed: " + c.getTitle());
            notifications.notify(uid, "Challenge completed: " + c.getTitle(), "You earned " + c.getRewardPoints() + " bonus eco-points.", "ECO");
        }
    }

    // ------------------------------------------------------------------ dashboard
    @Transactional
    public EcoDashboardDto dashboard(AuthUser me) {
        EcoAccount a = accountForUpdate(me.id());
        evaluateBadges(me.id()); evaluateChallenges(me.id());   // catches anything missed (for example activity that pre-dates this feature)
        a = accounts.findById(me.id()).orElse(a);
        Stats all = stats(me.id(), Instant.EPOCH, FAR_FUTURE);
        int lvl = EcoEngine.level(a.getLifetimePoints());
        var eq = EcoEngine.equivalents(all.carbon());
        Map<String, Number> byMonth = new TreeMap<>();
        for (EcoLedger l : ledger.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(me.id(), Instant.now().minus(365, ChronoUnit.DAYS)))
            if (l.getDelta() > 0) byMonth.merge(MONTH.format(l.getCreatedAt().atZone(ZoneOffset.UTC)), l.getDelta(), (x, y) -> x.intValue() + y.intValue());
        return new EcoDashboardDto(a.getBalance(), a.getLifetimePoints(), lvl, EcoEngine.levelName(lvl), EcoEngine.levelProgress(a.getLifetimePoints()), EcoEngine.pointsForLevel(lvl + 1),
                rankOf(a), a.isShowOnLeaderboard(), round(all.carbon()), round(all.weight()), (long) all.count("PICKUP_COMPLETED"), new EquivalentsDto(eq.treeYears(), eq.carKm(), (long) eq.phoneCharges()),
                badgeDtos(me.id(), all), challengeDtos(me.id(), false), recent(me.id()), byMonth, METHOD_NOTE);
    }
    private int rankOf(EcoAccount a) { return (int) accounts.countByShowOnLeaderboardTrueAndLifetimePointsGreaterThan(a.getLifetimePoints()) + 1; }
    private List<BadgeDto> badgeDtos(Long uid, Stats s) {
        Map<String, Instant> mine = new HashMap<>(); userBadges.findByUserId(uid).forEach(b -> mine.put(b.getBadgeCode(), b.getAwardedAt()));
        return badges.findAllByOrderBySortOrderAsc().stream().map(b -> new BadgeDto(b.getCode(), b.getName(), b.getDescription(), b.getIcon(), mine.containsKey(b.getCode()), mine.get(b.getCode()),
                round(Math.min(metric(b.getCriteria(), s), b.getThreshold().doubleValue())), b.getThreshold().doubleValue())).toList();
    }
    private List<LedgerDto> recent(Long uid) { return ledger.findTop50ByUserIdOrderByIdDesc(uid).stream().map(l -> new LedgerDto(l.getId(), l.getDelta(), l.getActivity(), l.getNote(), l.getWeightKg().doubleValue(), l.getCarbonKg().doubleValue(), l.getCreatedAt())).toList(); }
    @Transactional(readOnly = true)
    public List<LedgerDto> history(AuthUser me) { return recent(me.id()); }
    @Transactional(readOnly = true)
    public List<BadgeDto> myBadges(AuthUser me) { return badgeDtos(me.id(), stats(me.id(), Instant.EPOCH, FAR_FUTURE)); }
    @Transactional(readOnly = true)
    public List<ChallengeDto> myChallenges(AuthUser me) { return challengeDtos(me.id(), false); }
    private List<ChallengeDto> challengeDtos(Long uid, boolean includeInactive) {
        Set<Long> done = new HashSet<>(); completions.findByUserId(uid).forEach(c -> done.add(c.getChallengeId()));
        List<Challenge> list = includeInactive ? challenges.findAll() : challenges.findByActiveTrueOrderByIdAsc();
        Instant now = Instant.now();
        return list.stream().filter(c -> includeInactive || c.getEndsAt() == null || c.getEndsAt().isAfter(now) || done.contains(c.getId())).map(c -> {
            Stats s = stats(uid, c.getStartsAt(), c.getEndsAt() == null ? FAR_FUTURE : c.getEndsAt());
            double prog = Math.min(metric(c.getMetric(), s), c.getTarget().doubleValue());
            return new ChallengeDto(c.getId(), c.getCode(), c.getTitle(), c.getDescription(), c.getMetric(), c.getTarget().doubleValue(), round(prog),
                    EcoEngine.challengeFraction(prog, c.getTarget().doubleValue()), c.getRewardPoints(), done.contains(c.getId()), c.getEndsAt(), c.isActive());
        }).toList();
    }

    // ------------------------------------------------------------------ leaderboard
    @Transactional
    public List<LeaderboardEntryDto> leaderboard(AuthUser me, int limit) {
        int n = Math.max(1, Math.min(100, limit));
        List<EcoAccount> top = accounts.findByShowOnLeaderboardTrueOrderByLifetimePointsDescUserIdAsc(PageRequest.of(0, n));
        List<Long> scores = top.stream().map(a -> (long) a.getLifetimePoints()).toList();
        List<EcoEngine.Ranked> ranks = EcoEngine.rank(scores);
        Map<Long, String> names = lookup.names(top.stream().map(EcoAccount::getUserId).toList());
        List<LeaderboardEntryDto> out = new ArrayList<>();
        for (EcoEngine.Ranked r : ranks) {
            EcoAccount a = top.get(r.index()); int lvl = EcoEngine.level(a.getLifetimePoints());
            out.add(new LeaderboardEntryDto(r.rank(), EcoEngine.publicName(names.get(a.getUserId())), a.getLifetimePoints(), lvl, EcoEngine.levelName(lvl), a.getUserId().equals(me.id())));
        }
        if (out.stream().noneMatch(LeaderboardEntryDto::me)) {
            EcoAccount mine = accountForUpdate(me.id());
            if (mine.isShowOnLeaderboard()) { int lvl = EcoEngine.level(mine.getLifetimePoints()); out.add(new LeaderboardEntryDto(rankOf(mine), "You", mine.getLifetimePoints(), lvl, EcoEngine.levelName(lvl), true)); }
        }
        return out;
    }
    @Transactional
    public void setLeaderboardVisible(AuthUser me, boolean visible) { EcoAccount a = accountForUpdate(me.id()); a.setShowOnLeaderboard(visible); accounts.save(a); }

    // ------------------------------------------------------------------ rewards
    @Transactional(readOnly = true)
    public List<RewardDto> rewards(AuthUser me, boolean all) {
        int bal = accounts.findById(me.id()).map(EcoAccount::getBalance).orElse(0);
        return (all ? rewards.findAll() : rewards.findByActiveTrueOrderByCostPointsAsc()).stream().sorted(Comparator.comparingInt(Reward::getCostPoints))
                .map(r -> new RewardDto(r.getId(), r.getCode(), r.getTitle(), r.getDescription(), r.getCostPoints(), r.getStock(), r.isActive(), r.isActive() && bal >= r.getCostPoints() && (r.getStock() == null || r.getStock() > 0))).toList();
    }
    @Transactional
    public RedemptionDto redeem(AuthUser me, Long rewardId) {
        EcoAccount a = accountForUpdate(me.id());                                   // lock order: account, then reward (always the same, no deadlock)
        Reward r = rewards.lockById(rewardId).orElseThrow(() -> ApiException.notFound("Reward not found"));
        if (!r.isActive()) throw ApiException.conflict("This reward is no longer available");
        if (r.getStock() != null && r.getStock() <= 0) throw ApiException.conflict("This reward is out of stock");
        if (a.getBalance() < r.getCostPoints()) throw ApiException.conflict("Not enough eco-points: you have " + a.getBalance() + " and this costs " + r.getCostPoints());
        a.setBalance(a.getBalance() - r.getCostPoints()); accounts.save(a);
        if (r.getStock() != null) r.setStock(r.getStock() - 1);
        RewardRedemption x = new RewardRedemption(); x.setUserId(me.id()); x.setRewardId(r.getId()); x.setPointsSpent(r.getCostPoints());
        StringBuilder v = new StringBuilder("ECO"); for (int i = 0; i < 8; i++) { if (i % 4 == 0) v.append('-'); v.append(ALPHA.charAt(rnd.nextInt(ALPHA.length()))); }
        x.setVoucherCode(v.toString()); redemptions.save(x);
        EcoLedger l = new EcoLedger(); l.setUserId(me.id()); l.setDelta(-r.getCostPoints()); l.setActivity("REDEMPTION"); l.setRefType("REDEMPTION"); l.setRefId(x.getId()); l.setNote("Redeemed: " + r.getTitle()); ledger.save(l);
        audit.log(me.id(), "REWARD_REDEEMED", "rewards", r.getId(), "cost=" + r.getCostPoints());
        notifications.notify(me.id(), "Reward redeemed", r.getTitle() + " - voucher " + x.getVoucherCode(), "ECO");
        return new RedemptionDto(x.getId(), r.getTitle(), x.getPointsSpent(), x.getVoucherCode(), x.getStatus(), x.getCreatedAt());
    }
    @Transactional(readOnly = true)
    public List<RedemptionDto> redemptions(AuthUser me) {
        Map<Long, String> titles = new HashMap<>(); rewards.findAll().forEach(r -> titles.put(r.getId(), r.getTitle()));
        return redemptions.findByUserIdOrderByIdDesc(me.id()).stream().map(x -> new RedemptionDto(x.getId(), titles.get(x.getRewardId()), x.getPointsSpent(), x.getVoucherCode(), x.getStatus(), x.getCreatedAt())).toList();
    }

    // ------------------------------------------------------------------ admin management
    @Transactional
    public RewardDto saveReward(Long adminId, Long id, RewardRequest r) {
        Reward x = id == null ? new Reward() : rewards.findById(id).orElseThrow(() -> ApiException.notFound("Reward not found"));
        if (id == null && rewards.findByCode(r.code()).isPresent()) throw ApiException.conflict("Reward code already exists");
        if (id == null) x.setCode(r.code().trim().toUpperCase(Locale.ROOT));
        x.setTitle(r.title()); x.setDescription(r.description()); x.setCostPoints(r.costPoints()); x.setStock(r.stock()); x.setActive(r.active()); rewards.save(x);
        audit.log(adminId, id == null ? "REWARD_CREATED" : "REWARD_UPDATED", "rewards", x.getId(), x.getCode());
        return new RewardDto(x.getId(), x.getCode(), x.getTitle(), x.getDescription(), x.getCostPoints(), x.getStock(), x.isActive(), true);
    }
    @Transactional
    public ChallengeDto saveChallenge(Long adminId, Long id, ChallengeRequest r) {
        if (!METRICS.contains(r.metric())) throw ApiException.badRequest("Metric must be one of " + new TreeSet<>(METRICS));
        Challenge c = id == null ? new Challenge() : challenges.findById(id).orElseThrow(() -> ApiException.notFound("Challenge not found"));
        if (id == null && challenges.findByCode(r.code()).isPresent()) throw ApiException.conflict("Challenge code already exists");
        if (id == null) c.setCode(r.code().trim().toUpperCase(Locale.ROOT));
        c.setTitle(r.title()); c.setDescription(r.description()); c.setMetric(r.metric()); c.setTarget(r.target()); c.setRewardPoints(r.rewardPoints()); c.setEndsAt(r.endsAt()); c.setActive(r.active());
        challenges.save(c);
        audit.log(adminId, id == null ? "CHALLENGE_CREATED" : "CHALLENGE_UPDATED", "challenges", c.getId(), c.getCode());
        return new ChallengeDto(c.getId(), c.getCode(), c.getTitle(), c.getDescription(), c.getMetric(), c.getTarget().doubleValue(), 0, 0, c.getRewardPoints(), false, c.getEndsAt(), c.isActive());
    }
    @Transactional(readOnly = true)
    public List<ChallengeDto> allChallenges(AuthUser me) { return challengeDtos(me.id(), true); }

    // ------------------------------------------------------------------ carbon estimate (reuses the environmental module)
    @Transactional(readOnly = true)
    public EstimateDto estimate(Long categoryId, BigDecimal weightKg, int quantity, Pathway pathway) {
        ProductCategory c = categories.findById(categoryId).orElseThrow(() -> ApiException.notFound("Category not found"));
        EnvironmentalFactor f = factors.findByCategoryId(categoryId).orElseThrow(() -> ApiException.conflict("No environmental factors configured for " + c.getName()));
        double unit = weightKg == null ? c.getDefaultWeightKg().doubleValue() : weightKg.doubleValue();
        var res = calc.calculate(unit, Math.max(1, quantity), pathway, new EnvironmentalCalculator.Factors(f.getCarbonReusePerKg().doubleValue(), f.getCarbonRepairPerKg().doubleValue(),
                f.getCarbonRefurbishPerKg().doubleValue(), f.getCarbonRecyclePerKg().doubleValue(), f.getMetalsRecoveryPct().doubleValue()), weightKg != null);
        var eq = EcoEngine.equivalents(res.carbonAvoidedKg());
        return new EstimateDto(res.weightKgTotal(), res.ewasteDivertedKg(), res.carbonAvoidedKg(), res.materialsRecoveredKg(), EcoEngine.pointsForWeight(ratePerKg(pathway), res.ewasteDivertedKg()),
                new EquivalentsDto(eq.treeYears(), eq.carKm(), (long) eq.phoneCharges()), res.methodology() + " " + METHOD_NOTE);
    }
    /** Estimated carbon avoided for a pickup (recycling pathway) used to pre-fill the pickup and compute certificate figures. */
    public double recycleCarbonKg(Long categoryId, double totalKg) {
        if (categoryId == null) return 0;
        return factors.findByCategoryId(categoryId).map(f -> Math.round(totalKg * f.getCarbonRecyclePerKg().doubleValue() * 1000.0) / 1000.0).orElse(0.0);
    }
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
