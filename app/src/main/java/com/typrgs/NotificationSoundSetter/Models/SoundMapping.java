package com.typrgs.NotificationSoundSetter.Models;

import java.util.ArrayList;
import java.util.List;

/** Persisted mapping data. Matching and validation live in SoundMappingManager. */
public class SoundMapping {
    public String id, packageName, appName, soundName, soundUri;
    public List<String> titleKeywords = new ArrayList<>();
    public List<String> bodyKeywords = new ArrayList<>();
    public boolean anyNotification;
    public boolean enabled = true;
}
