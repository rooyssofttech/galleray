package com.amitbharat.gallery.data.repository;

import android.content.Context;
import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.amitbharat.gallery.data.database.AppDatabase;
import com.amitbharat.gallery.data.database.BookmarkDao;
import com.amitbharat.gallery.data.database.BookmarkEntity;
import com.amitbharat.gallery.data.models.FileItem;
import com.amitbharat.gallery.data.models.FilterOptions;
import com.amitbharat.gallery.utils.FileUtils;
import com.amitbharat.gallery.utils.PreferencesManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileRepository {
    private final Context context;
    private final BookmarkDao bookmarkDao;
    private final ExecutorService executor;

    private final MutableLiveData<List<FileItem>> currentFilesLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoadingLive = new MutableLiveData<>(false);

    public FileRepository(Context context) {
        this.context = context.getApplicationContext();
        this.bookmarkDao = AppDatabase.getInstance(context).bookmarkDao();
        this.executor = Executors.newFixedThreadPool(4);
    }

    public LiveData<List<FileItem>> getCurrentFilesLive() { return currentFilesLive; }
    public LiveData<Boolean> getIsLoadingLive() { return isLoadingLive; }

    public void loadDirectory(final String dirPath, final FilterOptions.SortOrder sortOrder) {
        isLoadingLive.postValue(true);
        executor.execute(() -> {
            try {
                File dir = new File(dirPath);
                List<FileItem> fileList = new ArrayList<>();
                boolean showHidden = PreferencesManager.getInstance(context).isShowHiddenFiles();

                if (dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (!showHidden && (file.isHidden() || file.getName().startsWith("."))) {
                                continue;
                            }
                            fileList.add(new FileItem(file, false));
                        }
                    }
                }

                sortFileList(fileList, sortOrder);
                currentFilesLive.postValue(fileList);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isLoadingLive.postValue(false);
            }
        });
    }

    public void loadCategoryFiles(final String category, final FilterOptions.SortOrder sortOrder) {
        isLoadingLive.postValue(true);
        executor.execute(() -> {
            List<FileItem> results = new ArrayList<>();
            try {
                Uri filesUri;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    filesUri = android.provider.MediaStore.Files.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL);
                } else {
                    filesUri = android.provider.MediaStore.Files.getContentUri("external");
                }

                String[] projection = new String[]{
                        android.provider.MediaStore.Files.FileColumns._ID,
                        android.provider.MediaStore.Files.FileColumns.DATA,
                        android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
                        android.provider.MediaStore.Files.FileColumns.SIZE,
                        android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED
                };

                String selection = null;
                String cat = category != null ? category.toLowerCase(java.util.Locale.ROOT) : "";
                if ("apks".equals(cat)) {
                    selection = android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.apk' OR "
                            + android.provider.MediaStore.Files.FileColumns.MIME_TYPE + " = 'application/vnd.android.package-archive'";
                } else if ("documents".equals(cat)) {
                    selection = android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.pdf' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.doc%' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.xls%' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.ppt%' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.txt' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.epub' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.rtf' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.csv' OR "
                            + android.provider.MediaStore.Files.FileColumns.MIME_TYPE + " LIKE 'application/pdf' OR "
                            + android.provider.MediaStore.Files.FileColumns.MIME_TYPE + " LIKE 'text/%'";
                } else if ("audio".equals(cat)) {
                    selection = android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE + " = "
                            + android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO + " OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.mp3' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.m4a' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.wav' OR "
                            + android.provider.MediaStore.Files.FileColumns.MIME_TYPE + " LIKE 'audio/%'";
                } else if ("archives".equals(cat)) {
                    selection = android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.zip' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.rar' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.7z' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.tar' OR "
                            + android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE '%.gz' OR "
                            + android.provider.MediaStore.Files.FileColumns.MIME_TYPE + " LIKE 'application/zip'";
                }

                if (selection != null) {
                    try (android.database.Cursor cursor = context.getContentResolver().query(
                            filesUri, projection, selection, null,
                            android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC")) {
                        if (cursor != null) {
                            int dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATA);
                            int nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME);
                            int sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.SIZE);
                            int dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED);

                            while (cursor.moveToNext()) {
                                String path = cursor.getString(dataCol);
                                String name = cursor.getString(nameCol);
                                long size = cursor.getLong(sizeCol);
                                long lastMod = cursor.getLong(dateCol) * 1000;
                                if (path != null && !path.isEmpty()) {
                                    results.add(new FileItem(name, path, false, size, lastMod));
                                    if (results.size() == 60) {
                                        List<FileItem> initialPacket = new ArrayList<>(results);
                                        sortFileList(initialPacket, sortOrder);
                                        currentFilesLive.postValue(initialPacket);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (results.isEmpty()) {
                    File root = android.os.Environment.getExternalStorageDirectory();
                    if (root != null && root.exists()) {
                        scanCategoryRecursively(root, cat, results, 0);
                    }
                }

                sortFileList(results, sortOrder);
                currentFilesLive.postValue(results);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isLoadingLive.postValue(false);
            }
        });
    }

    private void scanCategoryRecursively(File dir, String category, List<FileItem> out, int depth) {
        if (depth > 6 || dir == null || !dir.exists() || !dir.canRead() || out.size() >= 1000) return;
        
        String dirName = dir.getName().toLowerCase(java.util.Locale.ROOT);
        if (dirName.startsWith(".") && !dir.equals(android.os.Environment.getExternalStorageDirectory())) return;
        if (dirName.equals("data") || dirName.equals("obb")) {
            File parent = dir.getParentFile();
            if (parent != null && parent.getName().equalsIgnoreCase("Android")) {
                return; // Skip Android/data and Android/obb
            }
        }

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                scanCategoryRecursively(f, category, out, depth + 1);
            } else {
                String name = f.getName().toLowerCase(java.util.Locale.ROOT);
                boolean matches = false;
                if ("apks".equals(category) && name.endsWith(".apk")) {
                    matches = true;
                } else if ("documents".equals(category) && (name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".pptx") || name.endsWith(".ppt") || name.endsWith(".txt") || name.endsWith(".epub") || name.endsWith(".rtf") || name.endsWith(".csv"))) {
                    matches = true;
                } else if ("audio".equals(category) && (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".flac") || name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".wma") || name.endsWith(".opus") || name.endsWith(".m4p"))) {
                    matches = true;
                } else if ("archives".equals(category) && (name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".tar") || name.endsWith(".gz") || name.endsWith(".bz2") || name.endsWith(".xz") || name.endsWith(".iso"))) {
                    matches = true;
                }

                if (matches) {
                    out.add(new FileItem(f, false));
                }
            }
        }
    }

    public static void sortFileList(List<FileItem> list, FilterOptions.SortOrder sortOrder) {
        if (list == null) return;
        Collections.sort(list, (o1, o2) -> {
            // Folders always first
            if (o1.isDirectory() && !o2.isDirectory()) return -1;
            if (!o1.isDirectory() && o2.isDirectory()) return 1;

            switch (sortOrder) {
                case DATE_ASC:
                    return Long.compare(o1.getLastModified(), o2.getLastModified());
                case DATE_DESC:
                    return Long.compare(o2.getLastModified(), o1.getLastModified());
                case NAME_DESC:
                    return o2.getName().compareToIgnoreCase(o1.getName());
                case SIZE_DESC:
                    return Long.compare(o2.getSize(), o1.getSize());
                case SIZE_ASC:
                    return Long.compare(o1.getSize(), o2.getSize());
                case NAME_ASC:
                default:
                    return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });
    }

    public void createNewFolder(File parent, String folderName, Runnable onComplete) {
        executor.execute(() -> {
            File newFolder = new File(parent, folderName);
            if (!newFolder.exists()) {
                newFolder.mkdirs();
            }
            if (onComplete != null) onComplete.run();
        });
    }

    public void renameFile(File file, String newName, Runnable onComplete) {
        executor.execute(() -> {
            File target = new File(file.getParentFile(), newName);
            file.renameTo(target);
            if (onComplete != null) onComplete.run();
        });
    }

    public void deleteFiles(List<File> files, Runnable onComplete) {
        executor.execute(() -> {
            for (File f : files) {
                FileUtils.deleteRecursive(f);
            }
            if (onComplete != null) onComplete.run();
        });
    }
}
