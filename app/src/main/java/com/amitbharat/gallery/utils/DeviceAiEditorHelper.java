package com.amitbharat.gallery.utils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DeviceAiEditorHelper {

    public static class AiEditorOption {
        public final String name;
        public final String packageName;
        public final String activityName;
        public final String description;
        public final Drawable icon;

        public AiEditorOption(String name, String packageName, String activityName, String description, Drawable icon) {
            this.name = name;
            this.packageName = packageName;
            this.activityName = activityName;
            this.description = description;
            this.icon = icon;
        }
    }

    public static List<AiEditorOption> getAvailableAiEditors(Context context, Uri imageUri) {
        List<AiEditorOption> list = new ArrayList<>();
        PackageManager pm = context.getPackageManager();

        // 1. Xiaomi / MIUI / HyperOS AI Media Editor (AI Magic Eraser, AI Sky, Cutout)
        try {
            pm.getPackageInfo("com.miui.mediaeditor", 0);
            list.add(new AiEditorOption(
                    "Xiaomi AI Media Editor (Magic Eraser & Sky)",
                    "com.miui.mediaeditor",
                    null,
                    "Built-in device AI: Object removal, Sky replacement & Enhancements",
                    pm.getApplicationIcon("com.miui.mediaeditor")
            ));
        } catch (Exception ignored) {}

        // 2. Google Photos (Magic Eraser, Magic Editor, Unblur, Portrait Light)
        try {
            pm.getPackageInfo("com.google.android.apps.photos", 0);
            list.add(new AiEditorOption(
                    "Google Photos (Magic Eraser & AI Editor)",
                    "com.google.android.apps.photos",
                    null,
                    "Google AI: Magic Eraser, Unblur & Portrait Lighting",
                    pm.getApplicationIcon("com.google.android.apps.photos")
            ));
        } catch (Exception ignored) {}

        // 3. Samsung Galaxy AI Photo Editor (Object Eraser, Remaster)
        try {
            pm.getPackageInfo("com.sec.android.app.editor", 0);
            list.add(new AiEditorOption(
                    "Samsung Galaxy AI Photo Editor",
                    "com.sec.android.app.editor",
                    null,
                    "Samsung AI: Object Eraser & Remaster",
                    pm.getApplicationIcon("com.sec.android.app.editor")
            ));
        } catch (Exception ignored) {}

        // 4. Query system image edit activities
        try {
            Intent queryIntent = new Intent(Intent.ACTION_EDIT);
            queryIntent.setDataAndType(imageUri != null ? imageUri : Uri.parse("content://dummy/image.jpg"), "image/*");
            List<ResolveInfo> resolves = pm.queryIntentActivities(queryIntent, PackageManager.MATCH_DEFAULT_ONLY);

            for (ResolveInfo info : resolves) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(context.getPackageName())) continue; // Ignore self

                boolean alreadyAdded = false;
                for (AiEditorOption o : list) {
                    if (o.packageName.equals(pkg)) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (!alreadyAdded) {
                    CharSequence label = info.loadLabel(pm);
                    Drawable icon = info.loadIcon(pm);
                    list.add(new AiEditorOption(
                            label != null ? label.toString() : pkg,
                            pkg,
                            info.activityInfo.name,
                            "System installed photo editor",
                            icon
                    ));
                }
            }
        } catch (Exception ignored) {}

        return list;
    }

    public static boolean hasDeviceAiEditor(Context context) {
        PackageManager pm = context.getPackageManager();
        String[] knownAiPackages = new String[]{
                "com.miui.mediaeditor",
                "com.google.android.apps.photos",
                "com.sec.android.app.editor"
        };
        for (String pkg : knownAiPackages) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (Exception ignored) {}
        }

        try {
            Intent queryIntent = new Intent(Intent.ACTION_EDIT);
            queryIntent.setType("image/*");
            List<ResolveInfo> resolves = pm.queryIntentActivities(queryIntent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo info : resolves) {
                if (info.activityInfo != null && !info.activityInfo.packageName.equals(context.getPackageName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    public static void openDeviceAiEditor(Activity activity, String imagePath, int requestCode) {
        if (imagePath == null || !new File(imagePath).exists()) {
            Toast.makeText(activity, "Image not found", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri contentUri;
        try {
            contentUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    new File(imagePath)
            );
        } catch (Exception e) {
            Toast.makeText(activity, "Cannot access file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        List<AiEditorOption> options = getAvailableAiEditors(activity, contentUri);
        if (options.isEmpty()) {
            // Fallback generic edit intent
            try {
                Intent intent = new Intent(Intent.ACTION_EDIT);
                intent.setDataAndType(contentUri, "image/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                activity.startActivityForResult(Intent.createChooser(intent, "Edit Image with Device AI"), requestCode);
            } catch (Exception e) {
                Toast.makeText(activity, "No external image editor found on device.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (options.size() == 1) {
            launchOption(activity, contentUri, options.get(0), requestCode);
            return;
        }

        CharSequence[] names = new CharSequence[options.size()];
        for (int i = 0; i < options.size(); i++) {
            names[i] = options.get(i).name;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("✨ Open in Device AI Editor")
                .setItems(names, (dialog, which) -> {
                    launchOption(activity, contentUri, options.get(which), requestCode);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void launchOption(Activity activity, Uri contentUri, AiEditorOption option, int requestCode) {
        try {
            Intent intent = new Intent(Intent.ACTION_EDIT);
            intent.setDataAndType(contentUri, "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (option.activityName != null) {
                intent.setComponent(new ComponentName(option.packageName, option.activityName));
            } else {
                intent.setPackage(option.packageName);
            }
            activity.startActivityForResult(intent, requestCode);
            Toast.makeText(activity, "Opening " + option.name + "...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // Fallback without explicit component
            try {
                Intent fallback = new Intent(Intent.ACTION_EDIT);
                fallback.setDataAndType(contentUri, "image/*");
                fallback.setPackage(option.packageName);
                fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                activity.startActivityForResult(fallback, requestCode);
            } catch (Exception ex) {
                Toast.makeText(activity, "Failed to launch: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
