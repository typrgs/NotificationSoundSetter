package com.typrgs.NotificationSoundSetter.Models;

import android.net.Uri;

public class NotificationSound
{
    private final String title;
    private final Uri uri;

    public NotificationSound(String title, Uri uri)
    {
        this.title = title;
        this.uri = uri;
    }

    public String getTitle()
    {
        return title;
    }

    public Uri getUri()
    {
        return uri;
    }
}
