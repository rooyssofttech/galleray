package com.amitbharat.gallery.ui.adapters;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.amitbharat.gallery.R;
import com.amitbharat.gallery.data.models.MediaItem;
import com.amitbharat.gallery.utils.DateUtils;
import com.amitbharat.gallery.utils.FileUtils;
import com.amitbharat.gallery.utils.MediaUtils;
import java.util.ArrayList;
import java.util.List;

public class MediaGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final String PAYLOAD_SELECTION = "payload_selection";
    public static final String PAYLOAD_FAVORITE = "payload_favorite";

    public interface OnMediaClickListener {
        void onMediaClick(MediaItem item, int position);
        void onMediaLongClick(MediaItem item, int position);
        default void onHeaderSelectToggle(int headerPosition, String dateTitle, List<MediaItem> groupItems) {}
    }

    private static final int TYPE_GRID = 0;
    private static final int TYPE_LIST = 1;
    private static final int TYPE_HEADER = 2;

    private final Context context;
    private final List<MediaItem> mediaList = new ArrayList<>();
    private boolean isGridView = true;
    private OnMediaClickListener listener;

    public MediaGridAdapter(Context context) {
        this.context = context;
        setHasStableIds(true);
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    public boolean isHeader(int position) {
        if (position >= 0 && position < mediaList.size()) {
            return mediaList.get(position).isHeader();
        }
        return false;
    }

    public List<MediaItem> getItemsForHeader(int headerPosition) {
        List<MediaItem> items = new ArrayList<>();
        if (headerPosition < 0 || headerPosition >= mediaList.size()) return items;
        if (!mediaList.get(headerPosition).isHeader()) return items;
        for (int i = headerPosition + 1; i < mediaList.size(); i++) {
            MediaItem item = mediaList.get(i);
            if (item.isHeader()) break;
            items.add(item);
        }
        return items;
    }

    public boolean isHeaderAllSelected(int headerPosition) {
        if (headerPosition < 0 || headerPosition >= mediaList.size()) return false;
        int count = 0;
        for (int i = headerPosition + 1; i < mediaList.size(); i++) {
            MediaItem item = mediaList.get(i);
            if (item.isHeader()) break;
            count++;
            if (!item.isSelected()) {
                return false;
            }
        }
        return count > 0;
    }

    public int findHeaderPositionBefore(int position) {
        for (int i = position - 1; i >= 0; i--) {
            if (i < mediaList.size() && mediaList.get(i).isHeader()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < mediaList.size()) {
            MediaItem item = mediaList.get(position);
            if (item.isHeader()) {
                return item.getHeaderTitle() != null ? ("hdr_" + item.getHeaderTitle()).hashCode() : -1;
            }
            return item.getId();
        }
        return RecyclerView.NO_ID;
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof GridViewHolder) {
            Glide.with(context).clear(((GridViewHolder) holder).imgThumbnail);
        } else if (holder instanceof ListViewHolder) {
            Glide.with(context).clear(((ListViewHolder) holder).imgThumbnail);
        }
    }

    public void setListener(OnMediaClickListener listener) {
        this.listener = listener;
    }

    public void setGridView(boolean isGrid) {
        if (this.isGridView != isGrid) {
            this.isGridView = isGrid;
            notifyDataSetChanged();
        }
    }

    public boolean isGridView() {
        return isGridView;
    }

    public void submitList(List<MediaItem> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return mediaList.size(); }
            @Override
            public int getNewListSize() { return newList != null ? newList.size() : 0; }
            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                MediaItem oldItem = mediaList.get(oldItemPosition);
                MediaItem newItem = newList.get(newItemPosition);
                if (oldItem.isHeader() || newItem.isHeader()) {
                    return oldItem.isHeader() == newItem.isHeader() &&
                            java.util.Objects.equals(oldItem.getHeaderTitle(), newItem.getHeaderTitle());
                }
                return oldItem.equals(newItem);
            }
            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                MediaItem oldItem = mediaList.get(oldItemPosition);
                MediaItem newItem = newList.get(newItemPosition);
                if (oldItem.isHeader() || newItem.isHeader()) {
                    return java.util.Objects.equals(oldItem.getHeaderTitle(), newItem.getHeaderTitle());
                }
                return oldItem.isSelected() == newItem.isSelected() &&
                        oldItem.isFavorite() == newItem.isFavorite() &&
                        oldItem.getSize() == newItem.getSize();
            }
            @Nullable
            @Override
            public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                MediaItem oldItem = mediaList.get(oldItemPosition);
                MediaItem newItem = newList.get(newItemPosition);
                Bundle diffBundle = new Bundle();
                if (oldItem.isSelected() != newItem.isSelected()) {
                    diffBundle.putBoolean(PAYLOAD_SELECTION, newItem.isSelected());
                }
                if (oldItem.isFavorite() != newItem.isFavorite()) {
                    diffBundle.putBoolean(PAYLOAD_FAVORITE, newItem.isFavorite());
                }
                return diffBundle.isEmpty() ? null : diffBundle;
            }
        });

        mediaList.clear();
        if (newList != null) {
            mediaList.addAll(newList);
        }
        diffResult.dispatchUpdatesTo(this);
    }

    public List<MediaItem> getMediaList() {
        return mediaList;
    }

    public void appendItems(List<MediaItem> moreItems) {
        if (moreItems == null || moreItems.isEmpty()) return;
        int startPos = mediaList.size();
        mediaList.addAll(moreItems);
        notifyItemRangeInserted(startPos, moreItems.size());
    }

    public void clearAllSelections() {
        for (MediaItem item : mediaList) {
            item.setSelected(false);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= 0 && position < mediaList.size()) {
            if (mediaList.get(position).isHeader()) {
                return TYPE_HEADER;
            }
        }
        return isGridView ? TYPE_GRID : TYPE_LIST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_date_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (viewType == TYPE_GRID) {
            View view = inflater.inflate(R.layout.item_media_grid, parent, false);
            return new GridViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_media_list, parent, false);
            return new ListViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(mediaList.get(position), position);
            return;
        }
        if (!payloads.isEmpty()) {
            for (Object payload : payloads) {
                if (payload instanceof Bundle) {
                    Bundle b = (Bundle) payload;
                    if (b.containsKey(PAYLOAD_SELECTION)) {
                        boolean sel = b.getBoolean(PAYLOAD_SELECTION);
                        if (holder instanceof GridViewHolder) {
                            ((GridViewHolder) holder).imgCheck.setVisibility(sel ? View.VISIBLE : View.GONE);
                        } else if (holder instanceof ListViewHolder) {
                            ((ListViewHolder) holder).imgCheck.setVisibility(sel ? View.VISIBLE : View.GONE);
                        }
                    }
                    if (b.containsKey(PAYLOAD_FAVORITE)) {
                        boolean fav = b.getBoolean(PAYLOAD_FAVORITE);
                        if (holder instanceof GridViewHolder) {
                            ((GridViewHolder) holder).imgFavorite.setVisibility(fav ? View.VISIBLE : View.GONE);
                        } else if (holder instanceof ListViewHolder) {
                            ((ListViewHolder) holder).imgFavorite.setVisibility(fav ? View.VISIBLE : View.GONE);
                        }
                    }
                }
            }
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MediaItem item = mediaList.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item, position);
        } else if (holder instanceof GridViewHolder) {
            ((GridViewHolder) holder).bind(item);
        } else if (holder instanceof ListViewHolder) {
            ((ListViewHolder) holder).bind(item);
        }
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateHeader;
        ImageView btnSelectDateGroup;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
            btnSelectDateGroup = itemView.findViewById(R.id.btnSelectDateGroup);

            View.OnClickListener toggleClick = v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    List<MediaItem> items = getItemsForHeader(pos);
                    MediaItem hdr = mediaList.get(pos);
                    listener.onHeaderSelectToggle(pos, hdr.getHeaderTitle(), items);
                }
            };

            if (btnSelectDateGroup != null) {
                btnSelectDateGroup.setOnClickListener(toggleClick);
            }
            itemView.setOnClickListener(toggleClick);
        }

        void bind(MediaItem item, int position) {
            tvDateHeader.setText(item.getHeaderTitle() != null ? item.getHeaderTitle() : "");
            int pos = position >= 0 ? position : getAdapterPosition();
            boolean allSelected = isHeaderAllSelected(pos);
            if (btnSelectDateGroup != null) {
                if (allSelected) {
                    btnSelectDateGroup.setImageResource(R.drawable.ic_check_circle);
                    btnSelectDateGroup.setAlpha(1.0f);
                } else {
                    btnSelectDateGroup.setImageResource(R.drawable.ic_check_circle_outline);
                    btnSelectDateGroup.setAlpha(0.6f);
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return mediaList.size();
    }

    class GridViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail, imgCheck, imgFavorite;
        TextView tvDuration;

        GridViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.imgThumbnail);
            imgCheck = itemView.findViewById(R.id.imgCheck);
            imgFavorite = itemView.findViewById(R.id.imgFavorite);
            tvDuration = itemView.findViewById(R.id.tvDuration);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMediaClick(mediaList.get(pos), pos);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMediaLongClick(mediaList.get(pos), pos);
                }
                return true;
            });
        }

        void bind(MediaItem item) {
            if (item.isAudio()) {
                Glide.with(context)
                        .load(item.getUri() != null ? item.getUri() : item.getPath())
                        .thumbnail(0.2f)
                        .override(280, 280)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.ic_audio_disc)
                        .error(R.drawable.ic_audio_disc)
                        .centerCrop()
                        .into(imgThumbnail);
                tvDuration.setVisibility(View.VISIBLE);
                tvDuration.setText(item.getDuration() > 0 ? "🎵 " + MediaUtils.formatDuration(item.getDuration()) : "🎵 Audio");
            } else {
                Glide.with(context)
                        .load(item.getUri() != null ? item.getUri() : item.getPath())
                        .thumbnail(0.2f)
                        .override(280, 280)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .transition(DrawableTransitionOptions.withCrossFade(100))
                        .into(imgThumbnail);

                if (item.isVideo() && item.getDuration() > 0) {
                    tvDuration.setVisibility(View.VISIBLE);
                    tvDuration.setText(MediaUtils.formatDuration(item.getDuration()));
                } else {
                    tvDuration.setVisibility(View.GONE);
                }
            }

            imgCheck.setVisibility(item.isSelected() ? View.VISIBLE : View.GONE);
            imgFavorite.setVisibility(item.isFavorite() ? View.VISIBLE : View.GONE);
        }
    }

    class ListViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail, imgCheck, imgFavorite;
        TextView tvTitle, tvDetails, tvDuration;

        ListViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.imgThumbnail);
            imgCheck = itemView.findViewById(R.id.imgCheck);
            imgFavorite = itemView.findViewById(R.id.imgFavorite);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDetails = itemView.findViewById(R.id.tvDetails);
            tvDuration = itemView.findViewById(R.id.tvDuration);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMediaClick(mediaList.get(pos), pos);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMediaLongClick(mediaList.get(pos), pos);
                }
                return true;
            });
        }

        void bind(MediaItem item) {
            if (item.isAudio()) {
                Glide.with(context)
                        .load(item.getUri() != null ? item.getUri() : item.getPath())
                        .thumbnail(0.2f)
                        .override(160, 160)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.ic_audio_disc)
                        .error(R.drawable.ic_audio_disc)
                        .centerCrop()
                        .into(imgThumbnail);
            } else {
                Glide.with(context)
                        .load(item.getUri() != null ? item.getUri() : item.getPath())
                        .thumbnail(0.2f)
                        .override(160, 160)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .into(imgThumbnail);
            }

            tvTitle.setText(item.getDisplayName());
            String details = (item.isAudio() ? "Audio • " : "") + FileUtils.formatFileSize(item.getSize()) + " • " + DateUtils.formatDate(item.getDateAdded());
            tvDetails.setText(details);

            if (item.isAudio()) {
                tvDuration.setVisibility(View.VISIBLE);
                tvDuration.setText(item.getDuration() > 0 ? "🎵 " + MediaUtils.formatDuration(item.getDuration()) : "🎵 Audio");
            } else if (item.isVideo() && item.getDuration() > 0) {
                tvDuration.setVisibility(View.VISIBLE);
                tvDuration.setText(MediaUtils.formatDuration(item.getDuration()));
            } else {
                tvDuration.setVisibility(View.GONE);
            }

            imgCheck.setVisibility(item.isSelected() ? View.VISIBLE : View.GONE);
            imgFavorite.setVisibility(item.isFavorite() ? View.VISIBLE : View.GONE);
        }
    }
}
