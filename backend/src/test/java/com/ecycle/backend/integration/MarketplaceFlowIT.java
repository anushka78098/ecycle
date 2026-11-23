package com.ecycle.backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end workflow against real PostgreSQL (Flyway migrations included) and MongoDB (chat, notifications, assistant history): authentication, RBAC, listing approval, order, server-verified payment,
 * shipping, delivery, passport ownership, dispute with evidence, resolution with refund, hazard routing, reverse auction, and DB-level immutability.
 * Run with: mvn -pl backend verify   (needs Docker for Testcontainers).
 */
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings({"unchecked", "rawtypes"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=integration-test-secret-integration-test-secret", "app.admin.email=admin@test.local", "app.admin.password=Admin@12345",
        "app.rate-limit.per-minute=10000", "app.assistant.provider=none", "app.certificate.signing-secret=integration-test-certificate-secret", "app.storage-dir=target/it-uploads", "app.payment.provider=mock", "app.seed-demo=false"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MarketplaceFlowIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7");
    @Autowired TestRestTemplate rest; @Autowired JdbcTemplate jdbc;

    static String adminToken, sellerToken, buyerToken, strangerToken; static Long sellerId, buyerId, listingId, orderId, paymentId, disputeId;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> ResponseEntity<T> call(HttpMethod m, String path, String token, Object body, Class<T> type) {
        HttpHeaders h = new HttpHeaders(); if (token != null) h.setBearerAuth(token);
        if (!(body instanceof MultiValueMap)) h.setContentType(MediaType.APPLICATION_JSON); else h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(path, m, new HttpEntity<>(body, h), type);
    }
    private ResponseEntity<Map> post(String p, String t, Object b) { return call(HttpMethod.POST, p, t, b == null ? Map.of() : b, Map.class); }
    private ResponseEntity<Map> get(String p, String t) { return call(HttpMethod.GET, p, t, null, Map.class); }
    private ResponseEntity<List> list(String p, String t) { return call(HttpMethod.GET, p, t, null, List.class); }
    private static Number num(Object o) { return (Number) o; }

    private Map<String, Object> register(String email, String role, String name) {
        Map<String, Object> b = new HashMap<>(Map.of("email", email, "password", "Passw0rd!x", "fullName", name, "role", role, "city", "Pune"));
        ResponseEntity<Map> r = post("/api/auth/register", null, b); assertEquals(HttpStatus.CREATED, r.getStatusCode(), String.valueOf(r.getBody())); return r.getBody();
    }
    private Long categoryId(String name) { for (Object o : list("/api/categories", null).getBody()) { Map<?, ?> m = (Map<?, ?>) o; if (name.equals(m.get("name"))) return num(m.get("id")).longValue(); } throw new AssertionError("category " + name); }
    private Map<String, Object> listingBody(String name, Map<String, Boolean> hazard, boolean auction, String pathway) {
        Map<String, Object> b = new HashMap<>(); b.put("name", name); b.put("categoryId", categoryId("Laptops")); b.put("ageMonths", 24); b.put("physicalCondition", "GOOD"); b.put("functionalStatus", "FULLY_WORKING");
        b.put("quantity", 1); b.put("expectedPrice", 5000); b.put("pickupCity", "Pune"); b.put("weightKg", 2); b.put("hazardAnswers", hazard); b.put("auctionEnabled", auction);
        if (auction) { b.put("auctionHours", 24); b.put("auctionMinPrice", 100); } if (pathway != null) b.put("pathway", pathway); return b;
    }
    private Map createAndApprove(Map<String, Object> body) {
        ResponseEntity<Map> c = post("/api/listings", sellerToken, body); assertEquals(HttpStatus.CREATED, c.getStatusCode(), String.valueOf(c.getBody()));
        Long id = num(c.getBody().get("id")).longValue(); ResponseEntity<Map> a = post("/api/admin/listings/" + id + "/approve", adminToken, null); assertEquals(HttpStatus.OK, a.getStatusCode(), String.valueOf(a.getBody())); return a.getBody();
    }

    @Test @Order(1) void unauthenticatedRequestsAreRejectedAndHealthIsPublic() {
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/listings", null).getStatusCode()); assertEquals(HttpStatus.UNAUTHORIZED, get("/api/admin/users", null).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/health", null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/listings", "not.a.jwt").getStatusCode());
    }
    @Test @Order(2) void registrationLoginAndRoleEnforcement() {
        sellerId = num(register("seller@it.test", "SELLER", "Sam Seller").get("userId")).longValue(); buyerId = num(register("buyer@it.test", "BUYER", "Bea Buyer").get("userId")).longValue();
        strangerToken = (String) register("stranger@it.test", "BUYER", "Sid Stranger").get("token");
        Map<String, Object> bad = new HashMap<>(Map.of("email", "evil@it.test", "password", "Passw0rd!x", "fullName", "E", "role", "ADMIN")); assertEquals(HttpStatus.BAD_REQUEST, post("/api/auth/register", null, bad).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, post("/api/auth/register", null, new HashMap<>(Map.of("email", "seller@it.test", "password", "Passw0rd!x", "fullName", "Dup", "role", "SELLER"))).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, post("/api/auth/login", null, Map.of("email", "seller@it.test", "password", "wrong-password")).getStatusCode());
        sellerToken = (String) post("/api/auth/login", null, Map.of("email", "seller@it.test", "password", "Passw0rd!x")).getBody().get("token");
        buyerToken = (String) post("/api/auth/login", null, Map.of("email", "buyer@it.test", "password", "Passw0rd!x")).getBody().get("token");
        adminToken = (String) post("/api/auth/login", null, Map.of("email", "admin@test.local", "password", "Admin@12345")).getBody().get("token"); assertNotNull(adminToken);
        assertEquals(HttpStatus.FORBIDDEN, get("/api/admin/users", buyerToken).getStatusCode(), "buyer must not reach admin API");
        assertEquals(HttpStatus.FORBIDDEN, post("/api/listings", buyerToken, listingBody("x", Map.of(), false, null)).getStatusCode(), "buyer must not create listings");
        assertEquals(HttpStatus.FORBIDDEN, get("/api/resolver/disputes", sellerToken).getStatusCode(), "seller must not reach resolver workspace");
        assertEquals(HttpStatus.OK, get("/api/dashboard/SELLER", sellerToken).getStatusCode()); assertEquals(HttpStatus.FORBIDDEN, get("/api/dashboard/RESOLVER", sellerToken).getStatusCode());
    }
    @Test @Order(3) void logoutRevokesTheToken() {
        String t = (String) post("/api/auth/login", null, Map.of("email", "stranger@it.test", "password", "Passw0rd!x")).getBody().get("token");
        assertEquals(HttpStatus.OK, get("/api/profile", t).getStatusCode()); post("/api/auth/logout", t, null); assertEquals(HttpStatus.UNAUTHORIZED, get("/api/profile", t).getStatusCode());
    }
    @Test @Order(4) void listingMustBeApprovedAndSellerVerifiedBeforeGoingLive() {
        ResponseEntity<Map> c = post("/api/listings", sellerToken, listingBody("ThinkPad T480", Map.of("STORAGE_DEVICE_PRESENT", true), false, null)); assertEquals(HttpStatus.CREATED, c.getStatusCode(), String.valueOf(c.getBody()));
        listingId = num(c.getBody().get("id")).longValue(); assertEquals("PENDING_APPROVAL", c.getBody().get("status")); assertNotNull(c.getBody().get("passportCode"));
        assertTrue(num(((Map<?, ?>) c.getBody().get("score")).get("overall")).doubleValue() > 0); assertEquals(Boolean.TRUE, c.getBody().get("requiresSanitization"));
        assertEquals(HttpStatus.NOT_FOUND, get("/api/listings/" + listingId, buyerToken).getStatusCode(), "pending listings are invisible to buyers");
        assertEquals(HttpStatus.CONFLICT, post("/api/admin/listings/" + listingId + "/approve", adminToken, null).getStatusCode(), "seller not yet verified");
        assertEquals(HttpStatus.OK, post("/api/admin/sellers/" + sellerId + "/verify?approve=true", adminToken, null).getStatusCode());
        assertEquals("APPROVED", post("/api/admin/listings/" + listingId + "/approve", adminToken, null).getBody().get("status"));
        assertEquals(HttpStatus.OK, get("/api/listings/" + listingId, buyerToken).getStatusCode());
    }
    @Test @Order(5) void unsanitizedStorageDeviceCannotBeSoldForReuseUntilAdminVerifiesErasure() {
        Map<String, Object> order = Map.of("listingId", listingId, "shippingAddress", "12 Main St, Pune");
        assertEquals(HttpStatus.CONFLICT, post("/api/orders", buyerToken, order).getStatusCode(), "sanitization gate");
        assertEquals(HttpStatus.NOT_FOUND, get("/api/listings/" + listingId + "/sanitization/certificate", sellerToken).getStatusCode(), "no certificate before verification");
        ResponseEntity<Map> s = post("/api/listings/" + listingId + "/sanitization", sellerToken, Map.of("method", "NIST 800-88 Purge", "toolName", "blancco", "evidenceRef", "report-123")); assertEquals(HttpStatus.OK, s.getStatusCode(), String.valueOf(s.getBody()));
        assertEquals(HttpStatus.FORBIDDEN, post("/api/admin/sanitization/" + num(s.getBody().get("id")) + "/verify", sellerToken, Map.of("approve", true)).getStatusCode());
        ResponseEntity<Map> v = post("/api/admin/sanitization/" + num(s.getBody().get("id")) + "/verify", adminToken, Map.of("approve", true, "note", "log verified")); assertEquals(HttpStatus.OK, v.getStatusCode());
        assertTrue(((String) v.getBody().get("certificateNo")).startsWith("DSC-")); assertEquals(HttpStatus.OK, get("/api/listings/" + listingId + "/sanitization/certificate", sellerToken).getStatusCode());
    }
    @Test @Order(6) void orderPaymentShippingAndDeliveryWithServerSideVerification() {
        ResponseEntity<Map> o = post("/api/orders", buyerToken, Map.of("listingId", listingId, "shippingAddress", "12 Main St, Pune")); assertEquals(HttpStatus.CREATED, o.getStatusCode(), String.valueOf(o.getBody()));
        orderId = num(o.getBody().get("id")).longValue(); assertEquals("PENDING_PAYMENT", o.getBody().get("status"));
        assertEquals(HttpStatus.CONFLICT, post("/api/orders", strangerToken, Map.of("listingId", listingId, "shippingAddress", "x")).getStatusCode(), "double sale prevented (reserved)");
        paymentId = num(post("/api/payments/orders/" + orderId + "/init", buyerToken, null).getBody().get("paymentId")).longValue();
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/payments/" + paymentId + "/verify", buyerToken, Map.of("providerPaymentId", "pay_fake", "signature", "forged")).getStatusCode(), "forged client 'success' is never trusted");
        paymentId = num(post("/api/payments/orders/" + orderId + "/init", buyerToken, null).getBody().get("paymentId")).longValue();   // a failed payment requires a fresh attempt
        assertEquals(HttpStatus.FORBIDDEN, post("/api/payments/" + paymentId + "/mock-confirm", strangerToken, null).getStatusCode());
        ResponseEntity<Map> paid = post("/api/payments/" + paymentId + "/mock-confirm", buyerToken, null); assertEquals(HttpStatus.OK, paid.getStatusCode(), String.valueOf(paid.getBody()));
        assertEquals("PAID", paid.getBody().get("status")); assertNotNull(paid.getBody().get("receiptNo"));
        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.PUT, "/api/orders/" + orderId + "/shipment", buyerToken, Map.of("carrier", "DHL", "status", "SHIPPED"), Map.class).getStatusCode());
        assertEquals("SHIPPED", call(HttpMethod.PUT, "/api/orders/" + orderId + "/shipment", sellerToken, Map.of("carrier", "DHL", "trackingNo", "T1", "status", "SHIPPED"), Map.class).getBody().get("status"));
        assertEquals(HttpStatus.FORBIDDEN, post("/api/orders/" + orderId + "/confirm-delivery", sellerToken, null).getStatusCode());
        assertEquals("DELIVERED", post("/api/orders/" + orderId + "/confirm-delivery", buyerToken, null).getBody().get("status"));
        Map passport = get("/api/listings/" + listingId + "/passport", buyerToken).getBody(); assertEquals("Bea Buyer", passport.get("ownerLabel")); assertEquals("DELIVERED", passport.get("stage"));
        List<?> events = (List<?>) passport.get("events"); assertTrue(events.stream().anyMatch(e -> "OWNERSHIP_TRANSFERRED".equals(((Map<?, ?>) e).get("type"))));
        assertEquals(HttpStatus.OK, call(HttpMethod.GET, "/api/passports/" + passport.get("code") + "/qr", buyerToken, null, byte[].class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get("/api/orders/" + orderId, strangerToken).getStatusCode(), "orders are private to their parties");
    }
    @Test @Order(7) void disputeWithEvidenceResolvedWithRefundByResolverWorkflow() {
        ResponseEntity<Map> d = post("/api/disputes", buyerToken, Map.of("orderId", orderId, "category", "DAMAGED_PRODUCT", "description", "Screen cracked on arrival")); assertEquals(HttpStatus.CREATED, d.getStatusCode(), String.valueOf(d.getBody()));
        disputeId = num(d.getBody().get("id")).longValue(); assertEquals("DISPUTED", get("/api/orders/" + orderId, buyerToken).getBody().get("status"));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/disputes/" + disputeId, strangerToken).getStatusCode());
        byte[] png = new byte[64]; System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, png, 0, 8);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>(); parts.add("file", new ByteArrayResource(png) { @Override public String getFilename() { return "damage.png"; } }); parts.add("category", "PHOTO"); parts.add("description", "cracked corner");
        ResponseEntity<Map> up = call(HttpMethod.POST, "/api/disputes/" + disputeId + "/evidence", buyerToken, parts, Map.class); assertEquals(HttpStatus.OK, up.getStatusCode(), String.valueOf(up.getBody())); assertEquals(64, ((String) up.getBody().get("sha256")).length());
        MultiValueMap<String, Object> fake = new LinkedMultiValueMap<>(); fake.add("file", new ByteArrayResource(new byte[64]) { @Override public String getFilename() { return "evil.png"; } }); fake.add("category", "PHOTO");
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.POST, "/api/disputes/" + disputeId + "/evidence", buyerToken, fake, Map.class).getStatusCode(), "file content must match the declared type");
        assertEquals(HttpStatus.FORBIDDEN, post("/api/resolver/disputes/" + disputeId + "/assign", buyerToken, null).getStatusCode());
        assertEquals("ASSIGNED", ((Map<?, ?>) post("/api/resolver/disputes/" + disputeId + "/assign", adminToken, null).getBody()).get("status"));
        assertEquals(HttpStatus.OK, post("/api/resolver/disputes/" + disputeId + "/resolve", adminToken, Map.of("type", "FULL_REFUND", "decisionText", "Seller shipped a damaged unit")).getStatusCode());
        assertEquals("REFUNDED", get("/api/orders/" + orderId, buyerToken).getBody().get("status")); assertEquals("REFUNDED", get("/api/orders/" + orderId, buyerToken).getBody().get("paymentStatus"));
        assertEquals(HttpStatus.CONFLICT, post("/api/resolver/disputes/" + disputeId + "/reject", adminToken, Map.of("reason", "late")).getStatusCode(), "final disputes cannot change");
    }
    @Test @Order(8) void databaseRefusesToAlterEvidenceAuditLogsAndBids() {
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE dispute_evidence SET description = 'tampered'"));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM dispute_evidence"));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE audit_logs SET action = 'X'"));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM product_lifecycle"));
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class) > 10);
    }
    @Test @Order(9) void hazardousProductsAreBlockedFromNormalBuyersAndResaleRouting() {
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/listings", sellerToken, listingBody("Swollen phone", Map.of("BATTERY_SWELLING", true), false, "REUSE")).getStatusCode(), "hazardous items cannot be routed to resale");
        Map live = createAndApprove(listingBody("Swollen phone", Map.of("BATTERY_SWELLING", true), false, null)); Long id = num(live.get("id")).longValue();
        assertEquals(Boolean.TRUE, live.get("shippingBlocked")); assertTrue(Set.of("HIGH", "CRITICAL").contains(live.get("hazardLevel"))); assertNotNull(live.get("specialistRequired"));
        assertEquals(HttpStatus.CONFLICT, post("/api/orders", buyerToken, Map.of("listingId", id, "shippingAddress", "x")).getStatusCode(), "only verified specialist recyclers can buy hazardous items");
    }
    @Test @Order(10) void reverseAuctionRequiresVerifiedBuyersAndSellerAwardsABid() {
        Map live = createAndApprove(listingBody("PCB scrap lot", Map.of(), true, "RECYCLE")); Long aid = num(live.get("auctionId")).longValue(); assertNotNull(aid);
        assertEquals(HttpStatus.CONFLICT, post("/api/orders", buyerToken, Map.of("listingId", live.get("id"), "shippingAddress", "x")).getStatusCode(), "auction items cannot be bought directly");
        Map<String, Object> bid = Map.of("price", 3000, "pathway", "RECYCLE", "recoveryPlan", "Shred and smelt");
        assertEquals(HttpStatus.FORBIDDEN, post("/api/auctions/" + aid + "/bids", buyerToken, bid).getStatusCode(), "unverified buyers cannot bid");
        assertEquals(HttpStatus.OK, post("/api/admin/buyers/" + buyerId + "/verify", adminToken, Map.of("approve", true, "recoveryCapability", 80, "environmentalRating", 60)).getStatusCode());
        ResponseEntity<Map> b1 = post("/api/auctions/" + aid + "/bids", buyerToken, bid); assertEquals(HttpStatus.CREATED, b1.getStatusCode(), String.valueOf(b1.getBody())); assertTrue(num(b1.getBody().get("circularBidScore")).doubleValue() > 0);
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/auctions/" + aid + "/bids", buyerToken, Map.of("price", 50, "pathway", "RECYCLE")).getStatusCode(), "below minimum price");
        Map auction = get("/api/auctions/" + aid, sellerToken).getBody(); assertEquals(1, ((List<?>) auction.get("bids")).size()); assertNotNull(auction.get("highestPriceBid")); assertNotNull(auction.get("highestScoreBid"));
        Long bidId = num(((Map<?, ?>) ((List<?>) auction.get("bids")).get(0)).get("id")).longValue();
        assertEquals(HttpStatus.FORBIDDEN, post("/api/auctions/" + aid + "/accept/" + bidId, buyerToken, null).getStatusCode(), "only the seller accepts bids");
        ResponseEntity<Map> won = post("/api/auctions/" + aid + "/accept/" + bidId, sellerToken, null); assertEquals(HttpStatus.OK, won.getStatusCode(), String.valueOf(won.getBody())); assertEquals(0, new java.math.BigDecimal(won.getBody().get("amount").toString()).compareTo(new java.math.BigDecimal("3000")));
        assertEquals("AWARDED", get("/api/auctions/" + aid, sellerToken).getBody().get("status"));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE bids SET price = 1"), "bids are append-only");
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM bids"));
    }

    @Test @Order(11) void certificatesCustodyPickupsEcoPointsAndAssistantWorkTogether() {
        // sanitization certificate was issued when the admin verified the evidence in test 5
        List<?> certs = list("/api/certificates", sellerToken).getBody(); assertFalse(certs.isEmpty(), "sanitization certificate exists");
        Map<?, ?> c = (Map<?, ?>) certs.get(0); String no = (String) c.get("certificateNo"), code = (String) c.get("verificationCode"); assertEquals("DATA_SANITIZATION", c.get("type"));
        byte[] pdf = call(HttpMethod.GET, "/api/certificates/" + no + "/pdf", sellerToken, null, byte[].class).getBody(); assertTrue(new String(pdf, 0, 5).startsWith("%PDF-"), "PDF returned");
        assertEquals(HttpStatus.NOT_FOUND, get("/api/certificates/" + no, buyerToken).getStatusCode(), "other users cannot read it");
        assertEquals(Boolean.TRUE, get("/api/public/certificates/verify?no=" + no + "&code=" + code, null).getBody().get("valid"), "public verification with code");
        assertEquals(Boolean.FALSE, get("/api/public/certificates/verify?no=" + no + "&code=AAAA-AAAA-AAAA", null).getBody().get("valid"), "wrong code");
        // chain of custody
        String asset = (String) ((Map<?, ?>) list("/api/passports/mine", sellerToken).getBody().get(0)).get("code");
        Map chain = get("/api/custody/" + asset, sellerToken).getBody(); assertEquals(Boolean.TRUE, chain.get("valid")); assertTrue(num(chain.get("length")).intValue() >= 2);
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE custody_events SET detail = 'x'"), "custody log is append-only");
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM certificates"), "certificates are immutable");
        // recycler, facility, pickup state machine
        register("recycler@it.test", "BUYER", "Rita Recycler"); // buyer type is set below through the admin verification
        String recToken = (String) post("/api/auth/login", null, Map.of("email", "recycler@it.test", "password", "Passw0rd!x")).getBody().get("token");
        Long recId = jdbc.queryForObject("select id from users where email='recycler@it.test'", Long.class);
        jdbc.update("update buyer_profiles set buyer_type='RECYCLER' where user_id=?", recId);
        assertEquals(HttpStatus.OK, post("/api/admin/buyers/" + recId + "/verify", adminToken, Map.of("approve", true, "recoveryCapability", 85, "environmentalRating", 80)).getStatusCode());
        Map<String, Object> fac = new HashMap<>(Map.of("facilityName", "IT Hub", "address", "1 Test Rd", "city", "Pune", "latitude", 18.52, "longitude", 73.85, "serviceRadiusKm", 50, "active", true, "acceptedCategories", "Laptops"));
        assertEquals(HttpStatus.FORBIDDEN, post("/api/recyclers", sellerToken, fac).getStatusCode(), "only recyclers add facilities");
        ResponseEntity<Map> f = post("/api/recyclers", recToken, fac); assertEquals(HttpStatus.CREATED, f.getStatusCode(), String.valueOf(f.getBody())); Long facId = num(f.getBody().get("id")).longValue();
        assertFalse(list("/api/recyclers?lat=18.5&lng=73.8&radiusKm=30", sellerToken).getBody().isEmpty(), "found by distance");
        Map<String, Object> pk = new HashMap<>(Map.of("locationId", facId, "itemsDescription", "Old laptop", "quantity", 1, "estWeightKg", 2.5, "dataBearing", false, "hazardous", false, "preferredAt", java.time.Instant.now().plusSeconds(86400).toString()));
        ResponseEntity<Map> p = post("/api/pickups", sellerToken, pk); assertEquals(HttpStatus.CREATED, p.getStatusCode(), String.valueOf(p.getBody())); Long pid = num(p.getBody().get("id")).longValue();
        assertEquals(HttpStatus.CONFLICT, post("/api/pickups/" + pid + "/status", sellerToken, Map.of("status", "ACCEPTED")).getStatusCode(), "requester cannot accept");
        assertEquals(HttpStatus.CONFLICT, post("/api/pickups/" + pid + "/status", recToken, Map.of("status", "PICKED_UP")).getStatusCode(), "no skipping states");
        for (String st : List.of("ACCEPTED", "EN_ROUTE", "PICKED_UP", "AT_FACILITY")) assertEquals(HttpStatus.OK, post("/api/pickups/" + pid + "/status", recToken, Map.of("status", st)).getStatusCode(), st);
        assertEquals(HttpStatus.NOT_FOUND, get("/api/pickups/" + pid, buyerToken).getStatusCode(), "pickups are private to the parties");
        ResponseEntity<Map> done = post("/api/pickups/" + pid + "/verify", recToken, Map.of("actualWeightKg", 2.4, "treatment", "Dismantled and materials recovered")); assertEquals(HttpStatus.OK, done.getStatusCode(), String.valueOf(done.getBody()));
        assertEquals("COMPLETED", done.getBody().get("status")); assertFalse(((List<?>) done.getBody().get("certificateNos")).isEmpty(), "recycling certificate issued");
        // eco-points, idempotency, notifications (MongoDB), assistant
        Map dash = get("/api/eco/dashboard", sellerToken).getBody(); int before = num(dash.get("lifetimePoints")).intValue(); assertTrue(before > 0, "points awarded");
        assertEquals(HttpStatus.CONFLICT, post("/api/pickups/" + pid + "/verify", recToken, Map.of("actualWeightKg", 2.4, "treatment", "again")).getStatusCode(), "cannot verify twice");
        assertEquals(before, num(get("/api/eco/dashboard", sellerToken).getBody().get("lifetimePoints")).intValue(), "no double award");
        assertFalse(list("/api/notifications", sellerToken).getBody().isEmpty(), "notifications stored in MongoDB");
        Map reply = post("/api/assistant/chat", sellerToken, Map.of("message", "How many eco-points do I have?")).getBody();
        assertTrue(((String) reply.get("reply")).contains("Balance"), String.valueOf(reply)); assertTrue(((List<?>) reply.get("toolsUsed")).contains("getMyEcoPoints"));
        Map prop = post("/api/assistant/chat", sellerToken, Map.of("conversationId", reply.get("conversationId"), "message", "cancel pickup " + p.getBody().get("trackingCode"))).getBody();
        assertEquals(HttpStatus.OK, post("/api/assistant/chat", buyerToken, Map.of("message", "hello")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, get("/api/assistant/conversations/" + reply.get("conversationId"), buyerToken).getStatusCode(), "conversations are private");
        assertNotNull(prop.get("reply"));
        assertTrue(list("/api/eco/leaderboard", sellerToken).getBody().size() >= 1);
    }
}
