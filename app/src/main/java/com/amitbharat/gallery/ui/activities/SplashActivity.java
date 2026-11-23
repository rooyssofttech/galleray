package com.amitbharat.gallery.ui.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import androidx.appcompat.app.AppCompatActivity;
import com.amitbharat.gallery.data.models.FilterOptions;
import com.amitbharat.gallery.data.repository.MediaRepository;
import com.amitbharat.gallery.databinding.ActivitySplashBinding;
import com.amitbharat.gallery.utils.PermissionHelper;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Pre-warm media cache in background if permissions are already granted
        if (PermissionHelper.hasStoragePermission(this)) {
            MediaRepository.getInstance(this).loadMedia(FilterOptions.SortOrder.DATE_DESC);
        }

        // Fast subtle fade-in animation
        AlphaAnimation fadeIn = new AlphaAnimation(0.2f, 1.0f);
        fadeIn.setDuration(250);
        binding.getRoot().startAnimation(fadeIn);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            if (getIntent() != null && getIntent().getData() != null) {
                intent.setDataAndType(getIntent().getData(), getIntent().getType());
                intent.setAction(getIntent().getAction());
            }
            startActivity(intent);
            finish();
        }, 250);
    }
}
