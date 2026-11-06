package com.typrgs.NotificationSoundSetter.Models;

import android.graphics.drawable.Drawable;

/** A launcher application displayed in the app chooser. */
public class AppChoice {
    public final String packageName;
    public final String name;
    public final Drawable icon;

    public AppChoice(String packageName, String name, Drawable icon) {
        this.packageName = packageName;
        this.name = name;
        this.icon = icon;
    }
}
