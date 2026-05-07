package com.slagalica.app.util;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.slagalica.app.R;

public class ConfirmDialog {

    private static final float DIM_AMOUNT = 0.88f;

    public static void show(Activity activity,
                            String title,
                            String message,
                            String positiveText,
                            String negativeText,
                            Runnable onConfirm) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        Dialog d = new Dialog(activity);
        d.setContentView(R.layout.dialog_confirm);

        ((TextView) d.findViewById(R.id.tvDialogTitle)).setText(title);
        ((TextView) d.findViewById(R.id.tvDialogMessage)).setText(message);

        MaterialButton btnPositive = d.findViewById(R.id.btnDialogPositive);
        MaterialButton btnNegative = d.findViewById(R.id.btnDialogNegative);

        btnPositive.setText(positiveText);
        btnNegative.setText(negativeText);

        btnPositive.setOnClickListener(v -> {
            d.dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        btnNegative.setOnClickListener(v -> d.dismiss());

        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        d.getWindow().setDimAmount(DIM_AMOUNT);
        d.getWindow().setLayout(
            (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.88f),
            WindowManager.LayoutParams.WRAP_CONTENT
        );
        d.setCanceledOnTouchOutside(true);
        d.show();
    }

    public static void showInfo(Activity activity, String title, String message, String buttonText) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        Dialog d = new Dialog(activity);
        d.setContentView(R.layout.dialog_confirm);

        ((TextView) d.findViewById(R.id.tvDialogTitle)).setText(title);
        ((TextView) d.findViewById(R.id.tvDialogMessage)).setText(message);

        MaterialButton btnPositive = d.findViewById(R.id.btnDialogPositive);
        MaterialButton btnNegative = d.findViewById(R.id.btnDialogNegative);

        btnPositive.setText(buttonText);
        btnPositive.setOnClickListener(v -> d.dismiss());
        btnNegative.setVisibility(View.GONE);

        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        d.getWindow().setDimAmount(DIM_AMOUNT);
        d.getWindow().setLayout(
            (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.88f),
            WindowManager.LayoutParams.WRAP_CONTENT
        );
        d.setCanceledOnTouchOutside(true);
        d.show();
    }
}
