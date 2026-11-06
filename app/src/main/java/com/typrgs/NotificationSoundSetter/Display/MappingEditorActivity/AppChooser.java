package com.typrgs.NotificationSoundSetter.Display.MappingEditorActivity;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.typrgs.NotificationSoundSetter.R;
import com.typrgs.NotificationSoundSetter.Models.AppChoice;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** App chooser owned and closed by the mapping editor activity. */
public class AppChooser implements AutoCloseable {
    private final AppCompatActivity activity;
    private final Consumer<AppChoice> onSelected;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AlertDialog dialog;
    private boolean closed, loading;

    public AppChooser(AppCompatActivity activity, Consumer<AppChoice> onSelected) {
        this.activity = activity;
        this.onSelected = onSelected;
    }

    public void show() {
        if (closed || loading || (dialog != null && dialog.isShowing())) return;
        loading = true;
        Toast.makeText(activity, R.string.loading_apps, Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            List<ResolveInfo> apps = activity.getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            Map<String, AppChoice> unique = new HashMap<>();
            for (ResolveInfo app : apps) {
                String packageName = app.activityInfo.packageName;
                if (!packageName.equals(activity.getPackageName()) && !unique.containsKey(packageName)) {
                    unique.put(packageName, new AppChoice(packageName,
                            app.loadLabel(activity.getPackageManager()).toString(), app.loadIcon(activity.getPackageManager())));
                }
            }
            List<AppChoice> entries = new ArrayList<>(unique.values());
            entries.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            activity.runOnUiThread(() -> {
                loading = false;
                if (closed || activity.isDestroyed() || activity.isFinishing()) return;
                View layout = activity.getLayoutInflater().inflate(R.layout.dialog_app_chooser, null);
                EditText search = layout.findViewById(R.id.app_search);
                ListView list = layout.findViewById(R.id.app_list);
                List<AppChoice> filtered = new ArrayList<>(entries);
                BaseAdapter adapter = new BaseAdapter() {
                    @Override public int getCount() { return filtered.size(); }
                    @Override public AppChoice getItem(int position) { return filtered.get(position); }
                    @Override public long getItemId(int position) { return position; }
                    @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
                        View item = recycled == null
                                ? activity.getLayoutInflater().inflate(R.layout.item_app, parent, false) : recycled;
                        AppChoice app = getItem(position);
                        ((ImageView) item.findViewById(R.id.app_icon)).setImageDrawable(app.icon);
                        ((TextView) item.findViewById(R.id.app_name)).setText(app.name);
                        return item;
                    }
                };
                list.setAdapter(adapter);
                list.setEmptyView(layout.findViewById(R.id.empty_apps));
                Runnable refresh = () -> {
                    String query = search.getText().toString().toLowerCase(Locale.ROOT);
                    filtered.clear();
                    for (AppChoice app : entries) {
                        if (app.name.toLowerCase(Locale.ROOT).contains(query)) filtered.add(app);
                    }
                    adapter.notifyDataSetChanged();
                };
                watch(search, value -> refresh.run());
                refresh.run();
                dialog = new AlertDialog.Builder(activity).setTitle(R.string.choose_app)
                        .setView(layout).setNegativeButton(R.string.cancel, null).create();
                list.setOnItemClickListener((parent, view, position, id) -> {
                    AppChoice selected = filtered.get(position);
                    dialog.dismiss();
                    onSelected.accept(selected);
                });
                dialog.show();
            });
        });
    }

    private void watch(EditText input, java.util.function.Consumer<String> change) {
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { change.accept(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override public void close() {
        closed = true;
        if (dialog != null) dialog.dismiss();
        worker.shutdownNow();
    }
}
