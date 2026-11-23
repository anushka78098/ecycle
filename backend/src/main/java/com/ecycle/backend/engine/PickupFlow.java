package com.ecycle.backend.engine;

import com.ecycle.shared.Enums.PickupStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pickup status state machine: which actor may move a pickup from one status to another. */
public final class PickupFlow {
    private PickupFlow() {}
    public enum Actor { REQUESTER, RECYCLER, ADMIN }
    private record Rule(Set<Actor> actors, PickupStatus to) {}
    private static final Map<PickupStatus, List<Rule>> RULES = new EnumMap<>(PickupStatus.class);
    static {
        RULES.put(PickupStatus.REQUESTED, List.of(new Rule(EnumSet.of(Actor.RECYCLER), PickupStatus.ACCEPTED), new Rule(EnumSet.of(Actor.RECYCLER), PickupStatus.REJECTED),
                new Rule(EnumSet.of(Actor.REQUESTER, Actor.ADMIN), PickupStatus.CANCELLED)));
        RULES.put(PickupStatus.ACCEPTED, List.of(new Rule(EnumSet.of(Actor.RECYCLER), PickupStatus.EN_ROUTE), new Rule(EnumSet.of(Actor.REQUESTER, Actor.RECYCLER, Actor.ADMIN), PickupStatus.CANCELLED)));
        RULES.put(PickupStatus.EN_ROUTE, List.of(new Rule(EnumSet.of(Actor.RECYCLER), PickupStatus.PICKED_UP), new Rule(EnumSet.of(Actor.REQUESTER, Actor.RECYCLER, Actor.ADMIN), PickupStatus.CANCELLED)));
        RULES.put(PickupStatus.PICKED_UP, List.of(new Rule(EnumSet.of(Actor.RECYCLER), PickupStatus.AT_FACILITY), new Rule(EnumSet.of(Actor.ADMIN), PickupStatus.CANCELLED)));
        RULES.put(PickupStatus.AT_FACILITY, List.of(new Rule(EnumSet.of(Actor.RECYCLER), PickupStatus.COMPLETED)));
    }
    public static boolean terminal(PickupStatus s) { return s == PickupStatus.COMPLETED || s == PickupStatus.REJECTED || s == PickupStatus.CANCELLED; }
    public static boolean canTransition(PickupStatus from, PickupStatus to, Actor actor) {
        return RULES.getOrDefault(from, List.of()).stream().anyMatch(r -> r.to() == to && r.actors().contains(actor));
    }
    public static List<PickupStatus> allowedNext(PickupStatus from, Actor actor) {
        return RULES.getOrDefault(from, List.of()).stream().filter(r -> r.actors().contains(actor)).map(Rule::to).toList();
    }
    /** Statuses in which the recycler is physically responsible for the device (custody has passed to them). */
    public static boolean recyclerHoldsCustody(PickupStatus s) { return s == PickupStatus.PICKED_UP || s == PickupStatus.AT_FACILITY || s == PickupStatus.COMPLETED; }
}
