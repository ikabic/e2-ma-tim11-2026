package com.slagalica.app.util;

import android.view.View;
import android.widget.ImageView;
import com.google.android.material.imageview.ShapeableImageView;
import com.slagalica.app.R;

public class ProfileUtils {

    public static void applyRegionFrame(ImageView frameView, int regionRank, ShapeableImageView avatar) {
        if (frameView == null) return;

        int padPx = (int) (2 * avatar.getResources().getDisplayMetrics().density);

        switch (regionRank) {
            case 1:
                frameView.setImageResource(R.drawable.pfr_gold);
                frameView.setVisibility(View.VISIBLE);
                avatar.setStrokeWidth(0);
                avatar.setPadding(0, 0, 0, 0);
                break;
            case 2:
                frameView.setImageResource(R.drawable.pfr_silver);
                frameView.setVisibility(View.VISIBLE);
                avatar.setStrokeWidth(0);
                avatar.setPadding(0, 0, 0, 0);
                break;
            case 3:
                frameView.setImageResource(R.drawable.pfr_bronze);
                frameView.setVisibility(View.VISIBLE);
                avatar.setStrokeWidth(0);
                avatar.setPadding(0, 0, 0, 0);
                break;
            default:
                frameView.setVisibility(View.INVISIBLE);
                avatar.setStrokeWidth(2);
                avatar.setPadding(padPx, padPx, padPx, padPx);
                break;
        }
    }
}
