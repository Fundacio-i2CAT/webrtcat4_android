package net.i2cat.seg.webrtcat4.sampleapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import net.i2cat.seg.webrtcat4.sampleapp.service.WebRTCatIdService;
import net.i2cat.seg.webrtcat4.sampleapp.util.HttpClientUtils;
import net.i2cat.seg.webrtcat4.sampleapp.util.JSONUtils;
import net.i2cat.seg.webrtcat4.sampleapp.util.WebRTConstants;

import org.json.JSONException;
import org.json.JSONObject;

public class WebRTCatUserManager {
    private static final String PREF_WEBRTCAT_USERID = "webrtcat.userid";
    private static final String PREF_WEBRTCAT_USERNAME = "webrtcat.username";

    private Context context;

    public WebRTCatUserManager(Context context) {
        this.context = context;

        // Add local broadcast receiver to listen for TOKEN_REFRESH messages from the notification id service.
        LocalBroadcastManager localBroadcastMgr = LocalBroadcastManager.getInstance(context);
        localBroadcastMgr.registerReceiver(new TokenRefreshMessageReceiver(),
                new IntentFilter(WebRTConstants.INTENT_FILTER_NOTIF_TOKEN_REFRESH));
    }

    public static String getSavedUsername(Context ctx) {
        return getPreference(ctx, PREF_WEBRTCAT_USERNAME);
    }

    public String getSavedUsername() {
        return getPreference(this.context, PREF_WEBRTCAT_USERNAME);
    }

    public void saveUsername(String username) {
        savePreference(PREF_WEBRTCAT_USERNAME, username);
        // Username has been persisted -- make sure to persist this in the backend.
        sendUserInfo();
    }

    public String getSavedUserId() {
        return getPreference(this.context, PREF_WEBRTCAT_USERID);
    }

    public void saveUserId(String uid) {
        savePreference(PREF_WEBRTCAT_USERID, uid);
    }

    public void getContactList(JSONUtils.JSONArrayConsumer consumer) {
        String url = HttpClientUtils.buildUrl(WebRTConstants.
                getWebRTCat4UserServiceURL(), "/users");
        HttpClientUtils.getJSON(url, consumer);
    }

    private static String getPreference(Context ctx, String name) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getString(name, null);
    }

    private void savePreference(String name, String value) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(name, value).commit();
    }

    private void sendUserInfo() {
        if (WebRTConstants.getWebRTCat4UserServiceURL() == null) {
            // No user service was specified, so just return.
            Log.i(WebRTConstants.LOG_TAG, "No user service base URL specified, so user info will not be sent to backend");
            return;
        }

        String username = getSavedUsername();
        String uid = getSavedUserId();
        String notifToken = WebRTCatIdService.getActiveToken();
        if ((username != null) && (notifToken != null)) {
            // Already have both username and notification token, so store these in the backend,
            // under the same user id if this user already exists.
            JSONObject userInfo = new JSONObject();
            try {
                userInfo.put("username", username);
                userInfo.put("notif_token", notifToken);
            } catch (JSONException e) {
                Log.e(WebRTConstants.LOG_TAG, "Unable to create user JSON object", e);
                return;
            }

            String url = HttpClientUtils.buildUrl(WebRTConstants.
                    getWebRTCat4UserServiceURL(), "/user");
            if (uid != null) {
                // This user already exists, so specify the id in the url so we do an update.
                url = HttpClientUtils.buildUrl(url, "/" + uid);
            }
            HttpClientUtils.postJSON(url, userInfo, new JSONUtils.JSONObjectConsumer() {
                @Override
                public void onJSON(JSONObject obj) {
                    if ((obj == null) || (obj.length() == 0)) {
                        Log.w(WebRTConstants.LOG_TAG, "Got empty/null response object from POST; check user server logs");
                    } else {
                        try {
                            String uid = obj.getString("_id");
                            if (uid == null) {
                                Log.w(WebRTConstants.LOG_TAG, "Response object does not contain id: " + obj);
                            } else {
                                Log.d(WebRTConstants.LOG_TAG, "Saving user id: " + uid);
                                saveUserId(uid);
                            }
                        } catch (Exception e) {
                            Log.e(WebRTConstants.LOG_TAG, "Error while trying to process POST response object", e);
                        }
                    }
                }

                @Override
                public void onError(String message) {
                    Log.e(WebRTConstants.LOG_TAG, "Error while trying to save user info: " + message);
                }
            });
        }
    }

    private class TokenRefreshMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // A new token has been received -- make sure to persist it in the backend.
            WebRTCatUserManager.this.sendUserInfo();
        }
    }
}
