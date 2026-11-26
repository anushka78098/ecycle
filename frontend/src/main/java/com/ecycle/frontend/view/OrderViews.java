package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.Session;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.component.FormDialog.Opt;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.ecycle.shared.Enums;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.List;
import java.util.Set;

/** Orders, shipping, payment (mock sandbox or Razorpay), receipts, cancellation, disputes and reviews. */
public final class OrderViews {
    private OrderViews() {}
    private static final Set<String> HISTORY = Set.of("DELIVERED", "REFUNDED", "CANCELLED");

    public static ResourceView orders(String as, boolean history) {
        boolean seller = "seller".equals(as);
        ResourceView[] ref = new ResourceView[1];
        ref[0] = new ResourceView(history ? (seller ? "Sales history" : "Transaction history") : (seller ? "Orders & shipping" : "My orders"),
                seller ? "Fulfil paid orders. Hazardous items can only ship via SPECIALIST_PICKUP." : "Pay, track delivery, confirm receipt, review or raise a dispute", () -> {
            JsonNode all = ApiClient.get("/api/orders/mine?as=" + as); ArrayNode out = ApiClient.mapper().createArrayNode();
            for (JsonNode o : all) if (HISTORY.contains(J.s(o, "status")) == history) out.add(o);
            return out;
        });
        ResourceView v = ref[0];
        v.col("#", "id").col("Product", "listingName").col(seller ? "Buyer" : "Seller", seller ? "buyerName" : "sellerName").money("Amount", "amount").badge("Order", "status").badge("Payment", "paymentStatus")
                .badge("Shipment", "shipmentStatus").col("Tracking", n -> (J.s(n, "carrier") + " " + J.s(n, "trackingNo")).trim()).date("Created", "createdAt");
        if (seller) {
            v.action("Update shipment", n -> Set.of("PAID", "SHIPPED").contains(J.s(n, "status")), n -> shipment(n, v));
        } else {
            v.action("Set address", n -> "PENDING_PAYMENT".equals(J.s(n, "status")), n -> FormDialog.show("Delivery address", "Save", List.of(FormDialog.area("shippingAddress", "Full address", true)),
                    b -> () -> ApiClient.put("/api/orders/" + J.l(n, "id") + "/address", b), r -> v.reload()));
            v.action("Pay", "btn-primary", n -> "PENDING_PAYMENT".equals(J.s(n, "status")), n -> pay(n, v::reload));
            v.action("Confirm delivery", n -> "SHIPPED".equals(J.s(n, "status")), n -> Alerts.confirm("Confirm that you received this order? Ownership of the passport transfers to you.",
                    () -> Async.run(() -> ApiClient.post("/api/orders/" + J.l(n, "id") + "/confirm-delivery", null), r -> v.reload())));
        }
        v.action("Cancel", "btn-danger", n -> Set.of("PENDING_PAYMENT", "PAID").contains(J.s(n, "status")), n -> FormDialog.show("Cancel order #" + J.l(n, "id"), "Cancel order",
                List.of(FormDialog.text("reason", "Reason", true)), b -> () -> ApiClient.post("/api/orders/" + J.l(n, "id") + "/cancel", b), r -> v.reload()));
        v.action("Receipt", n -> !J.s(n, "receiptNo").isBlank(), OrderViews::receipt);
        v.action("Raise dispute", "btn-danger", n -> Set.of("PAID", "SHIPPED", "DELIVERED").contains(J.s(n, "status")), n -> FormDialog.show("Raise a dispute for order #" + J.l(n, "id"), "Submit dispute",
                List.of(FormDialog.combo("category", "Category", true, FormDialog.enumOpts(Enums.DisputeCategory.class, false), "PRODUCT_NOT_AS_DESCRIBED"), FormDialog.area("description", "Describe the problem", true)),
                b -> () -> { b.put("orderId", J.l(n, "id")); return ApiClient.post("/api/disputes", b); },
                r -> { Alerts.info("Dispute #" + r.path("id").asLong() + " opened. Upload evidence from Disputes."); v.reload(); }));
        v.action("Review", n -> "DELIVERED".equals(J.s(n, "status")), n -> FormDialog.show("Review the " + (seller ? "buyer" : "seller"), "Submit review",
                List.of(FormDialog.combo("rating", "Rating", true, List.of(new Opt("5", "5 - Excellent"), new Opt("4", "4 - Good"), new Opt("3", "3 - OK"), new Opt("2", "2 - Poor"), new Opt("1", "1 - Terrible")), "5"), FormDialog.area("comment", "Comment", false)),
                b -> () -> { b.put("orderId", J.l(n, "id")); b.put("rating", b.get("rating").asInt()); return ApiClient.post("/api/reviews", b); }, r -> Alerts.info("Thanks, your review was saved.")));
        v.action("Passport", n -> true, n -> Async.run(() -> ApiClient.get("/api/listings/" + J.l(n, "listingId") + "/passport"), p -> PassportViews.open(J.s(p, "code"))));
        return v;
    }

