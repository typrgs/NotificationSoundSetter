package com.typrgs.NotificationSoundSetter.Services.NotificationListener;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupedPostTrackerTest {
    private final GroupedPostTracker tracker = new GroupedPostTracker();
    private final List<String> first = Collections.singletonList("First");
    private final List<String> second = Collections.singletonList("Second");

    @Test public void formingStackSuppressesOnlyRepostedChild() {
        assertFalse(tracker.record("A", null, first, false));
        assertFalse(tracker.record("B", "chats", second, false));
        assertTrue(tracker.record("A", "chats", first, false));
        assertFalse(tracker.record("A", "chats", second, false));
    }

    @Test public void equalTextInExistingGroupNeedsMessageIdentityToSuppress() {
        assertFalse(tracker.record("A", "chats", first, false));
        assertFalse(tracker.record("A", "chats", first, false));

        List<String> message = List.of("OK", "1000");
        assertFalse(tracker.record("A", "chats", message, true));
        assertTrue(tracker.record("A", "chats", message, true));
        assertFalse(tracker.record("A", "chats", List.of("OK", "1001"), true));
    }

    @Test public void reusedKeysAndEmptyNotificationsAreEligible() {
        tracker.record("A", null, first, false);
        tracker.remove("A");
        assertFalse(tracker.record("A", "chats", first, false));
        tracker.record("B", null, Collections.emptyList(), false);
        assertFalse(tracker.record("B", "chats", Collections.emptyList(), false));
    }
}
