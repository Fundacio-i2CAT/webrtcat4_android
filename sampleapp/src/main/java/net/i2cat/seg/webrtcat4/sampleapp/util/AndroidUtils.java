package net.i2cat.seg.webrtcat4.sampleapp.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

/**
 * Created by Lino on 26/07/2016.
 */
public class AndroidUtils {
    public static void setTextViewText(Activity activity, int resourceId, String text) {
        setTextViewText(activity.findViewById(android.R.id.content), resourceId, text);
    }

    public static void setImageViewImage(Activity activity, int imageViewResourceId, int imageResourceId) {
        setImageViewImage(activity.findViewById(android.R.id.content), imageViewResourceId, imageResourceId);
    }

    public static void setImageViewImage(Activity activity, int imageViewResourceId, byte[] imageBytes) {
        setImageViewImage(activity.findViewById(android.R.id.content), imageViewResourceId, imageBytes);
    }

    public static void setTextViewText(View parentView, int resourceId, String text) {
        TextView tv = (TextView)parentView.findViewById(resourceId);
        tv.setText(text);
    }

    public static void setImageViewImage(View parentView, int imageViewResourceId, int imageResourceId) {
        ImageView iv = (ImageView)parentView.findViewById(imageViewResourceId);
        iv.setImageResource(imageResourceId);
    }

    public static void setImageViewImage(View parentView, int imageViewResourceId, byte[] imageBytes) {
        ImageView iv = (ImageView)parentView.findViewById(imageViewResourceId);
        iv.setImageBitmap(BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length));
    }

    public static void shortToast(final Activity activity, final String text) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void longToast(final Activity activity, final String text) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void longToast(final Context context, final String text) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
    }

    private static String NUMBERS = "0123456789";
    public static String generateRandomNumeric(int length) {
        StringBuilder s = new StringBuilder(length);
        Random rnd = new Random(System.currentTimeMillis());
        for (int i = 0; i < length; i++) {
            s.append(NUMBERS.charAt(rnd.nextInt(NUMBERS.length())));
        }
        return s.toString();
    }
}