    private static void shipment(JsonNode n, ResourceView v) {
        String hint = J.b(n, "shippingBlocked") ? "SPECIALIST_PICKUP" : "";
        FormDialog.show("Shipment for order #" + J.l(n, "id"), "Update", List.of(
                FormDialog.text("carrier", J.b(n, "shippingBlocked") ? "Carrier (hazardous: must be SPECIALIST_PICKUP)" : "Carrier", true, hint), FormDialog.text("trackingNo", "Tracking number", false),
                FormDialog.combo("status", "Status", true, FormDialog.opts("PENDING", "SHIPPED", "IN_TRANSIT"), "SHIPPED")),
                b -> () -> ApiClient.put("/api/orders/" + J.l(n, "id") + "/shipment", b), r -> v.reload());
    }

    private static void receipt(JsonNode n) {
        Async.run(() -> ApiClient.get("/api/orders/" + J.l(n, "id") + "/receipt"), r -> SwingPrint.preview("Receipt " + J.s(r, "receiptNo"), "E-CYCLE PAYMENT RECEIPT", null,
                "Receipt: " + J.s(r, "receiptNo"), "Order #" + J.l(r, "orderId") + " - " + J.s(r, "listingName"), "Buyer: " + J.s(r, "buyerName"), "Seller: " + J.s(r, "sellerName"),
                "Amount: " + J.money(r, "amount"), "Provider: " + J.s(r, "provider") + " / " + J.s(r, "providerPaymentId"), "Verified by server: " + J.date(r, "verifiedAt")));
    }

    /** Starts payment. Sandbox: simulated checkout confirmed by the backend. Razorpay: hosted checkout; the backend verifies the signature either way. */
    public static void pay(JsonNode order, Runnable after) {
        Async.run(() -> ApiClient.post("/api/payments/orders/" + J.l(order, "id") + "/init", null), init -> {
            long pid = init.path("paymentId").asLong();
            if ("MOCK_SANDBOX".equals(J.s(init, "provider"))) {
                Alerts.confirm("SANDBOX payment of " + J.money(init, "amount") + ".\nNo real money moves. The backend will still run its full signature verification.\n\nSimulate a successful payment?",
                        () -> Async.run(() -> ApiClient.post("/api/payments/" + pid + "/mock-confirm", null), r -> { Alerts.info("Payment verified by the server. Receipt " + J.s(r, "receiptNo")); after.run(); }));
            } else {
                RazorpayCheckout.open(init, (payId, sig) -> {
                    var b = J.obj(); b.put("providerPaymentId", payId); b.put("signature", sig);
                    Async.run(() -> ApiClient.post("/api/payments/" + pid + "/verify", b), r -> { Alerts.info("Payment verified. Receipt " + J.s(r, "receiptNo")); after.run(); });
                });
            }
        });
    }
}
