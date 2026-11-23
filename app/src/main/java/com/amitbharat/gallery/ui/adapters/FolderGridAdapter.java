package com.amitbharat.gallery.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.amitbharat.gallery.R;
import com.amitbharat.gallery.data.models.FolderItem;
import com.amitbharat.gallery.utils.FileUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FolderGridAdapter extends RecyclerView.Adapter<FolderGridAdapter.FolderViewHolder> {

    public interface OnFolderClickListener {
        void onFolderClick(FolderItem folder);
    }

    private final Context context;
    private final List<FolderItem> folderList = new ArrayList<>();
    private OnFolderClickListener listener;

    public FolderGridAdapter(Context context) {
        this.context = context;
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    public void setListener(OnFolderClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void onViewRecycled(@NonNull FolderViewHolder holder) {
        super.onViewRecycled(holder);
        Glide.with(context).clear(holder.imgCover);
    }

    public void submitList(List<FolderItem> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return folderList.size(); }
            @Override
            public int getNewListSize() { return newList != null ? newList.size() : 0; }
            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                FolderItem oldItem = folderList.get(oldItemPosition);
                FolderItem newItem = newList.get(newItemPosition);
                if (oldItem.getFolderPath() != null && newItem.getFolderPath() != null) {
                    return oldItem.getFolderPath().equals(newItem.getFolderPath());
                }
                return oldItem.getBucketId() == newItem.getBucketId();
            }
            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                FolderItem oldItem = folderList.get(oldItemPosition);
                FolderItem newItem = newList.get(newItemPosition);
                return oldItem.getFileCount() == newItem.getFileCount() &&
                        oldItem.getTotalSize() == newItem.getTotalSize() &&
                        Objects.equals(oldItem.getCoverUri(), newItem.getCoverUri()) &&
                        Objects.equals(oldItem.getCoverPath(), newItem.getCoverPath());
            }
        });

        folderList.clear();
        if (newList != null) {
            folderList.addAll(newList);
        }
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder_grid, parent, false);
        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        holder.bind(folderList.get(position));
    }

    @Override
    public int getItemCount() {
        return folderList.size();
    }

    class FolderViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCover, imgFolderIcon;
        TextView tvCountBadge, tvFolderName, tvFolderDetails;

        FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.imgCover);
            imgFolderIcon = itemView.findViewById(R.id.imgFolderIcon);
            tvCountBadge = itemView.findViewById(R.id.tvCountBadge);
            tvFolderName = itemView.findViewById(R.id.tvFolderName);
            tvFolderDetails = itemView.findViewById(R.id.tvFolderDetails);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onFolderClick(folderList.get(pos));
                }
            });
        }

        void bind(FolderItem folder) {
            tvFolderName.setText(folder.getFolderName());
            tvCountBadge.setText(String.valueOf(folder.getFileCount()));
            tvFolderDetails.setText(folder.getFileCount() == 1 ? "1 item" : folder.getFileCount() + " items");

            if (folder.getCoverUri() != null || folder.getCoverPath() != null) {
                imgFolderIcon.setVisibility(View.GONE);
                Glide.with(context)
                        .load(folder.getCoverUri() != null ? folder.getCoverUri() : folder.getCoverPath())
                        .thumbnail(0.2f)
                        .override(320, 320)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(imgCover);
            } else {
                imgCover.setImageDrawable(null);
                imgFolderIcon.setVisibility(View.VISIBLE);
            }
        }
    }
}
