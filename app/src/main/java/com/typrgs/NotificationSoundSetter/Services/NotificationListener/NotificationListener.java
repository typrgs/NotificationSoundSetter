package com.typrgs.NotificationSoundSetter.Services.NotificationListener;

import android.app.Notification;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import com.typrgs.NotificationSoundSetter.Models.SoundMapping;
import com.typrgs.NotificationSoundSetter.Services.SoundMappingManager.SoundMappingManager;

public class NotificationListener extends NotificationListenerService {
    private MediaPlayer player;
    private SoundMappingManager soundManager;

    @Override public void onCreate() {
        super.onCreate();
        soundManager = new SoundMappingManager(this);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle extras = notification.extras;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (body == null) body = extras.getCharSequence(Notification.EXTRA_TEXT);
        SoundMapping mapping = soundManager.findMatch(sbn.getPackageName(), title, body);
        if (mapping != null) play(Uri.parse(mapping.soundUri));
    }

    private void play(Uri uri) {
        releasePlayer();
        MediaPlayer next = new MediaPlayer();
        player = next;
        try {
            next.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            next.setDataSource(this, uri);
            next.setOnPreparedListener(MediaPlayer::start);
            next.setOnCompletionListener(p -> releasePlayer());
            next.setOnErrorListener((p, what, extra) -> {
                releasePlayer();
                return true;
            });
            next.prepareAsync();
        } catch (Exception e) {
            Log.w("NotificationListener", "Could not play mapped sound", e);
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
