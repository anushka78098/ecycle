package com.ecycle.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.engine.HazardEngine;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/** Feature A (passport/genealogy), Feature C (hazard + sanitization), environmental impact, listing moderation and recommendations. */
@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness = Strictness.LENIENT)
class CircularFeaturesTest {
    static final AuthUser ADMIN = new AuthUser(50L, "a", Set.of(Role.RESOLVER)), SELLER = new AuthUser(1L, "s", Set.of(Role.SELLER)), BUYER = new AuthUser(2L, "b", Set.of(Role.BUYER)), OTHER = new AuthUser(3L, "o", Set.of(Role.BUYER));
    @Mock ListingRepository listings; @Mock Lookup lookup; @Mock AuditService audit; @Mock NotificationService notifications; @Mock LifecycleService lifecycle;
    Listing listing;

    @BeforeEach void setUp() {
        listing = new Listing(); listing.setId(10L); listing.setSellerId(1L); listing.setName("ThinkPad"); listing.setCategoryId(2L); listing.setStatus(ListingStatus.PENDING_APPROVAL); listing.setPathway(Pathway.REFURBISH); listing.setWeightKg(new BigDecimal("2")); listing.setQuantity(2);
        when(listings.findById(10L)).thenReturn(Optional.of(listing));
        when(lifecycle.recordOutcome(any(), any(), any(), any(), any())).thenReturn(LifecycleStage.REUSED);
    }

    // ================= Feature C: sanitization certificates =================
    @Mock HazardRuleRepository rules; @Mock HazardInspectionRepository inspections; @Mock SanitizationRecordRepository sanitizations; @Mock PurchaseOrderRepository orders; @Mock ScoringService scoring; @Mock PassportService passportSvc;
    @Spy ObjectMapper json = new ObjectMapper();
    @InjectMocks HazardService hazard;
    private SanitizationRecord submitted() { SanitizationRecord s = new SanitizationRecord(); s.setId(5L); s.setListingId(10L); s.setSubmittedBy(1L); s.setStatus(SanitizationStatus.SUBMITTED); s.setMethod("NIST 800-88"); when(sanitizations.findById(5L)).thenReturn(Optional.of(s)); return s; }

