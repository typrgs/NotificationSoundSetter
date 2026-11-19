package com.typrgs.NotificationSoundSetter.Services.NotificationListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Remembers each active child's last post so moving it into a group cannot replay its sound. */
final class GroupedPostTracker {
    private static final class Post {
        final String group;
        final List<String> content;

        Post(String group, List<String> content) {
            this.group = group;
            this.content = content;
        }
    }

    private final Map<String, Post> active = new HashMap<>();

    boolean record(String key, String group, List<String> content, boolean hasMessageTimestamp) {
        Post previous = active.put(key, new Post(group, content));
        boolean duplicate = group != null && previous != null && !content.isEmpty()
                && content.equals(previous.content)
                && (!Objects.equals(group, previous.group) || hasMessageTimestamp);
        return duplicate;
    }

    void remove(String key) {
        active.remove(key);
    }

    void clear() {
        active.clear();
    }
}
