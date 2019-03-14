package com.wagner.test.totalreader;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class WagnerToast {

    public static void showSuccess(Context context, int stringResourceId, int length) {
        showSuccess(context, context.getString(stringResourceId), length);
    }

    public static void showError(Context context, int stringResourceId, int length) {
        showError(context, context.getString(stringResourceId), length);
    }

    public static void showSuccess(Context context, int stringResourceId) {
        showSuccess(context, context.getString(stringResourceId), Toast.LENGTH_LONG);
    }

    public static void showError(Context context, int stringResourceId) {
        showError(context, context.getString(stringResourceId), Toast.LENGTH_LONG);
    }

    public static void showSuccess(Context context, String message, int length) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(R.layout.wagner_toast, null);

        LinearLayout llContainer = (LinearLayout) layout.findViewById(R.id.llContainer);
//        llContainer.setBackgroundResource(R.drawable.talsam_toast_success);

        TextView text = (TextView) layout.findViewById(R.id.text);
        text.setText(message);

        Toast toast = new Toast(context);
        toast.setGravity(Gravity.FILL_HORIZONTAL | Gravity.FILL_VERTICAL, 0, 0);
        toast.setDuration(length);
        toast.setView(layout);
        toast.show();
    }

    public static void showError(Context context, String message, int length) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(R.layout.wagner_toast, null);

        LinearLayout llContainer = (LinearLayout) layout.findViewById(R.id.llContainer);
        llContainer.setBackgroundColor(context.getResources().getColor(R.color.colorAccent));
//        llContainer.setBackgroundResource(R.drawable.talsam_toast_error);

        TextView text = (TextView) layout.findViewById(R.id.text);
        text.setText(message);

        Toast toast = new Toast(context);
        toast.setGravity(Gravity.FILL_HORIZONTAL | Gravity.FILL_VERTICAL, 0, 0);
        toast.setDuration(length);
        toast.setView(layout);
        toast.show();
    }
}
