package com.ecycle.backend.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.shared.Enums.Role;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleBasedAssistantTest {
    private final AssistantActions actions = mock(AssistantActions.class);
    private final AuthUser me = new AuthUser(5L, "a@b.c", Set.of(Role.SELLER));
    private final EcoTools tools = new EcoTools(actions, me, "conv1");
    private final RuleBasedAssistant bot = new RuleBasedAssistant(actions, me, tools);

    @Test void ecoPointsQuestionUsesTheTool() {
        when(actions.ecoSummary(me)).thenReturn("Balance 120 eco-points");
        assertEquals("Balance 120 eco-points", bot.answer("How many eco points do I have?")); assertEquals(java.util.List.of("getMyEcoPoints"), tools.toolsUsed());
    }
    @Test void bookingNeverExecutes() {
        when(actions.proposeBook(eq(me), eq("conv1"), eq(3L), eq("old laptop"), eq(2.5), eq(1), eq("2027-01-10"), eq(true), isNull())).thenReturn(new com.ecycle.shared.Dtos.PendingActionDto("a1", com.ecycle.shared.Enums.PendingActionType.BOOK_PICKUP, "Book", Instant.now(), "PENDING"));
        String r = bot.answer("book a pickup at recycler #3 for \"old laptop\" 2.5 kg on 2027-01-10");
        assertTrue(r.contains("nothing is booked yet")); assertEquals(1, tools.proposals().size()); verify(actions, never()).confirm(any(), any());
    }
    @Test void cancelRequiresTrackingCodeAndOnlyProposes() {
        when(actions.proposeCancel(me, "conv1", "PK-ABC2345")).thenReturn(new com.ecycle.shared.Dtos.PendingActionDto("a2", com.ecycle.shared.Enums.PendingActionType.CANCEL_PICKUP, "Cancel", Instant.now(), "PENDING"));
        assertTrue(bot.answer("please cancel PK-ABC2345").contains("nothing is cancelled yet"));
    }
    @Test void toolErrorsAreReturnedAsTextNotThrown() {
        when(actions.pickupsSummary(me)).thenThrow(ApiException.forbidden("nope"));
        assertTrue(bot.answer("show my pickups").startsWith("Not possible: nope"));
    }
    @Test void generalKnowledgeFallsBackToHelp() {
        assertTrue(bot.answer("what about batteries?").toLowerCase().contains("lithium")); assertTrue(bot.answer("zzz").contains("eco-points"));
    }
    @Test void pickupDateParsing() {
        assertThrows(ApiException.class, () -> AssistantActions.parseWhen("2001-01-01")); assertThrows(ApiException.class, () -> AssistantActions.parseWhen("next week")); assertThrows(ApiException.class, () -> AssistantActions.parseWhen(null));
        assertTrue(AssistantActions.parseWhen("tomorrow").isAfter(Instant.now()));
    }
}
