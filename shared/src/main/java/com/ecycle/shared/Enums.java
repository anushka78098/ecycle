package com.ecycle.shared;

/** All domain enumerations shared by the backend API and the JavaFX client. */
public final class Enums {
    private Enums() {}
    public enum Role { SELLER, BUYER, RESOLVER }
    public enum AccountStatus { ACTIVE, SUSPENDED }
    /** Ordered worst to best; ordinal() is used for "at least this condition" comparisons. */
    public enum ItemCondition { SCRAP, POOR, FAIR, GOOD, LIKE_NEW }
    public enum FunctionalStatus { NOT_WORKING, PARTIALLY_WORKING, FULLY_WORKING }
    public enum Pathway { REUSE, REPAIR, REFURBISH, RECYCLE, DISPOSE }
    public enum ListingStatus { PENDING_APPROVAL, APPROVED, REJECTED, RESERVED, SOLD, WITHDRAWN }
    public enum OfferStatus { PENDING, ACCEPTED, REJECTED, WITHDRAWN, SUPERSEDED }
    public enum OrderStatus { PENDING_PAYMENT, PAID, SHIPPED, DELIVERED, DISPUTED, CANCELLED, REFUNDED }
    public enum PaymentStatus { CREATED, VERIFIED, FAILED, PARTIALLY_REFUNDED, REFUNDED }
    public enum RefundStatus { REQUESTED, PROCESSED, FAILED }
    public enum ShipmentStatus { PENDING, SHIPPED, IN_TRANSIT, DELIVERED }
    public enum LifecycleStage { LISTED, APPROVED, PURCHASED, DELIVERED, DISMANTLED, COMPONENT_RECOVERED,
        REUSED, REPAIRED, REFURBISHED, RECYCLED, DISPOSED, HAZARD_HOLD }
    public enum DisputeStatus { OPEN, ASSIGNED, AWAITING_EVIDENCE, UNDER_REVIEW, RESOLVED, REJECTED }
    public enum DisputePriority { LOW, MEDIUM, HIGH, CRITICAL }
    public enum DisputeCategory { PRODUCT_NOT_AS_DESCRIBED, DAMAGED_PRODUCT, WRONG_PRODUCT_RECEIVED, PAYMENT_ISSUE,
        DELIVERY_ISSUE, SELLER_NON_COOPERATION, BUYER_NON_COOPERATION }
    public enum ResolutionType { FULL_REFUND, PARTIAL_REFUND, REPLACEMENT, RETURN_AND_REFUND,
        RESOLVED_BUYER_FAVOR, RESOLVED_SELLER_FAVOR, MUTUAL_SETTLEMENT }
    public enum EvidenceCategory { PHOTO, DOCUMENT, PAYMENT_PROOF, DELIVERY_PROOF, COMMUNICATION, OTHER }
    public enum MessageVisibility { PARTIES, INTERNAL, EVIDENCE_REQUEST }
    public enum HazardLevel { LOW, MODERATE, HIGH, CRITICAL }
    public enum AuctionStatus { OPEN, CLOSED_PENDING_SELLER, CLOSED_NO_BIDS, AWARDED, CANCELLED }
    public enum BuyerType { INDIVIDUAL, REFURBISHER, REPAIR_SHOP, RECYCLER }
    public enum VerificationStatus { PENDING, VERIFIED, REJECTED }
    public enum SanitizationStatus { SUBMITTED, VERIFIED, REJECTED }
    // ---- pickups, certificates, custody, gamification
    public enum PickupStatus { REQUESTED, ACCEPTED, EN_ROUTE, PICKED_UP, AT_FACILITY, COMPLETED, REJECTED, CANCELLED }
    public enum CertificateType { DATA_SANITIZATION, DATA_DESTRUCTION, RECYCLING }
    public enum RewardStatus { ISSUED, FULFILLED, CANCELLED }
    public enum PendingActionType { BOOK_PICKUP, CANCEL_PICKUP, REDEEM_REWARD }
}
