package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
