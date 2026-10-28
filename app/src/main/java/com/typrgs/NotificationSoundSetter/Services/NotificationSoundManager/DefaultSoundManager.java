package com.typrgs.NotificationSoundSetter.Services.NotificationSoundManager;

import android.content.Context;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import com.typrgs.NotificationSoundSetter.Models.NotificationSound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultSoundManager implements NotificationSoundManager
{
    private final String TAG = "DefaultSoundManager";
    private final List<NotificationSound> sounds;
    private final Map<String, Uri> soundMap;
    private final MediaPlayer player;

    public DefaultSoundManager(Context context)
    {
        RingtoneManager ringtoneManager = new RingtoneManager(context);
        player = new MediaPlayer();
        sounds = new ArrayList<>();
        soundMap = new HashMap<>();

        player.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION) // Matches system notification volume
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
        );
        player.setLooping(false);
        player.setOnCompletionListener(MediaPlayer::reset); // Reset media player on sound finish
        player.setOnPreparedListener(MediaPlayer::start); // Start playing on preparation finish

        ringtoneManager.setType(RingtoneManager.TYPE_NOTIFICATION);
        try (Cursor cursor = ringtoneManager.getCursor()) {
            while (cursor.moveToNext()) {
                String title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
//                Log.d(TAG, "Title: " + title);
                Uri uri = ringtoneManager.getRingtoneUri(cursor.getPosition());
                sounds.add(new NotificationSound(title, uri));
            }
        }
    }

    @Override
    public List<NotificationSound> getAvailableNotificationSounds()
    {
        return sounds;
    }

    @Override
    public void playNotificationSound(Context context, String key)
    {
        Uri soundUri = soundMap.getOrDefault(key, null);

        if (soundUri != null)
        {
            try
            {
                player.setDataSource(context, soundUri);
                player.prepareAsync();
            }
            catch (Exception e)
            {
                Log.e(TAG, "Error paying ringtone", e);
            }
        }
    }

    @Override
    public void registerSoundMapping(String key, Uri uri)
    {
        this.soundMap.put(key, uri);
    }
}
