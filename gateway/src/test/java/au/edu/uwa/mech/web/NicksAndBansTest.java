package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import au.edu.uwa.mech.web.cloud.MemoryParamStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class NicksAndBansTest {

    @Test
    void duplicateNicksGetSuffixesAndFreedOnesComeBack() {
        Nicks n = new Nicks();
        assertEquals("ann", n.reserve("ann"));
        assertEquals("ann~2", n.reserve("ann"));
        assertEquals("ANN~3", n.reserve("ANN"), "case does not make a nick different");
        n.release("ann~2");
        assertEquals("ann~2", n.reserve("ann"));
        assertTrue(n.held("Ann"));
        n.release("ann");
        assertFalse(n.held("ann"));
    }

    @Test
    void reservedNicksIgnoreCaseSpacesDotsUnderscoresAndDashes() {
        for (String n : List.of("admin", "Administrator", "BARNEY", "robot", "camera", "team", "guest", "registered",
            "system", "moderator", "Ad.Min", "ro_bot", "sys tem", "-camera-", "M.O.D.E.R.A.T.O.R", " admin ")) {
            assertTrue(Nicks.reserved(n), n);
        }
        for (String n : List.of("admin2", "robots", "barney d", "alice", "team~2x", "")) {
            assertFalse(Nicks.reserved(n), n);
        }
        assertFalse(Nicks.reserved(null));
    }

    @Test
    void chatIdsAreTheSameInEverySession() {
        assertEquals(Session.chatId("ann", "hi", 1000), Session.chatId("ann", "hi", 1000));
        assertTrue(Session.chatId("ann", "hi", 1000).matches("1000-[0-9a-z]{4}"));
    }

    @Test
    void bansCoverSubAndIpHashAndPersist() {
        MemoryParamStore store = new MemoryParamStore();
        Bans bans = new Bans(store);
        Identity ann = new Identity("guest:abc", Identity.Level.GUEST, "abc");
        assertFalse(bans.banned(ann));
        bans.ban(ann);
        assertTrue(bans.banned(ann));
        assertTrue(bans.banned(new Identity("someone-else", Identity.Level.REGISTERED, "abc")), "same address");
        assertTrue(bans.banned(new Identity("guest:abc", Identity.Level.GUEST, "other")), "same subject");
        assertFalse(bans.banned(new Identity("x", Identity.Level.GUEST, "y")));

        Bans reloaded = new Bans(store);
        assertTrue(reloaded.banned(ann));
        assertEquals(List.of("abc", "guest:abc"), List.copyOf(new java.util.TreeSet<>(reloaded.all())));

        store.put(Bans.PARAM, "not json");
        assertFalse(new Bans(store).banned(ann), "an unreadable list is ignored, not fatal");
    }

    @Test
    void anUnreadableStoredListIsNeverOverwritten() {
        MemoryParamStore store = new MemoryParamStore();
        store.put(Bans.PARAM, "[\"kept-ban\", oops");
        Bans bans = new Bans(store);
        Identity bob = new Identity("bob", Identity.Level.REGISTERED, "h1");
        bans.ban(bob);
        assertTrue(bans.banned(bob), "the ban holds in memory");
        assertEquals("[\"kept-ban\", oops", store.get(Bans.PARAM).orElseThrow(), "the stored list is left for a human");
    }
}