    @Test void certificateIsIssuedOnlyAfterAdminVerification() {
        listing.setRequiresSanitization(true); SanitizationRecord s = submitted(); assertNull(s.getCertificateNo());
        SanitizationDto d = hazard.verify(50L, 5L, new VerifyRequest(true, "log checked"));
        assertEquals(SanitizationStatus.VERIFIED, d.status()); assertTrue(d.certificateNo().startsWith("DSC-")); assertTrue(listing.isSanitizationVerified());
        verify(passportSvc).event(eq(10L), eq("SANITIZED"), eq(50L), contains("DSC-"));
    }
    @Test void rejectedEvidenceIssuesNoCertificate() {
        listing.setRequiresSanitization(true); SanitizationRecord s = submitted(); hazard.verify(50L, 5L, new VerifyRequest(false, "log missing"));
        assertEquals(SanitizationStatus.REJECTED, s.getStatus()); assertNull(s.getCertificateNo()); assertFalse(listing.isSanitizationVerified());
    }
    @Test void submitterCannotVerifyOwnEvidenceAndReviewedRecordsAreFinal() {
        SanitizationRecord s = submitted(); assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> hazard.verify(1L, 5L, new VerifyRequest(true, null))).status());
        s.setStatus(SanitizationStatus.VERIFIED); assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> hazard.verify(50L, 5L, new VerifyRequest(true, null))).status());
    }
    @Test void onlySellerCanSubmitAndOnlyWhenRequired() {
        SanitizationRequest r = new SanitizationRequest("Purge", "tool", "report-1");
        listing.setRequiresSanitization(false); assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> hazard.submit(SELLER, 10L, r)).status());
        listing.setRequiresSanitization(true); assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> hazard.submit(BUYER, 10L, r)).status());
        assertDoesNotThrow(() -> hazard.submit(SELLER, 10L, r)); verify(sanitizations).save(any(SanitizationRecord.class));
    }
    @Test void certificateEndpointFailsWhenNothingWasVerified() {
        SanitizationRecord s = new SanitizationRecord(); s.setStatus(SanitizationStatus.SUBMITTED); s.setListingId(10L); when(sanitizations.findByListingIdOrderByIdDesc(10L)).thenReturn(List.of(s));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> hazard.certificate(SELLER, 10L)).status());
    }
    @Test void hazardRulesComeFromTheDatabaseAndUnknownQuestionsAreRejected() {
        HazardRule r = new HazardRule(); r.setCode("BATTERY_SWELLING"); r.setDescription("swollen"); r.setPoints(40); r.setBlocksShipping(true); r.setHandling("isolate"); r.setSpecialistType("Battery recycler"); r.setEnabled(true);
        when(rules.findByEnabledTrue()).thenReturn(List.of(r));
        HazardEngine.Result res = hazard.evaluate(Map.of("BATTERY_SWELLING", true)); assertTrue(res.blocked()); assertEquals(HazardLevel.HIGH, res.level());
        assertThrows(ApiException.class, () -> hazard.evaluate(Map.of("NOT_A_RULE", true)));
        hazard.apply(listing, res); assertTrue(listing.isShippingBlocked()); assertEquals("Battery recycler", listing.getSpecialistRequired()); assertEquals(HazardLevel.HIGH, listing.getHazardLevel());
    }
    @Test void adminCanReconfigureARuleAndItIsAudited() {
        HazardRule r = new HazardRule(); r.setId(1L); r.setCode("OVERHEATING"); r.setPoints(25); when(rules.findById(1L)).thenReturn(Optional.of(r));
        HazardRuleDto d = hazard.saveRule(50L, 1L, new HazardRuleRequest("IGNORED", "desc", 45, true, false, "handle", "Tech", true));
        assertEquals(45, d.points()); assertEquals("OVERHEATING", d.code()); assertTrue(d.blocksShipping()); verify(audit).log(eq(50L), eq("HAZARD_RULE_UPDATED"), eq("hazard_rules"), eq(1L), anyString());
    }
    @Test void duplicateRuleCodeIsRejected() { when(rules.findByCode("DUP")).thenReturn(Optional.of(new HazardRule())); assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> hazard.saveRule(50L, null, new HazardRuleRequest("DUP", "d", 1, false, false, "h", null, true))).status()); }

    // ================= Feature A: passport & genealogy =================
    @Mock PassportRepository passports; @Mock PassportEventRepository events; @Mock ProductCategoryRepository categories; @Mock UserRepository users; @Mock BuyerProfileRepository buyers;
    private PassportService passportService() { return new PassportService(passports, events, listings, categories, users, buyers, lifecycle, lookup, audit, notifications); }
    private Passport root(LifecycleStage stage, Long owner) { Passport p = new Passport(); p.setId(1L); p.setPassportCode("ECP-AAAAAAAAAA"); p.setListingId(10L); p.setName("ThinkPad"); p.setStage(stage); p.setCurrentOwnerId(owner); when(passports.findByPassportCode("ECP-AAAAAAAAAA")).thenReturn(Optional.of(p)); return p; }
    private void dismantlable(boolean v) { ProductCategory c = new ProductCategory(); c.setDismantlable(v); when(categories.findById(2L)).thenReturn(Optional.of(c)); }
    private static DismantleRequest parts() { return new DismantleRequest(List.of(new ComponentSpec("RAM", "8GB DDR4"), new ComponentSpec("SSD", "256GB"), new ComponentSpec("Battery", "Li-ion"))); }

    @Test void passportIsCreatedWithUniqueCodeAndCreationEvent() {
        when(passports.findByPassportCode(anyString())).thenReturn(Optional.empty()); when(lookup.category(2L)).thenReturn(new ProductCategory());
        Passport p = passportService().createFor(listing, 1L); assertTrue(p.getPassportCode().matches("ECP-[A-Z2-9]{10}")); assertEquals(LifecycleStage.LISTED, p.getStage()); assertEquals(1L, p.getCurrentOwnerId());
        verify(events).save(argThat(e -> "CREATED".equals(e.getEventType())));
    }
    @Test void ownershipMovesToBuyerOnDelivery() {
        Passport p = new Passport(); p.setId(1L); p.setCurrentOwnerId(1L); when(passports.findByListingId(10L)).thenReturn(Optional.of(p));
        passportService().onDelivered(listing, 2L); assertEquals(2L, p.getCurrentOwnerId()); assertEquals(LifecycleStage.DELIVERED, p.getStage());
        verify(events).save(argThat(e -> "OWNERSHIP_TRANSFERRED".equals(e.getEventType())));
    }
    @Test void dismantlingCreatesLinkedChildPassportsAndRecordsLifecycle() {
        Passport p = root(LifecycleStage.DELIVERED, 2L); dismantlable(true); when(passports.findByPassportCode(startsWith("ECC-"))).thenReturn(Optional.empty());
        passportService().dismantle(BUYER, "ECP-AAAAAAAAAA", parts());
        ArgumentCaptor<Passport> c = ArgumentCaptor.forClass(Passport.class); verify(passports, times(3)).save(c.capture());
        c.getAllValues().forEach(ch -> { assertEquals(1L, ch.getParentId()); assertEquals(2L, ch.getCurrentOwnerId()); assertTrue(ch.getPassportCode().startsWith("ECC-")); assertEquals(LifecycleStage.COMPONENT_RECOVERED, ch.getStage()); });
        assertEquals(LifecycleStage.DISMANTLED, p.getStage()); verify(lifecycle).record(eq(10L), eq(LifecycleStage.DISMANTLED), any(), eq(2L), anyString());
    }
    @Test void onlyTheCurrentOwnerCanDismantle() { root(LifecycleStage.DELIVERED, 2L); dismantlable(true); assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> passportService().dismantle(OTHER, "ECP-AAAAAAAAAA", parts())).status()); }
    @Test void nonDismantlableCategoriesAndUndeliveredProductsAreRefused() {
        root(LifecycleStage.DELIVERED, 2L); dismantlable(false); assertThrows(ApiException.class, () -> passportService().dismantle(BUYER, "ECP-AAAAAAAAAA", parts()));
        dismantlable(true); root(LifecycleStage.PURCHASED, 2L); assertThrows(ApiException.class, () -> passportService().dismantle(BUYER, "ECP-AAAAAAAAAA", parts()));
    }
    @Test void hazardousProductsMayOnlyBeDismantledBySpecialistRecyclers() {
        root(LifecycleStage.DELIVERED, 2L); dismantlable(true); listing.setShippingBlocked(true);
        BuyerProfile refurb = new BuyerProfile(); refurb.setVerified(true); refurb.setBuyerType(BuyerType.REFURBISHER); when(buyers.findByUserId(2L)).thenReturn(Optional.of(refurb));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> passportService().dismantle(BUYER, "ECP-AAAAAAAAAA", parts())).status());
        refurb.setBuyerType(BuyerType.RECYCLER); when(passports.findByPassportCode(startsWith("ECC-"))).thenReturn(Optional.empty()); assertDoesNotThrow(() -> passportService().dismantle(BUYER, "ECP-AAAAAAAAAA", parts()));
    }
    private Passport component(Long owner) { Passport c = new Passport(); c.setId(2L); c.setParentId(1L); c.setPassportCode("ECC-BBBBBBBBBB"); c.setName("RAM"); c.setStage(LifecycleStage.COMPONENT_RECOVERED); c.setCurrentOwnerId(owner); when(passports.findByPassportCode("ECC-BBBBBBBBBB")).thenReturn(Optional.of(c)); return c; }
    @Test void componentResaleTransfersOwnershipAndRecordsValue() {
        Passport c = component(2L); AppUser to = new AppUser(); to.setId(3L); AppRole br = new AppRole(); br.setName("BUYER"); to.getRoles().add(br); when(users.findById(3L)).thenReturn(Optional.of(to));
        passportService().componentAction(BUYER, "ECC-BBBBBBBBBB", new ComponentActionRequest("RESELL", 3L, new BigDecimal("700"), "RAM stick"));
        assertEquals(3L, c.getCurrentOwnerId()); verify(events).save(argThat(e -> "COMPONENT_RESOLD".equals(e.getEventType()) && new BigDecimal("700").equals(e.getValueAmount()))); verify(notifications).notify(eq(3L), anyString(), anyString(), anyString());
    }
    @Test void resaleNeedsAmountAndABuyerRecipient() {
        component(2L); assertThrows(ApiException.class, () -> passportService().componentAction(BUYER, "ECC-BBBBBBBBBB", new ComponentActionRequest("RESELL", null, null, null)));
        AppUser notBuyer = new AppUser(); notBuyer.setId(4L); when(users.findById(4L)).thenReturn(Optional.of(notBuyer));
        assertThrows(ApiException.class, () -> passportService().componentAction(BUYER, "ECC-BBBBBBBBBB", new ComponentActionRequest("RESELL", 4L, new BigDecimal("1"), null)));
        assertThrows(ApiException.class, () -> passportService().componentAction(OTHER, "ECC-BBBBBBBBBB", new ComponentActionRequest("RECOVER", null, null, null)));
    }
    @Test void measuredWeightsOnlyFromVerifiedRecyclersOrAdmins() {
        root(LifecycleStage.DELIVERED, 2L); BuyerProfile p = new BuyerProfile(); p.setVerified(true); p.setBuyerType(BuyerType.INDIVIDUAL); when(buyers.findByUserId(2L)).thenReturn(Optional.of(p));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> passportService().outcome(BUYER, "ECP-AAAAAAAAAA", new OutcomeRequest(Pathway.RECYCLE, "n", new BigDecimal("1.8")))).status());
        passportService().outcome(BUYER, "ECP-AAAAAAAAAA", new OutcomeRequest(Pathway.REUSE, "using it", null)); verify(lifecycle).recordOutcome(eq(listing), eq(Pathway.REUSE), eq(2L), anyString(), isNull());
    }
    @Test void outcomesRequireDelivery() { root(LifecycleStage.PURCHASED, 2L); assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> passportService().outcome(BUYER, "ECP-AAAAAAAAAA", new OutcomeRequest(Pathway.REUSE, null, null))).status()); }
    @Test void qrCodeIsAPngForTheCode() {
        root(LifecycleStage.LISTED, 1L); byte[] png = passportService().qrPng("ECP-AAAAAAAAAA", 200); assertEquals((byte) 0x89, png[0]); assertEquals((byte) 'P', png[1]); assertTrue(png.length > 200);
    }

    // ================= Environmental impact =================
    @Mock EnvironmentalImpactRepository impacts; @Mock EnvironmentalFactorRepository factors; private EnvironmentalService envService() { return new EnvironmentalService(impacts, factors, categories, orders, audit); }
    private void factors() { EnvironmentalFactor f = new EnvironmentalFactor(); f.setCarbonReusePerKg(new BigDecimal("35")); f.setCarbonRepairPerKg(new BigDecimal("28")); f.setCarbonRefurbishPerKg(new BigDecimal("30")); f.setCarbonRecyclePerKg(new BigDecimal("9")); f.setMetalsRecoveryPct(new BigDecimal("22")); when(factors.findByCategoryId(2L)).thenReturn(Optional.of(f)); }
    @Test void impactIsStoredOnceWithInputsAndMethodologyAndFlaggedEstimated() {
        factors(); envService().compute(listing, null, Pathway.RECYCLE, 3L, null);
        ArgumentCaptor<EnvironmentalImpact> c = ArgumentCaptor.forClass(EnvironmentalImpact.class); verify(impacts).save(c.capture()); EnvironmentalImpact i = c.getValue();
        assertFalse(i.isMeasured()); assertEquals(0, new BigDecimal("4.000").compareTo(i.getEwasteDivertedKg())); assertEquals(0, new BigDecimal("36.000").compareTo(i.getCarbonAvoidedKg()));
        assertTrue(i.getMethodology().contains("ESTIMATES")); assertTrue(i.getInputs().contains("\"quantity\":2"));
    }
    @Test void measuredWeightIsFlaggedAndASecondOutcomeDoesNotDoubleCount() {
        factors(); envService().compute(listing, null, Pathway.RECYCLE, 3L, new BigDecimal("1.5")); ArgumentCaptor<EnvironmentalImpact> c = ArgumentCaptor.forClass(EnvironmentalImpact.class); verify(impacts).save(c.capture()); assertTrue(c.getValue().isMeasured());
        when(impacts.findByListingIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(new EnvironmentalImpact())); envService().compute(listing, null, Pathway.REUSE, 3L, null); verify(impacts, times(1)).save(any());
    }
    @Test void summaryAggregatesPathwaysAndSeparatesEstimatedFromMeasured() {
        when(impacts.summaryAll()).thenReturn(List.<Object[]>of(new Object[]{Pathway.REUSE, 2L, 10.0, 100.0, 0.0, 0L}, new Object[]{Pathway.RECYCLE, 3L, 20.0, 50.0, 4.4, 1L}));
        ImpactDto d = envService().summary("global", null); assertEquals(30.0, d.ewasteDivertedKg()); assertEquals(150.0, d.carbonAvoidedKg()); assertEquals(2, d.reused()); assertEquals(3, d.recycled()); assertEquals(1, d.measuredRecords()); assertEquals(4, d.estimatedRecords());
        assertTrue(d.note().contains("ESTIMATES"));
    }

    // ================= Listing moderation =================
    @Mock ListingImageRepository images; @Mock SellerProfileRepository sellers; @Mock AuctionService auctions; @Mock ListingMapper mapper; @Mock FileStorageService storage;
    private ListingService listingService() { return new ListingService(listings, images, sellers, lookup, scoring, hazard, lifecycle, passportSvc, auctions, mapper, storage, audit, notifications, orders); }
    @Test void approvalRequiresAVerifiedSeller() { when(lookup.sellerVerified(1L)).thenReturn(false); assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> listingService().approve(50L, 10L)).status()); assertEquals(ListingStatus.PENDING_APPROVAL, listing.getStatus()); }
    @Test void approvalGoesLiveOpensAuctionAndNotifiesSeller() {
        when(lookup.sellerVerified(1L)).thenReturn(true); listing.setAuctionEnabled(true); listingService().approve(50L, 10L);
        assertEquals(ListingStatus.APPROVED, listing.getStatus()); verify(auctions).open(listing); verify(passportSvc).onApproved(listing, 50L); verify(notifications).notify(eq(1L), anyString(), anyString(), anyString());
    }
    @Test void onlyPendingListingsCanBeApprovedAndRejectionStoresTheReason() {
        when(lookup.sellerVerified(1L)).thenReturn(true); listing.setStatus(ListingStatus.SOLD); assertThrows(ApiException.class, () -> listingService().approve(50L, 10L));
        listing.setStatus(ListingStatus.PENDING_APPROVAL); listingService().reject(50L, 10L, "blurry photos"); assertEquals(ListingStatus.REJECTED, listing.getStatus()); assertEquals("blurry photos", listing.getRejectionReason());
    }
    @Test void sellersCannotEditOthersListingsOrApprovedOnes() {
        ListingRequest r = new ListingRequest("n", null, null, 2L, 1, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 1, BigDecimal.TEN, null, "Pune", null, BigDecimal.ONE, null, null, false, null, null);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> listingService().update(new AuthUser(77L, "z", Set.of(Role.SELLER)), 10L, r)).status());
        listing.setStatus(ListingStatus.APPROVED); assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> listingService().update(SELLER, 10L, r)).status());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> listingService().withdraw(SELLER, 10L)).status());
    }
    @Test void withdrawalIsASoftDeleteThatKeepsHistory() { listingService().withdraw(SELLER, 10L); assertEquals(ListingStatus.WITHDRAWN, listing.getStatus()); }
    @Test void listingWithOrdersCannotBeWithdrawn() { when(orders.existsByListingId(10L)).thenReturn(true); assertThrows(ApiException.class, () -> listingService().withdraw(SELLER, 10L)); }
    @Test void hazardousItemsCannotBeRoutedToResaleByTheSeller() {
        ProductCategory cat = new ProductCategory(); cat.setActive(true); when(lookup.category(2L)).thenReturn(cat);
        HazardRule r = new HazardRule(); r.setCode("BATTERY_SWELLING"); r.setPoints(40); r.setBlocksShipping(true); r.setDescription("d"); r.setHandling("h"); r.setEnabled(true); when(rules.findByEnabledTrue()).thenReturn(List.of(r));
        doAnswer(i -> { Listing l = i.getArgument(0); l.setRecommendedPathway(Pathway.RECYCLE); return null; }).when(scoring).apply(any());
        ListingRequest req = new ListingRequest("Phone", null, null, 2L, 12, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 1, BigDecimal.TEN, null, "Pune", null, BigDecimal.ONE, Pathway.REUSE, Map.of("BATTERY_SWELLING", true), false, null, null);
        ApiException e = assertThrows(ApiException.class, () -> listingService().create(SELLER, req)); assertEquals(HttpStatus.BAD_REQUEST, e.status()); assertTrue(e.getMessage().contains("RECYCLE"));
        verify(listings, never()).save(any());
    }

    // ================= Recommendations =================
    @Mock BuyerRequirementRepository requirements; @Mock WishlistRepository wishlist; 
    @Test void recommendationsAreRankedExplainedAndExcludeOwnListings() {
        Listing good = live(11L, 2L, "24000", "Patiala", ItemCondition.GOOD, "Dell laptop office work SSD", 80); Listing poor = live(12L, 2L, "90000", "Delhi", ItemCondition.POOR, "Old laptop", 20);
        Listing own = live(13L, 2L, "24000", "Patiala", ItemCondition.GOOD, "my own laptop", 90); own.setSellerId(2L); Listing other = live(14L, 5L, "100", "Patiala", ItemCondition.GOOD, "monitor", 90);
        BuyerRequirement b = new BuyerRequirement(); b.setBuyerId(2L); b.setCategoryId(2L); b.setMaxBudget(new BigDecimal("30000")); b.setCity("Patiala"); b.setIntendedUse("office work"); b.setActive(true);
        when(requirements.findByBuyerIdAndActiveTrue(2L)).thenReturn(List.of(b)); when(listings.findByStatus(ListingStatus.APPROVED)).thenReturn(List.of(good, poor, own, other)); when(categories.findAll()).thenReturn(List.of());
        when(mapper.toDtos(anyCollection())).thenAnswer(i -> new ArrayList<ListingDto>(Collections.nCopies(((Collection<?>) i.getArgument(0)).size(), (ListingDto) null)));
        List<RecommendationDto> out = new RecommendationService(requirements, listings, wishlist, categories, mapper, lookup, audit).recommend(BUYER, 10);
        assertFalse(out.isEmpty()); assertTrue(out.get(0).matchScore() >= out.get(out.size() - 1).matchScore()); assertTrue(out.get(0).reasons().size() >= 6); assertTrue(out.get(0).basedOn().startsWith("Your requirement"));
        @SuppressWarnings("unchecked") ArgumentCaptor<Collection<Listing>> c = (ArgumentCaptor<Collection<Listing>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Collection.class); verify(mapper).toDtos(c.capture());
        assertTrue(c.getValue().stream().noneMatch(l -> l.getId() == 13L || l.getId() == 14L)); assertEquals(11L, c.getValue().iterator().next().getId().longValue());
    }
    private Listing live(Long id, Long cat, String price, String city, ItemCondition cond, String text, double score) {
        Listing l = new Listing(); l.setId(id); l.setSellerId(1L); l.setCategoryId(cat); l.setExpectedPrice(new BigDecimal(price)); l.setPickupCity(city); l.setPhysicalCondition(cond); l.setName(text); l.setPathway(Pathway.REFURBISH); l.setCircularScore(BigDecimal.valueOf(score)); return l;
    }
}
