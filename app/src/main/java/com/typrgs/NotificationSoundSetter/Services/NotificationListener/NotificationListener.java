package com.typrgs.NotificationSoundSetter.Services.NotificationListener;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.typrgs.NotificationSoundSetter.Models.NotificationSound;
import com.typrgs.NotificationSoundSetter.Services.NotificationSoundManager.DefaultSoundManager;
import com.typrgs.NotificationSoundSetter.Services.NotificationSoundManager.NotificationSoundManager;

import java.util.Arrays;
import java.util.List;

public class NotificationListener extends NotificationListenerService
{
    private static final String TAG = "NotificationListener";
    private static final String INSTAGRAM = "instagram";
    private static final String ANDROID_TITLE_KEY = "android.title";
    private static final String ANDROID_TEXT_KEY = "android.text";
    private static final String INSTA_NOTIF_TYPE_KEY = "com.instagram.android.igns.logging.collapse_key";

    private static final String[] SPECIAL_SOUND_TAGS = { "baby", "hannahmdao"};

    private NotificationSoundManager soundManager;

    @Override
    public void onCreate()
    {
        super.onCreate();

        this.soundManager = new DefaultSoundManager(this);
        List<NotificationSound> sounds = this.soundManager.getAvailableNotificationSounds();

        for (NotificationSound sound : sounds)
        {
            if (sound.getTitle().equalsIgnoreCase("android.resource://com.google.android.soundpicker/string/bubble"))
            {
                for (String tag : SPECIAL_SOUND_TAGS)
                {
                    this.soundManager.registerSoundMapping(tag, sound.getUri());
                }
            }
            else if (sound.getTitle().equalsIgnoreCase("red dwarf"))
            {
                this.soundManager.registerSoundMapping(INSTAGRAM, sound.getUri());
            }
        }

        Log.d(TAG, "Listener created");
    }

    @Override
    public void onListenerConnected()
    {
        super.onListenerConnected();
        Log.d(TAG, "Listener connected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn)
    {
        String packageName = sbn.getPackageName();

        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            Bundle notifBundle = sbn.getNotification().extras;

            String appName = pm.getApplicationLabel(appInfo).toString();
            String notifType = notifBundle.getString(INSTA_NOTIF_TYPE_KEY);

//            for(String key : notifBundle.keySet())
//            {
//                Log.d(TAG, key + ": " + notifBundle.get(key));
//            }

            if (appName.equalsIgnoreCase(INSTAGRAM) && notifType != null && notifType.contains("message"))
            {

                String notifTitle = notifBundle.getString(ANDROID_TITLE_KEY);
                Log.d(TAG, "Instagram notification posted: " + notifTitle);
                if (notifTitle != null && Arrays.stream(SPECIAL_SOUND_TAGS).anyMatch(notifTitle::equalsIgnoreCase))
                {
                    this.soundManager.playNotificationSound(this, notifTitle);
                }
                else
                {
                    this.soundManager.playNotificationSound(this, INSTAGRAM);
                }
            }

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "App not found: " + packageName, e);
        }
    }
}
