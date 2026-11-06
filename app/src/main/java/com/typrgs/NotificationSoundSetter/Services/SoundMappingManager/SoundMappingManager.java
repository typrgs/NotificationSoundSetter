package com.typrgs.NotificationSoundSetter.Services.SoundMappingManager;

import android.content.Context;
import com.typrgs.NotificationSoundSetter.Models.SoundMapping;
import com.typrgs.NotificationSoundSetter.Persistence.SoundStore;
import java.util.*;

/** Owns mapping validation, updates and matching; persistence only serializes data. */
public class SoundMappingManager {
    private final SoundStore store;
    public enum ValidationError { NONE, APP_REQUIRED, KEYWORD_REQUIRED, SOUND_REQUIRED, DUPLICATE_FALLBACK }

    public SoundMappingManager(Context context) {
        store = new SoundStore(context.getApplicationContext());
    }

    public List<SoundMapping> mappings() { return store.mappings(); }

    public SoundMapping getMapping(String id) {
        for (SoundMapping mapping : mappings()) if (mapping.id.equals(id)) return mapping;
        return null;
    }

    public SoundMapping newMapping() {
        SoundMapping mapping = new SoundMapping();
        mapping.id = UUID.randomUUID().toString();
        mapping.packageName = mapping.appName = mapping.soundName = mapping.soundUri = "";
        return mapping;
    }

    public ValidationError saveMapping(SoundMapping draft) {
        List<SoundMapping> mappings = mappings();
        ValidationError error = validate(draft, mappings);
        if (error != ValidationError.NONE) return error;
        int index = -1;
        for (int i = 0; i < mappings.size(); i++) {
            if (mappings.get(i).id.equals(draft.id)) { index = i; break; }
        }
        if (index < 0) mappings.add(draft); else mappings.set(index, draft);
        store.saveMappings(mappings);
        return ValidationError.NONE;
    }

    public static ValidationError validate(SoundMapping draft, List<SoundMapping> mappings) {
        if (draft.packageName == null || draft.packageName.trim().isEmpty()) return ValidationError.APP_REQUIRED;
        if (!draft.anyNotification && draft.titleKeywords.stream().allMatch(k -> k.trim().isEmpty())
                && draft.bodyKeywords.stream().allMatch(k -> k.trim().isEmpty())) return ValidationError.KEYWORD_REQUIRED;
        if (draft.soundUri == null || draft.soundUri.isEmpty()) return ValidationError.SOUND_REQUIRED;
        for (SoundMapping mapping : mappings) {
            if (draft.anyNotification && mapping.anyNotification && mapping.packageName.equals(draft.packageName)
                    && !Objects.equals(mapping.id, draft.id)) return ValidationError.DUPLICATE_FALLBACK;
        }
        return ValidationError.NONE;
    }

    public void deleteMapping(String id) {
        List<SoundMapping> mappings = mappings();
        mappings.removeIf(mapping -> mapping.id.equals(id));
        store.saveMappings(mappings);
    }

    public void setEnabled(String id, boolean enabled) {
        List<SoundMapping> mappings = mappings();
        for (SoundMapping mapping : mappings) if (mapping.id.equals(id)) mapping.enabled = enabled;
        store.saveMappings(mappings);
    }

    public boolean isSoundInUse(String uri) {
        return mappings().stream().anyMatch(mapping -> mapping.soundUri.equals(uri));
    }

    public SoundMapping findMatch(String sourcePackage, CharSequence title, CharSequence body) {
        return findMatch(mappings(), sourcePackage, title, body);
    }

    public static List<String> parseKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String line : text.split("\\r?\\n")) {
            String keyword = line.trim();
            if (!keyword.isEmpty() && seen.add(keyword.toLowerCase(Locale.ROOT))) keywords.add(keyword);
        }
        return keywords;
    }

    public static boolean matches(SoundMapping mapping, String sourcePackage, CharSequence title, CharSequence body) {
        return mapping.enabled && mapping.packageName.equals(sourcePackage)
                && (mapping.anyNotification || containsAny(title, mapping.titleKeywords) || containsAny(body, mapping.bodyKeywords));
    }

    private static boolean containsAny(CharSequence value, List<String> keywords) {
        if (value == null) return false;
        String text = value.toString().toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (!keyword.trim().isEmpty() && text.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** Keyword rules take precedence regardless of where the app fallback was saved. */
    public static SoundMapping findMatch(List<SoundMapping> mappings, String sourcePackage,
                                         CharSequence title, CharSequence body) {
        SoundMapping fallback = null;
        for (SoundMapping mapping : mappings) {
            if (!matches(mapping, sourcePackage, title, body)) continue;
            if (!mapping.anyNotification) return mapping;
            if (fallback == null) fallback = mapping;
        }
        return fallback;
    }

    /** Disabled fallbacks still occupy the app's single fallback slot. */
    public static boolean hasDuplicateFallback(List<SoundMapping> mappings) {
        java.util.Set<String> apps = new java.util.HashSet<>();
        for (SoundMapping mapping : mappings) {
            if (mapping.anyNotification && !apps.add(mapping.packageName)) return true;
        }
        return false;
    }
}
