package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data, created through the real services so every rule is exercised: about 20 records for each feature
 * (listings with generated photos, offers, orders, payments, disputes with evidence and messages, chats, bids, reviews, wishlist).
 * Deliberately NOT transactional: each service call commits on its own, and one failing step is logged without undoing the rest.
 */
@Component @RequiredArgsConstructor
public class DemoDataSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private final AuthService auth; private final SellerProfileRepository sellers; private final BuyerProfileRepository buyers; private final ProductCategoryRepository categories;
    private final ListingService listingService; private final ListingImageRepository listingImages; private final OfferService offers; private final OrderService orders;
    private final PaymentService payments; private final ReviewService reviews; private final DisputeService disputes; private final DisputeEvidenceRepository evidenceRepo;
    private final ChatService chat; private final AuctionService auctions; private final WishlistService wishlist; private final FileStorageService storage;

    private record Cat(String name, String brand, String model, String category, int months, ItemCondition cond, FunctionalStatus func, double kg, int price, String kind, String blurb) {}
    private static final List<Cat> CATALOG = List.of(
        new Cat("Dell Latitude 7490", "Dell", "Latitude 7490", "Laptops", 36, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 1.6, 22000, "laptop", "14-inch business laptop, 8GB RAM, 256GB SSD, about 3 hours of battery."),
        new Cat("HP EliteBook 840 G3", "HP", "EliteBook 840 G3", "Laptops", 48, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 1.5, 16500, "laptop", "Aluminium body, i5 6th gen, 8GB RAM, small dent on the lid."),
        new Cat("Lenovo ThinkPad T470", "Lenovo", "ThinkPad T470", "Laptops", 50, ItemCondition.FAIR, FunctionalStatus.FULLY_WORKING, 1.6, 18500, "laptop", "Reliable office laptop, keyboard slightly worn, dual battery."),
        new Cat("MacBook Air 2017", "Apple", "MacBook Air A1466", "Laptops", 72, ItemCondition.FAIR, FunctionalStatus.PARTIALLY_WORKING, 1.3, 28000, "laptop", "Works well but battery service recommended; charger included."),
        new Cat("iPhone 11 64GB", "Apple", "iPhone 11", "Smartphones", 40, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 0.19, 17500, "phone", "Original screen, 86% battery health, no Face ID issues."),
        new Cat("Samsung Galaxy S9", "Samsung", "SM-G960F", "Smartphones", 66, ItemCondition.FAIR, FunctionalStatus.FULLY_WORKING, 0.16, 6800, "phone", "Mild screen burn, everything else works."),
        new Cat("OnePlus 6T", "OnePlus", "A6013", "Smartphones", 60, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 0.18, 8200, "phone", "Clean unit, fresh battery, original box."),
        new Cat("Redmi Note 8", "Xiaomi", "Note 8", "Smartphones", 48, ItemCondition.POOR, FunctionalStatus.PARTIALLY_WORKING, 0.19, 3200, "phone", "Cracked back glass, touch works, speaker is weak."),
        new Cat("iPad Air 2", "Apple", "A1566", "Tablets", 84, ItemCondition.FAIR, FunctionalStatus.PARTIALLY_WORKING, 0.44, 9800, "tablet", "Cracked glass, display and touch fully functional."),
        new Cat("Galaxy Tab A 10.1", "Samsung", "SM-T510", "Tablets", 54, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 0.47, 5200, "tablet", "Good for kids or reading, charger included."),
        new Cat("Dell OptiPlex 3040 tower", "Dell", "OptiPlex 3040", "Desktop computers", 70, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 6.5, 11500, "box", "i5, 8GB RAM, no storage drive; ideal for refurbishing."),
        new Cat("HP ProDesk 400 G4", "HP", "ProDesk 400", "Desktop computers", 60, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 6.0, 12800, "box", "Small form factor, 16GB RAM, no drives."),
        new Cat("Samsung 24-inch monitor", "Samsung", "S24F350", "Monitors", 48, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 3.5, 3800, "monitor", "Full HD IPS, HDMI and VGA, scratches on the stand."),
        new Cat("LG 22-inch IPS monitor", "LG", "22MP48", "Monitors", 54, ItemCondition.FAIR, FunctionalStatus.FULLY_WORKING, 3.2, 3200, "monitor", "One faint pixel line, otherwise clean."),
        new Cat("Dell U2415 monitor", "Dell", "U2415", "Monitors", 60, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 4.8, 8600, "monitor", "24-inch 1920x1200, great colour accuracy."),
        new Cat("Sony Bravia 32-inch TV", "Sony", "KLV-32R412", "Monitors", 72, ItemCondition.FAIR, FunctionalStatus.PARTIALLY_WORKING, 6.0, 9000, "monitor", "Panel perfect, HDMI 2 port not working."),
        new Cat("Server mainboard (Xeon)", "Supermicro", "X10SRL-F", "Circuit boards", 80, ItemCondition.FAIR, FunctionalStatus.NOT_WORKING, 0.9, 7200, "board", "Decommissioned board, good for repair or metal recovery."),
        new Cat("Raspberry Pi 4 board lot", "Raspberry Pi", "Pi 4 B 4GB", "Circuit boards", 30, ItemCondition.LIKE_NEW, FunctionalStatus.FULLY_WORKING, 0.1, 9500, "board", "Five tested boards, no SD cards or cases."),
        new Cat("Canon LBP2900 printer", "Canon", "LBP2900", "Printers", 84, ItemCondition.FAIR, FunctionalStatus.FULLY_WORKING, 6.8, 2800, "box", "Prints cleanly, toner not included."),
        new Cat("HP LaserJet 1020", "HP", "LaserJet 1020", "Printers", 90, ItemCondition.FAIR, FunctionalStatus.FULLY_WORKING, 5.6, 3300, "box", "Compact mono laser, USB cable included."),
        new Cat("Laptop charger lot (10)", "Assorted", "65W adapters", "Chargers", 36, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 3.0, 1800, "box", "Tested adapters for Dell, HP and Lenovo."),
        new Cat("DDR4 RAM lot (20 sticks)", "Assorted", "DDR4 8GB", "Electronic components", 40, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 0.4, 4500, "board", "Pulled from working desktops, tested with memtest."),
        new Cat("SSD 256GB lot (10)", "Assorted", "2.5-inch SATA", "Electronic components", 30, ItemCondition.GOOD, FunctionalStatus.FULLY_WORKING, 0.5, 5200, "box", "Securely erased, health above 90%."),
        new Cat("Gaming console (no HDMI)", "Sony", "PS4 CUH-1200", "Other electronic waste", 72, ItemCondition.POOR, FunctionalStatus.PARTIALLY_WORKING, 2.8, 6000, "box", "HDMI port damaged, good for parts or repair."));
    private static final String[] CITIES = {"Ludhiana", "Patiala", "Chandigarh", "Amritsar"};
    private static final String[][] SELLERS = {{"seller@ecycle.local", "Asha Verma", "GreenLoop Traders"}, {"seller2@ecycle.local", "Rohan Mehta", "Mehta Electronics"},
            {"seller3@ecycle.local", "Meera Nair", "Nair IT Disposals"}, {"seller4@ecycle.local", "Kabir Singh", "Singh Refurb Hub"}};
    private static final String[][] BUYERS = {{"buyer@ecycle.local", "Neha Kapoor", "ReNew Labs", "REFURBISHER"}, {"buyer2@ecycle.local", "Vikram Rao", "Rao Repairs", "REPAIR_SHOP"},
            {"buyer3@ecycle.local", "Tara Joshi", "", "INDIVIDUAL"}, {"buyer4@ecycle.local", "Imran Sheikh", "", "INDIVIDUAL"}};
    private static final String[] CHAT_Q = {"Hi, is this still available?", "Does it come with the original charger?", "Can you share one more photo of the ports?", "Would you accept a little less?", "Can you ship this week?"};
    private static final String[] CHAT_A = {"Yes, it is available.", "Charger and box are included.", "Sure, uploading another photo now.", "I can come down slightly if you order today.", "Yes, I can ship within two days."};
    private static final String[] REVIEW_TEXT = {"Exactly as described, packed well.", "Fast shipping and honest listing.", "Good value, would buy again.", "Minor cosmetic wear, as stated.", "Smooth deal and quick replies."};
    private static final String[] DISPUTE_TEXT = {"The item does not match the listing photos.", "The screen arrived with a crack that was not in the listing.", "The wrong model was sent to me.",
            "I paid but the seller has not confirmed the shipment.", "The courier has not delivered after ten days.", "The seller stopped replying to my messages.", "The buyer is refusing to confirm delivery."};

    private void step(String what, Runnable r) { try { r.run(); } catch (RuntimeException e) { log.warn("Demo seed step skipped ({}): {}", what, e.getMessage()); } }
    private static AuthUser au(AppUser u, Role r) { return new AuthUser(u.getId(), u.getEmail(), Set.of(r)); }

    public AppUser seed(AppUser moderatorOrNull) {
        log.warn("SEED_DEMO enabled: creating demo accounts with well-known passwords. Never enable this in production.");
        AppUser resolverA = auth.createUser("resolver@ecycle.local", "Resolver@123", "Dr. Iyer", null, Role.RESOLVER);
        AppUser resolverB = auth.createUser("resolver2@ecycle.local", "Resolver@123", "Sana Qureshi", null, Role.RESOLVER);
        AppUser moderator = moderatorOrNull != null ? moderatorOrNull : resolverA;
        List<AppUser> su = new ArrayList<>(), bu = new ArrayList<>();
        for (int i = 0; i < SELLERS.length; i++) {
            AppUser u = auth.createUser(SELLERS[i][0], "Seller@123", SELLERS[i][1], "90000000" + (10 + i), Role.SELLER);
            SellerProfile sp = new SellerProfile(); sp.setUserId(u.getId()); sp.setBusinessName(SELLERS[i][2]); sp.setCity(CITIES[i]);
            sp.setVerificationStatus(VerificationStatus.VERIFIED); sp.setVerifiedAt(Instant.now()); sellers.save(sp); su.add(u);
        }
        for (int i = 0; i < BUYERS.length; i++) {
            AppUser u = auth.createUser(BUYERS[i][0], "Buyer@123", BUYERS[i][1], "90000000" + (20 + i), Role.BUYER);
            BuyerProfile bp = new BuyerProfile(); bp.setUserId(u.getId()); bp.setCity(CITIES[i]); bp.setBuyerType(BuyerType.valueOf(BUYERS[i][3]));
            bp.setOrganization(BUYERS[i][2].isEmpty() ? null : BUYERS[i][2]); bp.setVerified(true); bp.setRecoveryCapability(60 + i * 8); buyers.save(bp); bu.add(u);
        }
        AppUser recycler = auth.createUser("recycler@ecycle.local", "Buyer@123", "SafeCycle E-Waste", "9000000030", Role.BUYER);
        BuyerProfile rp = new BuyerProfile(); rp.setUserId(recycler.getId()); rp.setCity("Chandigarh"); rp.setBuyerType(BuyerType.RECYCLER); rp.setOrganization("SafeCycle E-Waste Pvt Ltd");
        rp.setVerified(true); rp.setRecoveryCapability(90); buyers.save(rp);

        Map<String, Long> cat = new HashMap<>(); categories.findAll().forEach(c -> cat.put(c.getName(), c.getId()));
        List<ListingDto> normal = new ArrayList<>();      // 60 ordinary listings: 20 stay for sale, 40 get orders
        for (int i = 0; i < 60; i++) {
            Cat c = CATALOG.get(i % CATALOG.size()); int v = i / CATALOG.size(); AppUser s = su.get(i % su.size());
            int idx = i;
            ListingDto d = create(s, c, v, CITIES[i % CITIES.length], cat, Map.of(), false, idx < 20 ? 2 : 1, i);
            if (d != null) { normal.add(d); step("approve", () -> listingService.approve(moderator.getId(), d.id())); }
        }
        List<ListingDto> hazardous = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Cat c = new Cat("Swollen laptop battery pack lot " + (i + 1), "Assorted", "Li-ion packs", "Batteries", 60, ItemCondition.SCRAP, FunctionalStatus.NOT_WORKING, 2.5, 1200 + i * 300, "battery", "Swollen lithium cells, specialist recycler only. Do not ship by courier.");
            ListingDto d = create(su.get(i), c, 0, CITIES[i], cat, Map.of("BATTERY_SWELLING", true), false, 1, 70 + i);
            if (d != null) { hazardous.add(d); step("approve", () -> listingService.approve(moderator.getId(), d.id())); }
        }
        List<ListingDto> auctionListings = new ArrayList<>();
        String[] boards = {"Server board lot", "Telecom PCB lot", "Desktop motherboard lot", "Mixed PCB lot"};
        for (int i = 0; i < boards.length; i++) {
            Cat c = new Cat(boards[i], "Assorted", "PCB lot", "Circuit boards", 100, ItemCondition.FAIR, FunctionalStatus.NOT_WORKING, 0.35, 10000 + i * 1500, "board", "Boards stripped from decommissioned equipment, good for precious-metal recovery.");
            ListingDto d = create(su.get(i), c, 0, CITIES[i], cat, Map.of(), true, 40, 80 + i);
            if (d != null) { auctionListings.add(d); step("approve", () -> listingService.approve(moderator.getId(), d.id())); }
        }
        log.info("Demo listings: {} normal, {} hazardous, {} auctions", normal.size(), hazardous.size(), auctionListings.size());

        Map<Long, AppUser> byId = new HashMap<>(); su.forEach(u -> byId.put(u.getId(), u)); bu.forEach(u -> byId.put(u.getId(), u));
        List<OrderDto> placed = new ArrayList<>();
        for (int n = 0; n < 40 && 20 + n < normal.size(); n++) {                       // orders: 5 paid, 5 shipped, 30 delivered
            ListingDto l = normal.get(20 + n); AppUser buyer = bu.get(n % bu.size()); AppUser seller = byId.get(l.sellerId());
            final int k = n;
            step("order " + n, () -> {
                OrderDto o = orders.buyNow(au(buyer, Role.BUYER), new OrderRequest(l.id(), "Flat " + (10 + k) + ", Sector " + (5 + k % 20) + ", " + CITIES[k % CITIES.length]));
                PaymentInitDto p = payments.initiate(au(buyer, Role.BUYER), o.id());
                payments.mockConfirm(au(buyer, Role.BUYER), p.paymentId());
                if (k >= 5) orders.updateShipment(au(seller, Role.SELLER), o.id(), new ShipmentRequest("BlueDart", "BD" + (710000 + k), ShipmentStatus.SHIPPED));
                if (k >= 10) orders.confirmDelivery(au(buyer, Role.BUYER), o.id());
                placed.add(o);
            });
        }
        log.info("Demo orders: {}", placed.size());

        for (int n = 20; n < placed.size() && n < 40; n++) {                           // 20 reviews on undisputed delivered orders
            OrderDto o = placed.get(n); final int k = n;
            step("review", () -> reviews.create(au(byId.get(o.buyerId()), Role.BUYER), new ReviewRequest(o.id(), 3 + k % 3, REVIEW_TEXT[k % REVIEW_TEXT.length])));
        }
        for (int d = 0; d < 20 && d < placed.size(); d++) seedDispute(d, placed.get(d), byId, resolverA, resolverB);

        List<OfferDto> madeOffers = new ArrayList<>();
        for (int i = 0; i < 20 && i < normal.size(); i++) {                            // 20 offers, 20 chat threads, 20 wishlist entries on the listings still for sale
            ListingDto l = normal.get(i); AppUser buyer = bu.get((i + 1) % bu.size()); AppUser seller = byId.get(l.sellerId()); final int k = i;
            if (buyer.getId().equals(seller.getId())) continue;
            step("offer", () -> madeOffers.add(offers.make(au(buyer, Role.BUYER), new OfferRequest(l.id(), l.expectedPrice().multiply(new BigDecimal("0.85")).setScale(0, RoundingMode.HALF_UP), "Can you do this price? I can pay today."))));
            step("chat", () -> {
                chat.send(au(buyer, Role.BUYER), new ChatRequest(l.id(), seller.getId(), CHAT_Q[k % CHAT_Q.length]));
                chat.send(au(seller, Role.SELLER), new ChatRequest(l.id(), buyer.getId(), CHAT_A[k % CHAT_A.length]));
                chat.send(au(buyer, Role.BUYER), new ChatRequest(l.id(), seller.getId(), "Thanks, I will confirm shortly."));
            });
            step("wishlist", () -> wishlist.add(au(bu.get(k % bu.size()), Role.BUYER), l.id()));
        }
        for (int i = 0; i < 2 && i < madeOffers.size(); i++) {                          // two accepted offers leave orders awaiting payment
            OfferDto of = madeOffers.get(i);
            step("accept offer", () -> offers.accept(au(byId.get(normal.stream().filter(x -> x.id().equals(of.listingId())).findFirst().get().sellerId()), Role.SELLER), of.id()));
        }
        int bids = 0;                                                                   // reverse auctions: 4 auctions x 5 bids
        List<AppUser> bidders = new ArrayList<>(bu); bidders.add(recycler);
        for (ListingDto a : auctionListings) {
            AuthUser probe = au(bidders.get(0), Role.BUYER);
            AuctionDto ad; try { ad = auctions.forListing(probe, a.id()); } catch (RuntimeException e) { log.warn("Auction lookup skipped: {}", e.getMessage()); continue; }
            for (int k = 0; k < 5; k++) {
                AppUser b = bidders.get(k % bidders.size()); final Long auctionId = ad.id(); final int kk = k; final BigDecimal price = a.expectedPrice().add(BigDecimal.valueOf(500L * (k + 1)));
                step("bid", () -> auctions.placeBid(au(b, Role.BUYER), auctionId, new BidRequest(price, kk % 2 == 0 ? Pathway.RECYCLE : Pathway.REFURBISH, "Certified dismantling with component recovery and a recycling certificate.")));
                bids++;
            }
        }
        log.info("Demo data ready. Logins: seller@ / seller2@ / buyer@ / buyer2@ / recycler@ / resolver@ / resolver2@ ecycle.local (see README for passwords). Bids attempted: {}", bids);
        return moderator;
    }

    private ListingDto create(AppUser seller, Cat c, int variant, String city, Map<String, Long> cat, Map<String, Boolean> hazard, boolean auction, int qty, int imgSeed) {
        try {
            BigDecimal price = BigDecimal.valueOf(Math.max(500, Math.round(c.price() * (1 - 0.05 * variant) / 50.0) * 50));
            String name = variant == 0 ? c.name() : c.name() + " (batch " + (variant + 1) + ")";
            ListingDto d = listingService.create(au(seller, Role.SELLER), new ListingRequest(name, c.brand(), c.model(), cat.get(c.category()), c.months(), c.cond(), c.func(), qty, price, c.blurb(),
                    city, null, BigDecimal.valueOf(c.kg()), auction ? Pathway.RECYCLE : null, hazard, auction, auction ? 72 : null, auction ? price.multiply(new BigDecimal("0.8")).setScale(0, RoundingMode.HALF_UP) : null));
            image(d.id(), c.kind(), name, imgSeed, false); if (qty > 1 || imgSeed % 3 == 0) image(d.id(), c.kind(), name + " (back)", imgSeed + 5, false);
            return d;
        } catch (RuntimeException e) { log.warn("Demo listing skipped ({}): {}", c.name(), e.getMessage()); return null; }
    }

    private void image(Long listingId, String kind, String label, int seed, boolean cracked) {
        byte[] png = DemoImages.device(kind, label, seed, cracked);
        var st = storage.storeBytes(png, "listings/" + listingId, "image/png", label + ".png");
        ListingImage li = new ListingImage(); li.setListingId(listingId); li.setFilePath(st.path()); li.setOriginalName(st.originalName()); li.setContentType(st.contentType()); li.setSha256(st.sha256());
        listingImages.save(li);
    }

    private void seedDispute(int d, OrderDto o, Map<Long, AppUser> byId, AppUser resolverA, AppUser resolverB) {
        AppUser buyer = byId.get(o.buyerId()), seller = byId.get(o.sellerId()), resolver = d % 2 == 0 ? resolverA : resolverB;
        AuthUser b = au(buyer, Role.BUYER), s = au(seller, Role.SELLER), r = au(resolver, Role.RESOLVER);
        step("dispute " + d, () -> {
            DisputeCategory cat = DisputeCategory.values()[d % DisputeCategory.values().length];
            DisputeDto dd = disputes.create(b, new DisputeRequest(o.id(), cat, DISPUTE_TEXT[d % DISPUTE_TEXT.length]));
            Long id = dd.id();
            byte[] png = DemoImages.device(d % 2 == 0 ? "phone" : "laptop", "Evidence photo " + (d + 1), d + 9, true);
            var st = storage.storeBytes(png, "disputes/" + id, "image/png", "evidence-" + (d + 1) + ".png");
            DisputeEvidence ev = new DisputeEvidence(); ev.setDisputeId(id); ev.setSubmittedBy(buyer.getId()); ev.setCategory(EvidenceCategory.PHOTO); ev.setDescription("Photo taken on arrival");
            ev.setFilePath(st.path()); ev.setOriginalName(st.originalName()); ev.setContentType(st.contentType()); ev.setSha256(st.sha256()); evidenceRepo.save(ev);
            disputes.post(s, id, new MessageRequest(null, "I shipped exactly what was listed. Happy to help sort this out."));
            if (d < 4) return;                                                         // 4 stay OPEN
            disputes.assign(r, id, null);
            if (d < 8) { disputes.post(r, id, new MessageRequest(MessageVisibility.PARTIES, "I have taken this case and am reviewing the evidence.")); return; }
            if (d < 11) { disputes.post(r, id, new MessageRequest(MessageVisibility.EVIDENCE_REQUEST, "Please upload the packaging photo and the courier receipt.")); return; }
            if (d == 19) { disputes.reject(r, id, "No evidence supports the claim; the order stands."); return; }
            ResolutionType[] types = {ResolutionType.FULL_REFUND, ResolutionType.PARTIAL_REFUND, ResolutionType.RESOLVED_SELLER_FAVOR, ResolutionType.MUTUAL_SETTLEMENT, ResolutionType.REPLACEMENT, ResolutionType.RETURN_AND_REFUND, ResolutionType.RESOLVED_BUYER_FAVOR, ResolutionType.FULL_REFUND};
            ResolutionType t = types[(d - 11) % types.length];
            BigDecimal amt = t == ResolutionType.PARTIAL_REFUND ? o.amount().multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.HALF_UP) : null;
            disputes.resolve(r, id, new ResolutionRequest(t, amt, "After reviewing the photos and messages from both parties, the outcome is " + t.name().toLowerCase().replace('_', ' ') + "."));
        });
    }
}
