package net.i2cat.seg.webrtcat4.sampleapp.service;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import net.i2cat.seg.webrtcat4.sampleapp.util.WebRTConstants;

/**
 * A service to retrieve an instance token to be used for receiving notifications.
 */
public class WebRTCatIdService extends FirebaseInstanceIdService {
    /**
     * Called if the instance token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the instance token
     * is initially generated so this is where you would retrieve the token.
     */
    @Override
    public void onTokenRefresh() {
        // Get updated InstanceID token.
        String notifToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(WebRTConstants.LOG_TAG, "Received new notification token: " + notifToken);

        // Broadcast the new token.
        LocalBroadcastManager localBroadcastMgr = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(WebRTConstants.INTENT_FILTER_NOTIF_TOKEN_REFRESH);
        localBroadcastMgr.sendBroadcast(intent);
    }

    public static String getActiveToken() {
        return FirebaseInstanceId.getInstance().getToken();
    }
}
