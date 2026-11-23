package com.ecycle.backend.assistant;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.shared.Dtos.PendingActionDto;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.*;

/**
 * LangChain4j function-calling tools, created per request and bound to the authenticated user. The model never supplies a user id, so it cannot act for anyone else; actions that change data
 * only create a proposal that the user must confirm in the UI. Errors are returned as text so the model can explain them instead of crashing the conversation.
 */
public class EcoTools {
    private final AssistantActions actions; private final AuthUser me; private final String conversationId;
    private final Set<String> used = new LinkedHashSet<>(); private final List<PendingActionDto> proposals = new ArrayList<>();

    public EcoTools(AssistantActions actions, AuthUser me, String conversationId) { this.actions = actions; this.me = me; this.conversationId = conversationId; }
    public List<String> toolsUsed() { return new ArrayList<>(used); }
    public List<PendingActionDto> proposals() { return proposals; }

    private String run(String tool, java.util.function.Supplier<String> body) {
        used.add(tool);
        try { return body.get(); }
        catch (ApiException e) { return "Not possible: " + e.getMessage(); }
        catch (RuntimeException e) { return "The lookup failed; tell the user something went wrong and to try again."; }
    }

    @Tool("Get the signed-in user's eco-points balance, level, leaderboard rank, badges and carbon/e-waste totals.")
    public String getMyEcoPoints() { return run("getMyEcoPoints", () -> actions.ecoSummary(me)); }

    @Tool("List the signed-in user's pickup bookings with their current status.")
    public String listMyPickups() { return run("listMyPickups", () -> actions.pickupsSummary(me)); }

    @Tool("Get the status of one pickup by its tracking code (format PK-XXXXXXX) that belongs to the signed-in user.")
    public String getPickupStatus(@P("Pickup tracking code, e.g. PK-ABC2345") String trackingCode) { return run("getPickupStatus", () -> actions.pickupStatus(me, trackingCode)); }

    @Tool("Find active recycler facilities, optionally filtered by city and accepted category. Returns facility ids needed for booking.")
    public String findRecyclers(@P(value = "City name, or empty for all", required = false) String city, @P(value = "Category such as Laptops or Batteries, or empty", required = false) String category) {
        return run("findRecyclers", () -> actions.findRecyclers(city, category));
    }

    @Tool("List rewards the signed-in user can redeem with eco-points.")
    public String listRewards() { return run("listRewards", () -> actions.rewards(me)); }

    @Tool("List the signed-in user's recycling / data-destruction / data-sanitization certificates.")
    public String listMyCertificates() { return run("listMyCertificates", () -> actions.myCertificates(me)); }

    @Tool("Estimate e-waste diverted, carbon avoided and eco-points for a weight of devices. Estimates only.")
    public String estimateImpact(@P("Total weight in kilograms") double weightKg, @P(value = "Number of items", required = false) Integer quantity,
                                 @P(value = "REUSE, REPAIR, REFURBISH or RECYCLE (default RECYCLE)", required = false) String pathway, @P(value = "Product category id if known", required = false) Long categoryId) {
        return run("estimateImpact", () -> actions.estimate(categoryId, weightKg, quantity == null ? 1 : quantity, pathway));
    }

    @Tool("PROPOSE booking an e-waste pickup with a recycler. This does NOT book anything; the user must press Confirm in the app. Ask the user for any missing detail first (facility, items, weight, date).")
    public String proposeBookPickup(@P("Facility id from findRecyclers") long locationId, @P("What is being collected") String items, @P("Estimated total weight in kg") double weightKg,
                                    @P("Number of items") int quantity, @P("Preferred date, e.g. 2026-12-01 or 2026-12-01T10:00") String preferredDate,
                                    @P("True if the devices store personal data (phones, laptops, drives)") boolean dataBearing, @P(value = "Pickup address", required = false) String address) {
        return run("proposeBookPickup", () -> { PendingActionDto p = actions.proposeBook(me, conversationId, locationId, items, weightKg, quantity, preferredDate, dataBearing, address); proposals.add(p); return "Proposal created: " + p.summary() + ". Tell the user to press Confirm below to book it; nothing is booked yet."; });
    }

    @Tool("PROPOSE cancelling one of the user's pickups. Nothing is cancelled until the user presses Confirm in the app.")
    public String proposeCancelPickup(@P("Pickup tracking code") String trackingCode) {
        return run("proposeCancelPickup", () -> { PendingActionDto p = actions.proposeCancel(me, conversationId, trackingCode); proposals.add(p); return "Proposal created: " + p.summary() + ". Tell the user to press Confirm below; nothing is cancelled yet."; });
    }

    @Tool("PROPOSE redeeming a reward with eco-points. Nothing is spent until the user presses Confirm in the app.")
    public String proposeRedeemReward(@P("Reward id from listRewards") long rewardId) {
        return run("proposeRedeemReward", () -> { PendingActionDto p = actions.proposeRedeem(me, conversationId, rewardId); proposals.add(p); return "Proposal created: " + p.summary() + ". Tell the user to press Confirm below; no points are spent yet."; });
    }
}
