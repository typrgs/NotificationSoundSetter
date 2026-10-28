package com.typrgs.NotificationSoundSetter.Services.NotificationSoundManager;

import android.content.Context;
import android.net.Uri;

import com.typrgs.NotificationSoundSetter.Models.NotificationSound;

import java.util.List;

public interface NotificationSoundManager
{
    public List<NotificationSound> getAvailableNotificationSounds();
    public void playNotificationSound(Context context, String key);
    public void registerSoundMapping(String key, Uri uri);
}
