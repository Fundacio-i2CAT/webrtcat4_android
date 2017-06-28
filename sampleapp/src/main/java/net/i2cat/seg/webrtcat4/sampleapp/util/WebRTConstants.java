package net.i2cat.seg.webrtcat4.sampleapp.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class WebRTConstants {
    private static final String CONFIG_PROPERTIES_FILE = "webrtcat.properties";

    private static String WEBRTCAT4_SERVER_URL;
    private static String WEBRTCAT4_USER_SERVICE_BASE_URL;
    private static String WEBRTCAT_NOTIF_SERVICE_BASE_URL;


    public static final String LOG_TAG = "WebRTCat";

    public static final String INTENT_FILTER_NOTIF_TOKEN_REFRESH = "net.i2cat.seg.webrtcat4.NOTIF_TOKEN_REFRESH";

    public static final String LIBJINGLE_VERSION = "13665 (08/08/16)";

    public static void initProperties(Activity activity) {
        try {
            WEBRTCAT4_SERVER_URL = getConfigValue(activity, "WEBRTCAT4_SERVER_URL");
            WEBRTCAT4_USER_SERVICE_BASE_URL = getConfigValue(activity,
                    "WEBRTCAT4_USER_SERVICE_BASE_URL");
            WEBRTCAT_NOTIF_SERVICE_BASE_URL = getConfigValue(activity,
                    "WEBRTCAT_NOTIF_SERVICE_BASE_URL");
        } catch (Exception e) {
            String msg = "Unable to read configuration properties. Bad build! Closing app...";
            Log.e(LOG_TAG, msg);
            AndroidUtils.longToast(activity, msg);
            activity.finishAffinity();
        }
        Log.i(LOG_TAG, "Properties correctly read.");
    }

    public static final String getWebRTCat4ServerURL() {
        return WEBRTCAT4_SERVER_URL;
    }

    public static final String getWebRTCat4UserServiceURL() {
        return WEBRTCAT4_USER_SERVICE_BASE_URL;
    }

    public static final String getWebRTCat4NotifServiceURL() {
        return WEBRTCAT_NOTIF_SERVICE_BASE_URL;
    }

    private static String getConfigValue(Context context, String name)
            throws IllegalArgumentException, IOException {
        String prop;
        try {
            AssetManager am = context.getApplicationContext().getAssets();
            InputStream propertiesIS = am.open(CONFIG_PROPERTIES_FILE);

            Properties properties = new Properties();
            properties.load(propertiesIS);
            prop = properties.getProperty(name);
        } catch (IllegalArgumentException e) {
            String msg = "Unable to find the config file: " + e.getMessage();
            Log.e(LOG_TAG, msg);
            throw e;
        } catch (IOException e) {
            String msg = "Failed to open config file.";
            Log.e(LOG_TAG, msg);
            throw e;
        }

        if (prop == null) {
            String msg = "Unable to find the config property '" + name + "' in file "
                    + CONFIG_PROPERTIES_FILE;
            Log.e(LOG_TAG, msg);
            throw new IllegalArgumentException(msg);
        }

        return prop;
    }
}
