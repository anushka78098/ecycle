package com.ecycle.frontend.navigation;

import com.ecycle.frontend.view.*;
import java.util.List;
import java.util.function.Supplier;
import javafx.scene.Node;

/** Sidebar structure per role. A null view marks a section heading. Each supplier builds a fresh view so data is always reloaded. */
public final class Menus {
    private Menus() {}
    public record Entry(String label, Supplier<Node> view) {}
    private static Entry sec(String l) { return new Entry(l, null); }
    private static Entry e(String l, Supplier<Node> v) { return new Entry(l, v); }

    public static List<Entry> forRole(String role) {
        return switch (role == null ? "" : role) {
            case "SELLER" -> List.of(e("Dashboard", () -> new DashboardView("SELLER")), sec("Selling"), e("My listings", SellerViews::listings), e("Offers received", SellerViews::offers),
                    e("Reverse auctions", () -> AuctionViews.mine()), e("Orders & shipping", () -> OrderViews.orders("seller", false)), e("Sales history", () -> OrderViews.orders("seller", true)),
                    sec("Circular tools"), e("Digital passports", PassportViews::mine), e("Hazard & data safety", SellerViews::hazard), e("Environmental impact", () -> new ImpactView("seller")),
                    sec("Support"), e("Messages", MiscViews::messages), e("Eco-Assistant", AssistantView::new), e("Disputes", DisputeViews::mine), e("Reviews of me", MiscViews::myReviews));
            case "BUYER" -> List.of(e("Marketplace", () -> new MarketplaceView("")), e("Recommended for you", MiscViews::recommendations), e("Dashboard", () -> new DashboardView("BUYER")),
                    sec("Buying"), e("Wishlist", MiscViews::wishlist), e("My offers", MiscViews::myOffers),
                    e("Reverse auctions", () -> AuctionViews.browse()), e("My orders", () -> OrderViews.orders("buyer", false)), e("Transaction history", () -> OrderViews.orders("buyer", true)),
                    sec("Circular tools"), e("Passports & components", PassportViews::mine), e("Environmental impact", () -> new ImpactView("buyer")),
                    sec("Support"), e("Messages", MiscViews::messages), e("Eco-Assistant", AssistantView::new), e("Disputes", DisputeViews::mine));
            case "RESOLVER" -> List.of(e("Dashboard", () -> new DashboardView("RESOLVER")), sec("Disputes"), e("Dispute centre", () -> DisputeViews.centre(false)), e("Resolution history", DisputeViews::history),
                    sec("Moderation"), e("Listing approval", AdminViews::pendingListings), e("All listings", AdminViews::allListings), e("Users", AdminViews::users), e("Seller & buyer verification", AdminViews::verification),
                    e("Sanitization verification", AdminViews::sanitization), e("Transactions", AdminViews::orders),
                    sec("Help"), e("Eco-Assistant", AssistantView::new));
            default -> List.of();
        };
    }
}
