package com.amitbharat.gallery.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.amitbharat.gallery.data.models.FilterOptions;
import com.amitbharat.gallery.data.models.MediaItem;
import com.amitbharat.gallery.utils.MediaUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SearchViewModel extends AndroidViewModel {
    private final ExecutorService executor;
    private final MutableLiveData<List<MediaItem>> searchResultsLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isSearchingLive = new MutableLiveData<>(false);
    private final AtomicInteger searchReqId = new AtomicInteger(0);
    private List<MediaItem> allCachedMedia = new ArrayList<>();

    public SearchViewModel(@NonNull Application application) {
        super(application);
        this.executor = Executors.newSingleThreadExecutor();
        loadAllMedia();
    }

    public LiveData<List<MediaItem>> getSearchResultsLive() { return searchResultsLive; }
    public LiveData<Boolean> getIsSearchingLive() { return isSearchingLive; }

    public void loadAllMedia() {
        executor.execute(() -> {
            allCachedMedia = MediaUtils.queryAllMedia(getApplication(), true);
            searchResultsLive.postValue(allCachedMedia);
        });
    }

    public void performSearch(String query, FilterOptions.TypeFilter filter) {
        final int reqId = searchReqId.incrementAndGet();
        isSearchingLive.postValue(true);
        executor.execute(() -> {
            if (reqId != searchReqId.get()) return;
            List<MediaItem> filtered = new ArrayList<>();
            String lowerQuery = query != null ? query.toLowerCase(Locale.ROOT).trim() : "";

            for (MediaItem item : allCachedMedia) {
                if (reqId != searchReqId.get()) return;

                boolean matchesQuery = lowerQuery.isEmpty() ||
                        (item.getDisplayName() != null && item.getDisplayName().toLowerCase(Locale.ROOT).contains(lowerQuery)) ||
                        (item.getBucketDisplayName() != null && item.getBucketDisplayName().toLowerCase(Locale.ROOT).contains(lowerQuery));

                if (!matchesQuery) continue;

                boolean matchesType = true;
                if (filter == FilterOptions.TypeFilter.IMAGES) {
                    matchesType = !item.isVideo() && !item.isAudio();
                } else if (filter == FilterOptions.TypeFilter.VIDEOS) {
                    matchesType = item.isVideo();
                } else if (filter == FilterOptions.TypeFilter.AUDIO) {
                    matchesType = item.isAudio();
                } else if (filter == FilterOptions.TypeFilter.GIF) {
                    matchesType = item.getMimeType() != null && item.getMimeType().contains("gif");
                } else if (filter == FilterOptions.TypeFilter.LARGE) {
                    matchesType = item.getSize() > 20 * 1024 * 1024; // > 20MB
                }

                if (matchesType) {
                    filtered.add(item);
                    if (filtered.size() == 60) {
                        searchResultsLive.postValue(new ArrayList<>(filtered));
                        isSearchingLive.postValue(false);
                    }
                }
            }

            if (reqId == searchReqId.get()) {
                searchResultsLive.postValue(filtered);
                isSearchingLive.postValue(false);
            }
        });
    }
}
