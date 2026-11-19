package com.typrgs.NotificationSoundSetter.Services.NotificationListener;

import android.app.Notification;
import android.app.Person;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import com.typrgs.NotificationSoundSetter.Models.SoundMapping;
import com.typrgs.NotificationSoundSetter.Services.SoundMappingManager.SoundMappingManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NotificationListener extends NotificationListenerService {
    private MediaPlayer player;
    private SoundMappingManager soundManager;
    private final GroupedPostTracker groupedPosts = new GroupedPostTracker();

    @Override public void onCreate() {
        super.onCreate();
        soundManager = new SoundMappingManager(this);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        Notification notification = sbn.getNotification();
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Ranking ranking = new Ranking();
        if (rankingMap == null || !rankingMap.getRanking(sbn.getKey(), ranking)
                || !ranking.matchesInterruptionFilter()) return;

        observe(sbn, true);
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        groupedPosts.clear();
        StatusBarNotification[] active = getActiveNotifications();
        if (active != null) {
            for (StatusBarNotification sbn : active) {
                observe(sbn, false);
            }
        }
    }

    private void observe(StatusBarNotification sbn, boolean posted) {
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        String title = Objects.toString(extras.getCharSequence(Notification.EXTRA_TITLE), null);
        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        String body = Objects.toString(bigText != null ? bigText : extras.getCharSequence(Notification.EXTRA_TEXT), null);
        boolean summary = (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        SoundMapping mapping = null;

        if (summary) {
            groupedPosts.remove(sbn.getKey());
        } else {
            boolean duplicate = recordChild(sbn, title, body);
            if (posted && !duplicate) {
                mapping = soundManager.findMatch(sbn.getPackageName(), title, body);
            }
        }

        if (mapping != null) {
            play(Uri.parse(mapping.soundUri));
        }
    }

    private boolean recordChild(StatusBarNotification sbn, String title, String body) {
        List<String> content = new ArrayList<>();
        if ((title != null && !title.isEmpty()) || (body != null && !body.isEmpty())) {
            content.add(title);
            content.add(body);
        }

        boolean hasMessageTimestamp = false;
        Parcelable[] entries = sbn.getNotification().extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (entries != null) {
            List<Notification.MessagingStyle.Message> messages =
                    Notification.MessagingStyle.Message.getMessagesFromBundleArray(entries);
            if (!messages.isEmpty()) {
                Notification.MessagingStyle.Message latest = messages.get(messages.size() - 1);
                Person sender = latest.getSenderPerson();
                content.add(Integer.toString(messages.size()));
                content.add(Long.toString(latest.getTimestamp()));
                content.add(Objects.toString(latest.getText(), null));
                content.add(Objects.toString(latest.getDataUri(), null));
                content.add(sender == null ? null : sender.getKey());
                content.add(sender == null ? null : Objects.toString(sender.getName(), null));
                hasMessageTimestamp = latest.getTimestamp() > 0;
            }
        }

        String group = sbn.isGroup() ? sbn.getGroupKey() : null;
        return groupedPosts.record(sbn.getKey(), group, content, hasMessageTimestamp);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        groupedPosts.remove(sbn.getKey());
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
