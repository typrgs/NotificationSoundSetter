package com.typrgs.NotificationSoundSetter;

import com.typrgs.NotificationSoundSetter.Models.SoundMapping;
import com.typrgs.NotificationSoundSetter.Services.SoundMappingManager.SoundMappingManager;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class SoundMappingTest {
    private SoundMapping mapping() {
        SoundMapping mapping = new SoundMapping();
        mapping.packageName = "com.example.chat";
        mapping.titleKeywords.addAll(Arrays.asList("Alex", "Sam"));
        mapping.bodyKeywords.addAll(Arrays.asList("urgent", "call me"));
        return mapping;
    }

    @Test public void anyTitleKeywordMatchesIgnoringCase() {
        assertTrue(SoundMappingManager.matches(mapping(), "com.example.chat", "Message from SAM", "Hello"));
        assertTrue(SoundMappingManager.matches(mapping(), "com.example.chat", "ALEX replied", null));
    }

    @Test public void anyBodyKeywordMatchesWithoutTitleMatch() {
        assertTrue(SoundMappingManager.matches(mapping(), "com.example.chat", "Other person", "An URGENT update"));
        assertTrue(SoundMappingManager.matches(mapping(), "com.example.chat", null, "Please CALL ME today"));
    }

    @Test public void fieldsAreMatchedAgainstTheirOwnLists() {
        assertFalse(SoundMappingManager.matches(mapping(), "com.example.chat", "urgent", "Alex"));
        assertFalse(SoundMappingManager.matches(mapping(), "com.example.chat", null, null));
    }

    @Test public void bodyOnlyAndTitleOnlyRulesAreAllowed() {
        SoundMapping mapping = mapping();
        mapping.titleKeywords.clear();
        assertTrue(SoundMappingManager.matches(mapping, "com.example.chat", null, "urgent"));
        mapping.bodyKeywords.clear();
        assertFalse(SoundMappingManager.matches(mapping, "com.example.chat", "Alex", "urgent"));
        mapping.titleKeywords.add("Alex");
        assertTrue(SoundMappingManager.matches(mapping, "com.example.chat", "Alex", null));
    }

    @Test public void anotherAppAndDisabledRuleDoNotMatch() {
        SoundMapping mapping = mapping();
        assertFalse(SoundMappingManager.matches(mapping, "com.other.chat", "Alex", "urgent"));
        mapping.enabled = false;
        assertFalse(SoundMappingManager.matches(mapping, "com.example.chat", "Alex", "urgent"));
    }

    @Test public void emptyKeywordsNeverMatchEverything() {
        SoundMapping mapping = mapping();
        mapping.titleKeywords = Arrays.asList("", "  ");
        mapping.bodyKeywords.clear();
        assertFalse(SoundMappingManager.matches(mapping, "com.example.chat", "Anything", ""));
    }

    @Test public void fallbackMatchesEvenWithoutNotificationText() {
        SoundMapping fallback = mapping();
        fallback.anyNotification = true;
        assertTrue(SoundMappingManager.matches(fallback, "com.example.chat", null, null));
        assertFalse(SoundMappingManager.matches(fallback, "com.other.chat", null, null));
    }

    @Test public void keywordRulesWinEvenWhenFallbackIsFirst() {
        SoundMapping fallback = mapping();
        fallback.anyNotification = true;
        SoundMapping keyword = mapping();
        assertSame(keyword, SoundMappingManager.findMatch(Arrays.asList(fallback, keyword),
                "com.example.chat", "Alex", null));
        assertSame(fallback, SoundMappingManager.findMatch(Arrays.asList(keyword, fallback),
                "com.example.chat", "Other person", "Hello"));
    }

    @Test public void firstMatchingKeywordRuleWins() {
        SoundMapping first = mapping();
        assertSame(first, SoundMappingManager.findMatch(Arrays.asList(first, mapping()),
                "com.example.chat", "Sam", "urgent"));
    }

    @Test public void disabledRulesAreSkippedIncludingFallbacks() {
        SoundMapping keyword = mapping();
        keyword.enabled = false;
        SoundMapping fallback = mapping();
        fallback.anyNotification = true;
        assertSame(fallback, SoundMappingManager.findMatch(Arrays.asList(keyword, fallback),
                "com.example.chat", "Alex", null));
        fallback.enabled = false;
        assertNull(SoundMappingManager.findMatch(Arrays.asList(keyword, fallback),
                "com.example.chat", "Alex", null));
    }

    @Test public void duplicateFallbacksAreRejectedEvenIfDisabled() {
        SoundMapping first = mapping();
        first.anyNotification = true;
        SoundMapping second = mapping();
        second.anyNotification = true;
        second.enabled = false;
        assertTrue(SoundMappingManager.hasDuplicateFallback(Arrays.asList(first, second)));
        second.packageName = "com.other.chat";
        assertFalse(SoundMappingManager.hasDuplicateFallback(Arrays.asList(first, second)));
        assertFalse(SoundMappingManager.hasDuplicateFallback(Collections.singletonList(first)));
        second.packageName = first.packageName;
        second.anyNotification = false;
        assertFalse(SoundMappingManager.hasDuplicateFallback(Arrays.asList(first, second)));
    }

    @Test public void keywordsAreTrimmedAndDeduplicatedWithoutSplittingPhrases() {
        assertEquals(Arrays.asList("Alex", "call me"),
                SoundMappingManager.parseKeywords(" Alex \n\n alex\ncall me\n"));
    }

    @Test public void editingOwnFallbackIsAllowedButAnotherFallbackIsRejected() {
        SoundMapping existing = mapping();
        existing.id = "first";
        existing.anyNotification = true;
        existing.soundUri = "content://sounds/1";
        assertEquals(SoundMappingManager.ValidationError.NONE,
                SoundMappingManager.validate(existing, Collections.singletonList(existing)));
        SoundMapping other = mapping();
        other.id = "second";
        other.anyNotification = true;
        other.enabled = false;
        assertEquals(SoundMappingManager.ValidationError.DUPLICATE_FALLBACK,
                SoundMappingManager.validate(existing, Arrays.asList(existing, other)));
    }

    @Test public void validationAllowsBodyOnlyAndRequiresKeywordsOrFallback() {
        SoundMapping draft = mapping();
        draft.soundUri = "content://sounds/1";
        draft.titleKeywords.clear();
        assertEquals(SoundMappingManager.ValidationError.NONE,
                SoundMappingManager.validate(draft, Collections.emptyList()));
        draft.bodyKeywords.clear();
        assertEquals(SoundMappingManager.ValidationError.KEYWORD_REQUIRED,
                SoundMappingManager.validate(draft, Collections.emptyList()));
        draft.anyNotification = true;
        assertEquals(SoundMappingManager.ValidationError.NONE,
                SoundMappingManager.validate(draft, Collections.emptyList()));
    }
}
