package com.slagalica.app.util;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;

import com.slagalica.app.R;

public class GameToast {

    public enum Type { SUCCESS, ERROR, INFO }

    private static final int DURATION_MS = 1300;
    private static final float DIM_AMOUNT = 0.35f;

    private static final int COLOR_SUCCESS = 0xFF4CAF50;
    private static final int COLOR_ERROR   = 0xFFFF5252;
    private static final int COLOR_INFO    = 0xFFF2F2F2;

    public static void show(Activity activity, String message) {
        show(activity, message, Type.INFO);
    }

    public static void show(Activity activity, String message, Type type) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        Dialog d = new Dialog(activity);
        d.setContentView(R.layout.toast_game);

        TextView tv = d.findViewById(R.id.tvToastMessage);
        tv.setText(message);
        int textColor = type == Type.SUCCESS ? COLOR_SUCCESS
                      : type == Type.ERROR   ? COLOR_ERROR
                      : COLOR_INFO;
        tv.setTextColor(textColor);

        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        d.getWindow().setDimAmount(DIM_AMOUNT);
        d.getWindow().setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        );
        d.setCanceledOnTouchOutside(true);
        d.show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (d.isShowing()) d.dismiss();
        }, DURATION_MS);
    }

    public static void showCountdown(Activity activity, Runnable onDone) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            onDone.run();
            return;
        }
        Dialog d = new Dialog(activity);
        d.setContentView(R.layout.toast_game);
        TextView tv = d.findViewById(R.id.tvToastMessage);
        tv.setTextColor(COLOR_INFO);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        d.getWindow().setDimAmount(DIM_AMOUNT);
        d.getWindow().setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        );
        d.setCancelable(false);
        d.show();

        Handler h = new Handler(Looper.getMainLooper());
        tv.setText("Game starts in 3");
        h.postDelayed(() -> tv.setText("Game starts in 2"), 1000);
        h.postDelayed(() -> tv.setText("Game starts in 1"), 2000);
        h.postDelayed(() -> {
            if (d.isShowing()) d.dismiss();
            onDone.run();
        }, 3000);
    }
}
