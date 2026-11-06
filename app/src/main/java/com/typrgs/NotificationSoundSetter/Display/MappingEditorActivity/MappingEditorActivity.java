package com.typrgs.NotificationSoundSetter.Display.MappingEditorActivity;

import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.typrgs.NotificationSoundSetter.R;
import com.typrgs.NotificationSoundSetter.Models.NotificationSound;
import com.typrgs.NotificationSoundSetter.Models.SoundMapping;
import com.typrgs.NotificationSoundSetter.Persistence.SoundStore;
import com.typrgs.NotificationSoundSetter.Services.SoundMappingManager.SoundMappingManager;
import java.util.List;

public class MappingEditorActivity extends AppCompatActivity {
    public static final String EXTRA_MAPPING_ID = "mapping_id";
    private SoundStore store;
    private SoundMappingManager soundManager;
    private SoundMapping draft;
    private TextInputEditText titleInput;
    private AppChooser appChooser;
    private MediaPlayer preview;

    private final ActivityResultLauncher<Intent> systemSound = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null || draft == null) return;
                Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (uri != null) {
                    Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
                    draft.soundUri = uri.toString();
                    draft.soundName = ringtone == null ? getString(R.string.device_sound) : ringtone.getTitle(this);
                    updateSound();
                }
            });


    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new SoundStore(this);
        soundManager = new SoundMappingManager(this);
        if (state != null && state.containsKey("draftId")) {
            draft = new SoundMapping();
            draft.id = state.getString("draftId");
            draft.packageName = state.getString("package", "");
            draft.appName = state.getString("app", "");
            draft.titleKeywords = SoundMappingManager.parseKeywords(state.getString("titleKeywords", ""));
            draft.bodyKeywords = SoundMappingManager.parseKeywords(state.getString("bodyKeywords", ""));
            draft.anyNotification = state.getBoolean("anyNotification");
            draft.soundName = state.getString("soundName", "");
            draft.soundUri = state.getString("soundUri", "");
            draft.enabled = state.getBoolean("enabled", true);
        } else {
            String id = getIntent().getStringExtra(EXTRA_MAPPING_ID);
            draft = id == null ? soundManager.newMapping() : soundManager.getMapping(id);
            if (draft == null) { finish(); return; }
        }
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!dark);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(!dark);
        setContentView(R.layout.activity_mapping_editor);
        View scroll = findViewById(R.id.editor_scroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(scroll);
        appChooser = new AppChooser(this, app -> {
            draft.packageName = app.packageName;
            draft.appName = app.name;
            ((MaterialButton) findViewById(R.id.choose_app)).setText(app.name);
        });
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { confirmDiscard(); }
        });
        renderEditor();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (draft != null) {
            out.putString("draftId", draft.id);
            out.putString("package", draft.packageName);
            out.putString("app", draft.appName);
            out.putString("titleKeywords", String.join("\n", draft.titleKeywords));
            out.putString("bodyKeywords", String.join("\n", draft.bodyKeywords));
            out.putBoolean("anyNotification", draft.anyNotification);
            out.putString("soundName", draft.soundName);
            out.putString("soundUri", draft.soundUri);
            out.putBoolean("enabled", draft.enabled);
        }
    }

    private void renderEditor() {
        View editor = findViewById(R.id.editor_content);
        editor.findViewById(R.id.back).setOnClickListener(v -> confirmDiscard());
        MaterialButton chooseApp = editor.findViewById(R.id.choose_app);
        chooseApp.setText(draft.packageName.isEmpty() ? getString(R.string.choose_app) : draft.appName);
        chooseApp.setOnClickListener(v -> appChooser.show());
        SwitchCompat anyNotification = editor.findViewById(R.id.any_notification);
        anyNotification.setChecked(draft.anyNotification);
        View keywords = editor.findViewById(R.id.keyword_fields);
        View fallbackHelp = editor.findViewById(R.id.fallback_help);
        keywords.setVisibility(draft.anyNotification ? View.GONE : View.VISIBLE);
        fallbackHelp.setVisibility(draft.anyNotification ? View.VISIBLE : View.GONE);
        anyNotification.setOnCheckedChangeListener((view, checked) -> {
            draft.anyNotification = checked;
            keywords.setVisibility(checked ? View.GONE : View.VISIBLE);
            fallbackHelp.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        titleInput = editor.findViewById(R.id.title_keywords);
        titleInput.setText(String.join("\n", draft.titleKeywords));
        watch(titleInput, value -> draft.titleKeywords = SoundMappingManager.parseKeywords(value));
        TextInputEditText bodyInput = editor.findViewById(R.id.body_keywords);
        bodyInput.setText(String.join("\n", draft.bodyKeywords));
        watch(bodyInput, value -> draft.bodyKeywords = SoundMappingManager.parseKeywords(value));
        ((TextView) editor.findViewById(R.id.selected_sound)).setText(
                draft.soundUri.isEmpty() ? getString(R.string.no_sound) : draft.soundName);
        editor.findViewById(R.id.choose_sound).setOnClickListener(v -> chooseSound());
        View previewButton = editor.findViewById(R.id.preview_sound);
        previewButton.setVisibility(draft.soundUri.isEmpty() ? View.GONE : View.VISIBLE);
        previewButton.setOnClickListener(v -> playPreview(Uri.parse(draft.soundUri)));
        editor.findViewById(R.id.save_mapping).setOnClickListener(v -> saveMapping());
        boolean existing = soundManager.getMapping(draft.id) != null;
        View delete = editor.findViewById(R.id.delete_mapping);
        delete.setVisibility(existing ? View.VISIBLE : View.GONE);
        delete.setOnClickListener(v ->
                new AlertDialog.Builder(this).setTitle(R.string.delete_mapping_title)
                        .setMessage(R.string.delete_mapping_message)
                        .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete, (d, w) -> {
                            soundManager.deleteMapping(draft.id);
                            setResult(RESULT_OK);
                            finish();
                        }).show());
    }


    private void updateSound() {
        ((TextView) findViewById(R.id.selected_sound)).setText(
                draft.soundUri.isEmpty() ? getString(R.string.no_sound) : draft.soundName);
        findViewById(R.id.preview_sound).setVisibility(draft.soundUri.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void chooseSound() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.choose_sound))
                .setItems(R.array.sound_sources, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.notification_sound));
                        if (!draft.soundUri.isEmpty()) intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(draft.soundUri));
                        try { systemSound.launch(intent); }
                        catch (android.content.ActivityNotFoundException e) { toast(getString(R.string.no_sound_picker)); }
                    } else {
                        List<NotificationSound> sounds = store.sounds();
                        if (sounds.isEmpty()) { toast(getString(R.string.import_first)); return; }
                        String[] names = sounds.stream().map(NotificationSound::getTitle).toArray(String[]::new);
                        new AlertDialog.Builder(this).setTitle(getString(R.string.my_sounds)).setItems(names, (d, index) -> {
                            draft.soundUri = sounds.get(index).getUri().toString();
                            draft.soundName = sounds.get(index).getTitle();
                            updateSound();
                        }).setNegativeButton(getString(R.string.cancel), null).show();
                    }
                }).show();
    }


    private void saveMapping() {
        SoundMappingManager.ValidationError error = soundManager.saveMapping(draft);
        switch (error) {
            case APP_REQUIRED: toast(getString(R.string.choose_app_first)); return;
            case KEYWORD_REQUIRED:
                titleInput.setError(getString(R.string.keyword_required));
                titleInput.requestFocus();
                return;
            case SOUND_REQUIRED: toast(getString(R.string.choose_sound_first)); return;
            case DUPLICATE_FALLBACK: toast(getString(R.string.duplicate_fallback)); return;
            case NONE:
                toast(getString(R.string.mapping_saved));
                setResult(RESULT_OK);
                finish();
        }
    }

    private void confirmDiscard() {
        new AlertDialog.Builder(this).setTitle(R.string.discard_title)
                .setNegativeButton(R.string.keep_editing, null)
                .setPositiveButton(R.string.discard, (dialog, which) -> finish()).show();
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
    @Override protected void onDestroy() {
        if (appChooser != null) appChooser.close();
        super.onDestroy();
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private void watch(EditText input, java.util.function.Consumer<String> change) {
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { change.accept(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });
    }
}
