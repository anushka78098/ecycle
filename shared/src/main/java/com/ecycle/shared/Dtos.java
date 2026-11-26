package com.ecycle.shared;

import com.ecycle.shared.Enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** API contract shared by the Spring Boot backend and the JavaFX client. */
public final class Dtos {
    private Dtos() {}
    // ---- common
    public record ApiError(int status, String error, String message, Instant timestamp, Map<String, String> fieldErrors) {}
    public record Msg(String message) {}
    public record RejectRequest(@NotBlank @Size(max = 300) String reason) {}
    // ---- auth & profile
    public record RegisterRequest(@NotBlank @Email @Size(max = 160) String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 120) String fullName, @Size(max = 20) String phone,
                                  @NotNull Role role, @Size(max = 150) String businessName, @Size(max = 80) String city,
                                  @Size(max = 150) String organization, BuyerType buyerType) {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record AuthResponse(String token, long expiresInSeconds, Long userId, String fullName, String email, Set<Role> roles) {}
    /** Google sign-in: the OpenID Connect ID token; role is only needed the first time (SELLER or BUYER). */
    public record GoogleLoginRequest(@NotBlank String idToken, Role role) {}
    public record ForgotRequest(@NotBlank @Email String email) {}
    public record ResetRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record ProfileDto(Long userId, String email, String fullName, String phone, Set<Role> roles, AccountStatus status,
                             String city, String organization, VerificationStatus verificationStatus, BuyerType buyerType,
                             int recoveryCapability, int environmentalRating, double trustScore) {}
    public record ProfileUpdate(@NotBlank @Size(max = 120) String fullName, @Size(max = 20) String phone,
                                @Size(max = 80) String city, @Size(max = 150) String organization) {}
    // ---- catalog
    public record CategoryDto(Long id, String name, String description, int repairabilityBase, int componentRecoveryBase,
                              int lifeMonths, BigDecimal defaultWeightKg, boolean dismantlable, boolean active) {}
    public record CategoryRequest(@NotBlank @Size(max = 80) String name, @Size(max = 300) String description,
                                  @Min(0) @Max(100) int repairabilityBase, @Min(0) @Max(100) int componentRecoveryBase,
                                  @Min(1) int lifeMonths, @NotNull @DecimalMin("0.01") BigDecimal defaultWeightKg,
                                  boolean dismantlable, boolean active) {}
    // ---- listings
    public record ListingRequest(@NotBlank @Size(max = 150) String name, @Size(max = 80) String brand, @Size(max = 80) String model,
                                 @NotNull Long categoryId, @Min(0) int ageMonths, @NotNull ItemCondition physicalCondition,
                                 @NotNull FunctionalStatus functionalStatus, @Min(1) int quantity,
                                 @NotNull @DecimalMin("0.00") BigDecimal expectedPrice, @Size(max = 4000) String description,
                                 @NotBlank @Size(max = 80) String pickupCity, @Size(max = 250) String pickupAddress,
                                 @NotNull @DecimalMin("0.01") BigDecimal weightKg, Pathway pathway,
                                 Map<String, Boolean> hazardAnswers, boolean auctionEnabled, Integer auctionHours,
                                 BigDecimal auctionMinPrice) {}
    public record ScoreDto(double overall, double functionality, double condition, double repairability, double reusability,
                           double componentRecovery, double remainingLife, Pathway recommendedPathway, String explanation) {}
    public record ListingDto(Long id, Long sellerId, String sellerName, boolean sellerVerified, Long categoryId, String categoryName,
                             String name, String brand, String model, int ageMonths, ItemCondition physicalCondition,
                             FunctionalStatus functionalStatus, int quantity, BigDecimal expectedPrice, String description,
                             String pickupCity, String pickupAddress, BigDecimal weightKg, Pathway pathway, ListingStatus status,
                             String rejectionReason, ScoreDto score, HazardLevel hazardLevel, boolean shippingBlocked,
                             String specialistRequired, boolean requiresSanitization, boolean sanitizationVerified,
                             boolean auctionEnabled, Long auctionId, Instant auctionEndsAt, List<Long> imageIds,
                             String passportCode, Instant createdAt) {}
    // ---- offers / orders / payments
    public record OfferRequest(@NotNull Long listingId, @NotNull @DecimalMin("0.01") BigDecimal amount, @Size(max = 500) String message) {}
    public record OfferDto(Long id, Long listingId, String listingName, Long buyerId, String buyerName, BigDecimal amount,
                           String message, OfferStatus status, Instant createdAt, BigDecimal askingPrice) {}
    public record OrderRequest(@NotNull Long listingId, @Size(max = 300) String shippingAddress) {}
    public record AddressRequest(@NotBlank @Size(max = 300) String shippingAddress) {}
    public record CancelRequest(@Size(max = 300) String reason) {}
    public record ShipmentRequest(@NotBlank @Size(max = 80) String carrier, @Size(max = 80) String trackingNo, @NotNull ShipmentStatus status) {}
    public record OrderDto(Long id, Long listingId, String listingName, Long buyerId, String buyerName, Long sellerId, String sellerName,
                           BigDecimal amount, OrderStatus status, String shippingAddress, Long paymentId, PaymentStatus paymentStatus,
                           String receiptNo, String carrier, String trackingNo, ShipmentStatus shipmentStatus,
                           boolean specialistHandling, boolean shippingBlocked, Instant createdAt) {}
    public record PaymentInitDto(Long paymentId, String provider, String providerOrderId, BigDecimal amount, String currency, String keyId, boolean sandbox) {}
    public record PaymentVerifyRequest(@NotBlank String providerPaymentId, @NotBlank String signature) {}
    public record ReceiptDto(String receiptNo, Long orderId, String listingName, String buyerName, String sellerName, BigDecimal amount,
                             String provider, String providerPaymentId, Instant verifiedAt) {}
    public record ReviewRequest(@NotNull Long orderId, @Min(1) @Max(5) int rating, @Size(max = 1000) String comment) {}
    public record ReviewDto(Long id, Long orderId, String reviewerName, String revieweeName, int rating, String comment, Instant createdAt) {}
    // ---- disputes
    public record DisputeRequest(@NotNull Long orderId, @NotNull DisputeCategory category, @NotBlank @Size(max = 4000) String description) {}
    public record DisputeDto(Long id, Long orderId, String listingName, Long raisedBy, String raisedByName, Long againstUser, String againstName,
                             Long resolverId, String resolverName, DisputeCategory category, DisputeStatus status, DisputePriority priority,
                             String description, BigDecimal orderAmount, Instant createdAt, Instant resolvedAt) {}
    public record EvidenceDto(Long id, Long submittedBy, String submitterName, String submitterRole, EvidenceCategory category, String description,
                              String originalName, String contentType, String sha256, Instant createdAt) {}
    public record MessageDto(Long id, Long senderId, String senderName, MessageVisibility visibility, String body, Instant createdAt) {}
    public record MessageRequest(MessageVisibility visibility, @NotBlank @Size(max = 4000) String body) {}
    public record PriorityRequest(@NotNull DisputePriority priority) {}
    public record AssignRequest(Long resolverId) {}
    public record ResolutionRequest(@NotNull ResolutionType type, BigDecimal refundAmount, @NotBlank @Size(max = 4000) String decisionText) {}
    public record ResolutionDto(ResolutionType type, BigDecimal refundAmount, String decisionText, String resolverName, Instant createdAt) {}
    public record PartyDto(Long id, String displayName, String city, String contact) {}
    public record OrderSummary(Long orderId, String listingName, BigDecimal amount, OrderStatus orderStatus, PaymentStatus paymentStatus, ShipmentStatus shipmentStatus) {}
    public record DisputeDetailDto(DisputeDto dispute, List<EvidenceDto> evidence, List<MessageDto> messages, ResolutionDto resolution,
                                   PartyDto buyer, PartyDto seller, OrderSummary order, List<String> assignmentHistory) {}
    // ---- notifications, dashboards, impact
    public record NotificationDto(String id, String title, String body, String type, boolean read, Instant createdAt) {}
    public record DashboardDto(String role, Map<String, Object> stats, Map<String, Map<String, Number>> charts, List<Map<String, Object>> recent) {}
    public record ImpactDto(double ewasteDivertedKg, double carbonAvoidedKg, double materialsRecoveredKg, long reused, long repaired,
                            long refurbished, long recycled, long disposed, long estimatedRecords, long measuredRecords, String note) {}
    public record LifecycleDto(LifecycleStage stage, Pathway pathway, String actorLabel, String note, Instant createdAt) {}
    // ---- passport & genealogy
    public record PassportEventDto(String type, String actorLabel, String detail, BigDecimal valueAmount, Instant createdAt) {}
    public record PassportDto(String code, Long listingId, String name, String componentType, LifecycleStage stage, String ownerLabel,
                              String parentCode, String qrPayload, Instant createdAt, List<PassportDto> children, List<PassportEventDto> events) {}
    public record ComponentSpec(@NotBlank @Size(max = 60) String componentType, @NotBlank @Size(max = 150) String name) {}
    public record DismantleRequest(@NotEmpty List<@Valid ComponentSpec> components) {}
    public record ComponentActionRequest(@NotBlank String action, Long toUserId, @DecimalMin("0.00") BigDecimal amount, @Size(max = 400) String note) {}
    public record OutcomeRequest(@NotNull Pathway pathway, @Size(max = 400) String note, @DecimalMin("0.01") BigDecimal measuredWeightKg) {}
    // ---- auctions
    public record BidRequest(@NotNull @DecimalMin("0.01") BigDecimal price, @NotNull Pathway pathway, @Size(max = 500) String recoveryPlan) {}
    public record BidDto(Long id, Long bidderId, String bidderLabel, BigDecimal price, Pathway pathway, int recoveryCapability,
                         int environmentalPerformance, String recoveryPlan, double circularBidScore, Instant createdAt) {}
    public record AuctionDto(Long id, Long listingId, String listingName, Instant endsAt, AuctionStatus status, BigDecimal minPrice,
                             long bidCount, long secondsRemaining, BigDecimal referencePrice, BidDto highestPriceBid, BidDto highestScoreBid,
                             List<BidDto> bids, Long winningBidId) {}
    // ---- hazard & sanitization
    public record HazardAssessRequest(@NotNull Map<String, Boolean> answers) {}
    public record HazardDto(Long id, Long listingId, HazardLevel level, int totalPoints, boolean blocked, boolean requiresSanitization,
                            String specialist, List<String> findings, List<String> handling, String inspectorLabel, Instant createdAt) {}
    public record HazardRuleDto(Long id, String code, String description, int points, boolean blocksShipping, boolean requiresSanitization,
                                String handling, String specialistType, boolean enabled) {}
    public record HazardRuleRequest(@NotBlank @Size(max = 60) String code, @NotBlank @Size(max = 300) String description, @Min(0) @Max(100) int points,
                                    boolean blocksShipping, boolean requiresSanitization, @NotBlank @Size(max = 500) String handling,
                                    @Size(max = 100) String specialistType, boolean enabled) {}
    public record SanitizationRequest(@NotBlank @Size(max = 80) String method, @Size(max = 120) String toolName, @NotBlank @Size(max = 300) String evidenceRef) {}
    public record SanitizationDto(Long id, Long listingId, String listingName, String method, String toolName, String evidenceRef,
                                  SanitizationStatus status, String submittedByName, String verifiedByName, Instant verifiedAt,
                                  String certificateNo, String note, Instant createdAt) {}
    public record VerifyRequest(boolean approve, @Size(max = 300) String note) {}
    // ---- requirements & recommendations
    public record RequirementRequest(Long categoryId, @DecimalMin("0.00") BigDecimal maxBudget, @Size(max = 80) String city, ItemCondition minCondition,
                                     @Size(max = 300) String intendedUse, @Size(max = 300) String requiredComponents, Pathway preferredPathway, boolean active) {}
    public record RequirementDto(Long id, Long categoryId, String categoryName, BigDecimal maxBudget, String city, ItemCondition minCondition,
                                 String intendedUse, String requiredComponents, Pathway preferredPathway, boolean active) {}
    public record RecommendationDto(ListingDto listing, double matchScore, List<String> reasons, String basedOn) {}
    // ---- admin
    public record AdminUserDto(Long id, String email, String fullName, String phone, Set<Role> roles, AccountStatus status,
                               VerificationStatus verificationStatus, BuyerType buyerType, boolean buyerVerified, Instant createdAt) {}
    public record StaffRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8, max = 72) String password,
                               @NotBlank @Size(max = 120) String fullName, @NotNull Role role) {}
    public record VerifyBuyerRequest(boolean approve, @Min(0) @Max(100) int recoveryCapability, @Min(0) @Max(100) int environmentalRating) {}
    public record EnvFactorDto(Long id, Long categoryId, String categoryName, double carbonReusePerKg, double carbonRepairPerKg,
                               double carbonRefurbishPerKg, double carbonRecyclePerKg, double metalsRecoveryPct, String sourceNote, Instant updatedAt) {}
    public record EnvFactorRequest(@NotNull Long categoryId, @DecimalMin("0") double carbonReusePerKg, @DecimalMin("0") double carbonRepairPerKg,
                                   @DecimalMin("0") double carbonRefurbishPerKg, @DecimalMin("0") double carbonRecyclePerKg,
                                   @DecimalMin("0") @DecimalMax("100") double metalsRecoveryPct, @Size(max = 400) String sourceNote) {}
    public record ConfigEntryDto(String key, double value, String description) {}
    public record ConfigUpdate(double value) {}
    public record AuditLogDto(Long id, Long actorId, String actorName, String action, String entityType, Long entityId, String detail, Instant createdAt) {}
    // ---- chat
    public record ChatRequest(@NotNull Long listingId, @NotNull Long recipientId, @NotBlank @Size(max = 1000) String body) {}
    public record ChatDto(String id, Long listingId, String listingName, Long senderId, String senderName, Long recipientId, String recipientName, String body, Instant createdAt) {}

    // ---- recycler map & pickups
    public record RecyclerLocationRequest(@NotBlank @Size(max = 150) String facilityName, @NotBlank @Size(max = 250) String address, @NotBlank @Size(max = 80) String city,
                                          @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude, @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
                                          @Size(max = 30) String phone, @Size(max = 150) String openingHours, @Size(max = 300) String acceptedCategories,
                                          @Size(max = 200) String certifications, @Min(1) @Max(500) int serviceRadiusKm, boolean active) {}
    public record RecyclerLocationDto(Long id, Long recyclerId, String recyclerName, String organization, String facilityName, String address, String city,
                                      double latitude, double longitude, String phone, String openingHours, String acceptedCategories, String certifications,
                                      int serviceRadiusKm, boolean active, Double distanceKm, int recoveryCapability, int environmentalRating) {}
    public record PickupRequest(@NotNull Long locationId, Long listingId, Long categoryId, @NotBlank @Size(max = 500) String itemsDescription,
                                @Min(1) @Max(1000) int quantity, @NotNull @DecimalMin("0.01") @DecimalMax("5000") BigDecimal estWeightKg,
                                boolean dataBearing, boolean hazardous, @Size(max = 300) String pickupAddress, Double pickupLat, Double pickupLng,
                                @Size(max = 30) String contactPhone, @NotNull Instant preferredAt) {}
    public record PickupStatusRequest(@NotNull PickupStatus status, Instant scheduledAt, @Size(max = 300) String note, @Size(max = 200) String scanPayload, Double lat, Double lng) {}
    public record PickupPingRequest(@NotNull Double lat, @NotNull Double lng) {}
    public record PickupVerifyRequest(@NotNull @DecimalMin("0.01") BigDecimal actualWeightKg, @NotBlank @Size(max = 80) String treatment, @Size(max = 500) String note,
                                      @Size(max = 200) String scanPayload, @Size(max = 80) String dataDestructionMethod, @Size(max = 80) String deviceSerial,
                                      List<String> checklistItems) {}
    public record PickupEventDto(PickupStatus status, String actorLabel, String note, Double lat, Double lng, Instant createdAt) {}
    public record PickupDto(Long id, String trackingCode, Long requesterId, String requesterName, Long recyclerId, String recyclerName, Long locationId, String facilityName,
                            Double facilityLat, Double facilityLng, Long listingId, String passportCode, String categoryName, String itemsDescription, int quantity,
                            BigDecimal estWeightKg, BigDecimal actualWeightKg, boolean dataBearing, boolean hazardous, String pickupAddress, Double pickupLat, Double pickupLng,
                            String contactPhone, Instant preferredAt, Instant scheduledAt, PickupStatus status, String statusNote, Double lastLat, Double lastLng,
                            Instant lastPingAt, BigDecimal estCarbonKg, String treatment, List<String> nextActions, List<PickupEventDto> events,
                            List<String> certificateNos, Instant createdAt) {}
    /** Real-time message pushed over STOMP to /user/queue/events. type: PICKUP | NOTIFICATION | CHAT. */
    public record LiveEvent(String type, Object payload, Instant at) {}
    // ---- certificates
    public record CertificateDto(Long id, String certificateNo, CertificateType type, String verificationCode, Long listingId, Long pickupId, String passportCode,
                                 String holderName, String issuedByName, Map<String, String> details, String contentHash, Integer custodySeq, String custodyHeadHash,
                                 String verifyUrl, Instant issuedAt) {}
    public record CertificateVerificationDto(boolean valid, String message, String certificateNo, CertificateType type, Instant issuedAt, String issuer,
                                             Map<String, String> publicDetails, boolean custodyAnchorIntact) {}
    // ---- data-wipe checklists
    public record ChecklistItemDto(String code, String text, boolean mandatory) {}
    public record ChecklistTemplateDto(Long listingId, String categoryName, List<ChecklistItemDto> items, List<String> methods) {}
    public record ChecklistSubmitRequest(@NotBlank @Size(max = 80) String method, @Size(max = 80) String deviceSerial, @Size(max = 120) String toolName,
                                         @NotBlank @Size(max = 300) String evidenceRef, @NotNull List<String> completedItems) {}
    public record ChecklistDto(Long id, Long listingId, Long recordId, Long pickupId, String completedByName, String completedRole, String method, String deviceSerial,
                               List<String> completedItems, boolean allMandatoryDone, Instant createdAt) {}
    // ---- chain of custody
    public record CustodyEntryDto(int seq, String eventType, String actorLabel, String actorRole, String custodianLabel, String location, String detail,
                                  Instant occurredAt, String prevHash, String hash) {}
    public record CustodyChainDto(String assetCode, String assetName, String qrPayload, String currentCustodianLabel, boolean valid, String verificationMessage,
                                  Integer brokenAtSeq, int length, String headHash, List<CustodyEntryDto> entries) {}
    public record CustodyEventRequest(@NotBlank @Size(max = 40) String eventType, Long toUserId, @Size(max = 200) String location, @Size(max = 300) String detail) {}
    public record CustodyScanRequest(@NotBlank @Size(max = 200) String payload, @Size(max = 200) String location) {}
    // ---- eco-points & gamification
    public record EquivalentsDto(double treeYears, double carKm, long phoneCharges) {}
    public record BadgeDto(String code, String name, String description, String icon, boolean earned, Instant awardedAt, double progress, double threshold) {}
    public record ChallengeDto(Long id, String code, String title, String description, String metric, double target, double progress, double fraction, int rewardPoints,
                               boolean completed, Instant endsAt, boolean active) {}
    public record ChallengeRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 100) String title, @NotBlank @Size(max = 300) String description,
                                   @NotBlank String metric, @NotNull @DecimalMin("0.01") BigDecimal target, @Min(0) @Max(100000) int rewardPoints, Instant endsAt, boolean active) {}
    public record LedgerDto(Long id, int delta, String activity, String note, double weightKg, double carbonKg, Instant createdAt) {}
    public record LeaderboardEntryDto(int rank, String name, int points, int level, String levelName, boolean me) {}
    public record RewardDto(Long id, String code, String title, String description, int costPoints, Integer stock, boolean active, boolean affordable) {}
    public record RewardRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 100) String title, @NotBlank @Size(max = 300) String description,
                                @Min(1) int costPoints, Integer stock, boolean active) {}
    public record RedemptionDto(Long id, String rewardTitle, int pointsSpent, String voucherCode, RewardStatus status, Instant createdAt) {}
    public record EstimateDto(double weightKg, double ewasteDivertedKg, double carbonAvoidedKg, double materialsRecoveredKg, int estimatedPoints, EquivalentsDto equivalents, String methodology) {}
    public record EcoDashboardDto(int balance, int lifetimePoints, int level, String levelName, double levelProgress, long nextLevelAt, int rank, boolean showOnLeaderboard,
                                  double carbonAvoidedKg, double ewasteDivertedKg, long pickupsCompleted, EquivalentsDto equivalents, List<BadgeDto> badges,
                                  List<ChallengeDto> challenges, List<LedgerDto> recentActivity, Map<String, Number> pointsByMonth, String note) {}
    // ---- AI eco-assistant
    public record AssistantRequest(String conversationId, @NotBlank @Size(max = 2000) String message) {}
    public record PendingActionDto(String id, PendingActionType type, String summary, Instant expiresAt, String status) {}
    public record AssistantReplyDto(String conversationId, String reply, String mode, List<String> toolsUsed, List<PendingActionDto> pendingActions) {}
    public record AssistantMessageDto(String id, String role, String content, String mode, List<String> toolsUsed, Instant createdAt) {}
    public record ConversationDto(String id, String title, Instant updatedAt) {}
    public record ActionResultDto(boolean success, String message, String status) {}
}
