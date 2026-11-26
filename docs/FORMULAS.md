# Scoring formulas and assumptions

Everything below is implemented in plain Java under `backend/.../engine` with no framework dependency, so each engine can be replaced by an ML model behind the same input/output record. Weights and thresholds live in the `score_config` table and are editable by admins (every change is audited).

## 1. Circular Value Score (0-100) - `CircularValueEngine`

Inputs: functional status, physical condition, age in months, and three category baselines (repairability, component recovery, expected life in months) plus the hazard level.

| Factor | Formula |
|---|---|
| functionality | FULLY_WORKING 100, PARTIALLY_WORKING 55, NOT_WORKING 15 |
| condition | LIKE_NEW 100, GOOD 80, FAIR 55, POOR 30, SCRAP 10 |
| condMul | 0.5 + 0.5 x condition/100 |
| remaining life | max(0, 1 - age/lifeMonths) x 100 x condMul |
| repairability | categoryRepairability x condMul |
| reusability | (0.6 x functionality + 0.4 x condition) x (0.5 + 0.5 x remainingLife/100) |
| component recovery | categoryComponentRecovery x (0.6 + 0.4 x condition/100) |

`overall = sum(w_i x factor_i) / sum(w_i)` with default weights functionality 0.25, condition 0.20, repairability 0.15, reusability 0.15, component 0.10, life 0.15.

Recommended pathway (first rule that matches; thresholds configurable):
1. hazard level HIGH or CRITICAL -> RECYCLE if component recovery >= 25, else DISPOSE (safety override)
2. overall >= 75 and functionality >= 85 -> REUSE
3. overall >= 60, condition >= 50, repairability >= 50 -> REFURBISH
4. overall >= 45 and repairability >= 55 -> REPAIR
5. component recovery >= 25 -> RECYCLE
6. otherwise DISPOSE

The explanation string names the strongest and weakest factors and the rule that fired.
**Assumption:** inputs are the seller's declared condition; the score is a rule-based estimate, not a physical test.

## 2. Circular Bid Score (0-100) - `BidScoreEngine`

`score = (wP x price + wV x verification + wR x recovery + wE x environment) / sum(w)`; defaults 0.40 / 0.20 / 0.20 / 0.20.
- price = min(100, 100 x bidPrice / referencePrice), referencePrice = max(listing expected price, highest bid), so scores are recomputed as the market moves.
- verification = 100 for admin-verified buyers (only verified buyers can bid, so this is always 100 today; kept configurable).
- recovery and environment come from the buyer's **admin-verified profile**, not from the bid, so they cannot be inflated per bid.
The UI shows the *highest financial offer* and the *highest Circular Bid Score* separately; they can be different bids by design.

## 3. Hazard risk - `HazardEngine`

Each enabled rule in `hazard_rules` has points. Triggered points are summed: < 15 LOW, < 35 MODERATE, < 60 HIGH, otherwise CRITICAL. Any rule with `blocks_shipping` forces at least HIGH and blocks normal shipping (only `SPECIALIST_PICKUP` to a verified RECYCLER buyer). A rule with `requires_sanitization` (default: storage device present) gates resale until an admin verifies erase evidence and a certificate `DSC-yyyymmdd-nnnnnn` is issued.
**Assumption:** a questionnaire answered by the seller. It is not computer vision and not a hardware diagnostic, and the UI says so.
**Sanitization:** the platform cannot read a device. The certificate is an administrator's attestation after reviewing the seller's tool report reference, and cannot be issued without it.

## 4. Environmental impact - `EnvironmentalCalculator`

`total_kg = unit_weight x quantity`; `ewaste_diverted = total_kg` (0 for DISPOSE);
`carbon_avoided = ewaste_diverted x factor[pathway]` (kg CO2e per kg, per category, admin-editable);
`materials_recovered = ewaste_diverted x metals_recovery_pct / 100` for RECYCLE only.
Each record stores its inputs JSON and methodology text. Records are **estimates** unless a verified recycler/admin supplied a measured weight (then `measured = true`; carbon and materials remain factor-based). A product's weight is counted once even if later outcomes are recorded.
**Assumption:** the seeded factors are planning-grade placeholders, labelled as such in `environmental_factors.source_note`; replace with audited values before external reporting.

## 5. Smart matching - `MatchingEngine`

Points (max 100): category 30 (hard filter when specified), budget 25 (within budget 25, up to 20% over 10), location 15, condition 10 (one grade below 4), intended-use keywords 10, required-component keywords 5, pathway 5. Unspecified preferences earn partial neutral credit. Results below 35 are dropped; ties break by Circular Value Score. Every awarded point is returned as a human-readable reason.
