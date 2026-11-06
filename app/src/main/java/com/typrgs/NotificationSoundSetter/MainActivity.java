package com.typrgs.NotificationSoundSetter;

import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.typrgs.NotificationSoundSetter.Models.NotificationSound;
import com.typrgs.NotificationSoundSetter.Models.SoundMapping;
import com.typrgs.NotificationSoundSetter.Persistence.SoundStore;
import com.typrgs.NotificationSoundSetter.Services.SoundMappingManager.SoundMappingManager;
import com.typrgs.NotificationSoundSetter.Display.MappingEditorActivity.MappingEditorActivity;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private SoundStore store;
    private LinearLayout content;
    private boolean soundsTab, importing;
    private SoundMappingManager soundManager;
    private MediaPlayer preview;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String[]> importSound = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) importAudio(uri);
            });
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new SoundStore(this);
        soundManager = new SoundMappingManager(this);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!dark);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(!dark);
        if (state != null) soundsTab = state.getBoolean("soundsTab");
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store != null) render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("soundsTab", soundsTab);
    }

    private void openEditor(String mappingId) {
        Intent intent = new Intent(this, MappingEditorActivity.class);
        if (mappingId != null) intent.putExtra(MappingEditorActivity.EXTRA_MAPPING_ID, mappingId);
        startActivity(intent);
    }

    private void render() {
        setContentView(R.layout.activity_main);
        View scroll = findViewById(R.id.main_scroll);
        content = findViewById(R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(scroll);
        View home = getLayoutInflater().inflate(R.layout.screen_home, content, false);
        content.addView(home);
        boolean enabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName());
        ((TextView) home.findViewById(R.id.notification_status)).setText(
                enabled ? R.string.notification_enabled : R.string.notification_required);
        home.findViewById(R.id.notification_explanation).setVisibility(enabled ? View.GONE : View.VISIBLE);
        View enable = home.findViewById(R.id.enable_notification);
        enable.setVisibility(enabled ? View.GONE : View.VISIBLE);
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        boolean unrestricted = power.isIgnoringBatteryOptimizations(getPackageName());
        ((TextView) home.findViewById(R.id.battery_status)).setText(
                unrestricted ? R.string.battery_ignored : R.string.battery_enabled);
        home.findViewById(R.id.battery_explanation).setVisibility(unrestricted ? View.GONE : View.VISIBLE);
        View battery = home.findViewById(R.id.ignore_battery);
        battery.setVisibility(unrestricted ? View.GONE : View.VISIBLE);
        battery.setOnClickListener(v -> requestBatteryExemption());

        View mappings = home.findViewById(R.id.mappings_tab);
        View sounds = home.findViewById(R.id.sounds_tab);
        mappings.setSelected(!soundsTab);
        sounds.setSelected(soundsTab);
        mappings.setOnClickListener(v -> { soundsTab = false; render(); });
        sounds.setOnClickListener(v -> { soundsTab = true; render(); });
        LinearLayout page = home.findViewById(R.id.page_content);
        if (soundsTab) renderSounds(page); else renderMappings(page);
    }

    private void renderMappings(LinearLayout parent) {
        View page = getLayoutInflater().inflate(R.layout.screen_mappings, parent, false);
        parent.addView(page);
        List<SoundMapping> mappings = soundManager.mappings();
        ((TextView) page.findViewById(R.id.mapping_count)).setText(getString(R.string.mapping_count, mappings.size()));
        page.findViewById(R.id.new_mapping).setOnClickListener(v -> openEditor(null));
        page.findViewById(R.id.empty_mappings).setVisibility(mappings.isEmpty() ? View.VISIBLE : View.GONE);
        LinearLayout list = page.findViewById(R.id.mapping_list);
        for (SoundMapping mapping : mappings) {
            View item = getLayoutInflater().inflate(R.layout.item_mapping, list, false);
            list.addView(item);
            SwitchCompat toggle = item.findViewById(R.id.mapping_enabled);
            toggle.setText(mapping.appName);
            toggle.setChecked(mapping.enabled);
            String description = mapping.anyNotification ? getString(R.string.any_notification)
                    : String.join(", ", mapping.titleKeywords) + "; " + String.join(", ", mapping.bodyKeywords);
            toggle.setContentDescription(getString(R.string.enable_mapping_description, mapping.appName, description));
            toggle.setOnCheckedChangeListener((v, checked) -> {
                soundManager.setEnabled(mapping.id, checked);
            });
            item.findViewById(R.id.mapping_fallback).setVisibility(mapping.anyNotification ? View.VISIBLE : View.GONE);
            TextView title = item.findViewById(R.id.mapping_title);
            title.setVisibility(!mapping.anyNotification && !mapping.titleKeywords.isEmpty() ? View.VISIBLE : View.GONE);
            title.setText(getString(R.string.title_summary, String.join(", ", mapping.titleKeywords)));
            TextView body = item.findViewById(R.id.mapping_body);
            body.setVisibility(!mapping.anyNotification && !mapping.bodyKeywords.isEmpty() ? View.VISIBLE : View.GONE);
            body.setText(getString(R.string.body_summary, String.join(", ", mapping.bodyKeywords)));
            ((TextView) item.findViewById(R.id.mapping_sound)).setText(getString(R.string.sound_summary, mapping.soundName));
            item.findViewById(R.id.edit_mapping).setOnClickListener(v -> openEditor(mapping.id));
        }
    }

    private void renderSounds(LinearLayout parent) {
        View page = getLayoutInflater().inflate(R.layout.screen_sounds, parent, false);
        parent.addView(page);
        List<NotificationSound> sounds = store.sounds();
        ((TextView) page.findViewById(R.id.sound_count)).setText(getString(R.string.sound_count, sounds.size()));
        MaterialButton upload = page.findViewById(R.id.import_sound);
        upload.setText(importing ? R.string.importing : R.string.import_sound);
        upload.setEnabled(!importing);
        upload.setOnClickListener(v -> importSound.launch(new String[]{"audio/*"}));
        page.findViewById(R.id.empty_sounds).setVisibility(sounds.isEmpty() ? View.VISIBLE : View.GONE);
        LinearLayout list = page.findViewById(R.id.sound_list);
        for (NotificationSound sound : sounds) {
            View item = getLayoutInflater().inflate(R.layout.item_sound, list, false);
            list.addView(item);
            ((TextView) item.findViewById(R.id.sound_name)).setText(sound.getTitle());
            item.findViewById(R.id.preview_sound).setOnClickListener(v -> playPreview(sound.getUri()));
            item.findViewById(R.id.remove_sound).setOnClickListener(v -> removeSound(sound));
        }
    }

    private void requestBatteryExemption() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + getPackageName())));
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                toast(getString(R.string.battery_unavailable));
            }
        }
    }

    private void importAudio(Uri source) {
        importing = true;
        render();
        // Keep the application context so imports can finish safely across rotation.
        android.content.Context app = getApplicationContext();
        worker.execute(() -> {
            File target = null;
            try {
                String name = getString(R.string.custom_sound);
                try (Cursor cursor = app.getContentResolver().query(source,
                        new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                }
                File directory = new File(app.getFilesDir(), "sounds");
                if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create library");
                target = new File(directory, UUID.randomUUID().toString());
                try (InputStream input = app.getContentResolver().openInputStream(source);
                     OutputStream output = new FileOutputStream(target)) {
                    if (input == null) throw new IOException("Cannot read file");
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > 25L * 1024 * 1024) throw new IOException("Choose audio smaller than 25 MB");
                        output.write(buffer, 0, count);
                    }
                    if (total == 0) throw new IOException("This file is empty");
                }
                MediaPlayer validator = new MediaPlayer();
                try { validator.setDataSource(target.getAbsolutePath()); validator.prepare(); }
                finally { validator.release(); }
                NotificationSound imported = new NotificationSound(name == null ? getString(R.string.custom_sound) : name, Uri.fromFile(target));
                runOnUiThread(() -> {
                    SoundStore current = new SoundStore(app);
                    List<NotificationSound> sounds = current.sounds();
                    sounds.add(imported);
                    current.saveSounds(sounds);
                    importing = false;
                    if (!isDestroyed()) { render(); toast(getString(R.string.sound_imported)); }
                });
            } catch (Exception e) {
                if (target != null) target.delete();
                runOnUiThread(() -> {
                    importing = false;
                    if (!isDestroyed()) { render(); toast(getString(R.string.import_failed)); }
                });
            }
        });
    }

    private void removeSound(NotificationSound sound) {
        boolean used = soundManager.isSoundInUse(sound.getUri().toString());
        if (used) { toast(getString(R.string.sound_in_use)); return; }
        new AlertDialog.Builder(this).setTitle(getString(R.string.remove_sound_title)).setMessage(sound.getTitle())
                .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.remove), (dialog, which) -> {
                    stopPreview();
                    List<NotificationSound> sounds = store.sounds();
                    sounds.removeIf(s -> s.getUri().equals(sound.getUri()));
                    store.saveSounds(sounds);
                    if ("file".equals(sound.getUri().getScheme())) new File(sound.getUri().getPath()).delete();
                    render();
                }).show();
    }

    private void playPreview(Uri uri) {
        stopPreview();
        preview = new MediaPlayer();
        try {
            preview.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            preview.setDataSource(this, uri);
            preview.setOnPreparedListener(MediaPlayer::start);
            preview.setOnCompletionListener(p -> stopPreview());
            preview.setOnErrorListener((p, what, extra) -> { stopPreview(); toast(getString(R.string.sound_unavailable)); return true; });
            preview.prepareAsync();
        } catch (Exception e) { stopPreview(); toast(getString(R.string.sound_unavailable)); }
    }

    private void stopPreview() { if (preview != null) { preview.release(); preview = null; } }
    @Override protected void onStop() { stopPreview(); super.onStop(); }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

}
