package com.typrgs.NotificationSoundSetter.Persistence;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import com.typrgs.NotificationSoundSetter.Models.NotificationSound;
import com.typrgs.NotificationSoundSetter.Models.SoundMapping;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Shared by the activity and listener; changes are visible without restarting the service. */
public class SoundStore {
    private final SharedPreferences preferences;

    public SoundStore(Context context) {
        preferences = context.getSharedPreferences("sound_library", Context.MODE_PRIVATE);
    }

    private JSONArray read(String key) {
        try {
            return new JSONArray(preferences.getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public List<SoundMapping> mappings() {
        List<SoundMapping> result = new ArrayList<>();
        JSONArray values = read("mappings");
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value == null) continue;
            SoundMapping mapping = new SoundMapping();
            mapping.id = value.optString("id");
            mapping.packageName = value.optString("package");
            mapping.appName = value.optString("app");
            mapping.titleKeywords = readKeywords(value, "titleKeywords", "title");
            mapping.bodyKeywords = readKeywords(value, "bodyKeywords", "body");
            mapping.anyNotification = value.optBoolean("anyNotification", false);
            mapping.soundName = value.optString("soundName");
            mapping.soundUri = value.optString("soundUri");
            mapping.enabled = value.optBoolean("enabled", true);
            result.add(mapping);
        }
        return result;
    }

    private List<String> readKeywords(JSONObject value, String key, String legacyKey) {
        List<String> keywords = new ArrayList<>();
        JSONArray array = value.optJSONArray(key);
        if (array == null) {
            // Upgrade existing single-keyword mappings without splitting phrases.
            String legacy = value.optString(legacyKey).trim();
            if (!legacy.isEmpty()) keywords.add(legacy);
        } else {
            for (int i = 0; i < array.length(); i++) {
                String keyword = array.optString(i).trim();
                if (!keyword.isEmpty()) keywords.add(keyword);
            }
        }
        return keywords;
    }

    public void saveMappings(List<SoundMapping> mappings) {
        JSONArray values = new JSONArray();
        try {
            for (SoundMapping mapping : mappings) {
                values.put(new JSONObject().put("id", mapping.id)
                        .put("package", mapping.packageName).put("app", mapping.appName)
                        .put("titleKeywords", new JSONArray(mapping.titleKeywords))
                        .put("bodyKeywords", new JSONArray(mapping.bodyKeywords))
                        .put("anyNotification", mapping.anyNotification)
                        .put("soundName", mapping.soundName).put("soundUri", mapping.soundUri)
                        .put("enabled", mapping.enabled));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        preferences.edit().putString("mappings", values.toString()).apply();
    }

    public List<NotificationSound> sounds() {
        List<NotificationSound> result = new ArrayList<>();
        JSONArray values = read("sounds");
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value != null) result.add(new NotificationSound(value.optString("name"),
                    Uri.parse(value.optString("uri"))));
        }
        return result;
    }

    public void saveSounds(List<NotificationSound> sounds) {
        JSONArray values = new JSONArray();
        try {
            for (NotificationSound sound : sounds) {
                values.put(new JSONObject().put("name", sound.getTitle())
                        .put("uri", sound.getUri().toString()));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        preferences.edit().putString("sounds", values.toString()).apply();
    }
}
