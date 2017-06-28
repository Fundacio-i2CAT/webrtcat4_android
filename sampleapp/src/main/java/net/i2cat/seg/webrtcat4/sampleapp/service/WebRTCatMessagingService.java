package net.i2cat.seg.webrtcat4.sampleapp.service;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import net.i2cat.seg.webrtcat4.sampleapp.util.AndroidUtils;
import net.i2cat.seg.webrtcat4.sampleapp.util.HttpClientUtils;
import net.i2cat.seg.webrtcat4.sampleapp.util.JSONUtils;
import net.i2cat.seg.webrtcat4.sampleapp.activity.WebRTCallActivity;
import net.i2cat.seg.webrtcat4.sampleapp.util.WebRTConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * A service to receive notifications from firebase.
 */
public class WebRTCatMessagingService extends FirebaseMessagingService {
    // Notification message TTL. If we receive a notification older than this TTL
    // (based on the timestamp when it was sent) we ignore the message.
    private static final long NOTIF_MESSAGE_TTL = 80000L;

    /**
     * This method will be called when a message is received AND
     *   - the application is in the foreground, OR
     *   - the message is a data only message (NOT a notification NOR a notification/data hybrid)
     * Otherwise, the message goes to the device notification tray.
     *
     * For the differences between notification and data messages, see
     * https://firebase.google.com/docs/notifications/android/console-device#receive_and_handle_messages
     *
     * @param remoteMessage Object representing the message received from FCM.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        printMessage(remoteMessage);

        Bundle bundle = toBundle(remoteMessage);
        String messageType = bundle.getString("type");
        if ("INCOMING_CALL".equals(messageType)) {
            boolean startIncomingCall = true;
            Long timestamp = null;
            try {
                timestamp = Long.valueOf(bundle.getString("timestamp"));
            } catch (NumberFormatException e) {
                // Ignored
            }

            // If the notification message has a timestamp, check if the message has expired.
            // Note that for this to function correctly, the client clock and the notification server clock
            // should be (more or less) in sync. Furthermore the timestamp should be in UTC milliseconds.
            if (timestamp != null) {
                long messageAge = System.currentTimeMillis() - timestamp.longValue();
                startIncomingCall = (messageAge < NOTIF_MESSAGE_TTL);
                if (!startIncomingCall) {
                    Log.i(WebRTConstants.LOG_TAG, "Received expired " + messageType + " notification (sent " + messageAge + "ms ago)");
                }
            }
            if (startIncomingCall) {
                WebRTCallActivity.startIncomingCall(this, bundle);
            }
        }
    }

    public static void sendNotification(final Activity activity, String notifToken, String callerName, String roomId) {
        JSONObject notifData = new JSONObject();
        try {
            notifData.put("notifToken", notifToken);
            notifData.put("callerName", callerName);
            notifData.put("roomName", roomId);
        } catch (JSONException e) {
            Log.e(WebRTConstants.LOG_TAG, "Unable to create notifData JSON object", e);
            return;
        }

        String url = HttpClientUtils.buildUrl(WebRTConstants.getWebRTCat4NotifServiceURL(),
                "/notify");
        HttpClientUtils.postJSON(url, notifData, new JSONUtils.JSONObjectConsumer() {
            @Override
            public void onJSON(JSONObject obj) {
                if ((obj == null) || (obj.length() == 0)) {
                    Log.w(WebRTConstants.LOG_TAG, "Got empty/null response object from POST; check server logs?");
                } else  {
                    try {
                        String err = obj.optString("error", null);
                        if (err != null) {
                            Log.e(WebRTConstants.LOG_TAG, "Unable to send notification: " + err);
                        } else {
                            Log.d(WebRTConstants.LOG_TAG, "Notification sent successfully");
                        }
                    } catch (Exception e) {
                        Log.e(WebRTConstants.LOG_TAG, "Error while trying to process POST response object", e);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                AndroidUtils.longToast(activity, errorMessage);
            }
        });
    }

    private Bundle toBundle(RemoteMessage remoteMessage) {
        Bundle remoteMessageBundle = new Bundle();
        if (remoteMessage.getData() != null) {
            for (Map.Entry<String,String> e : remoteMessage.getData().entrySet()) {
                remoteMessageBundle.putString(e.getKey(), e.getValue());
            }
        }
        if (remoteMessage.getNotification() != null) {
            remoteMessageBundle.putString("notification.title", remoteMessage.getNotification().getTitle());
            remoteMessageBundle.putString("notification.body", remoteMessage.getNotification().getBody());
            remoteMessageBundle.putString("notification.icon", remoteMessage.getNotification().getIcon());
        }
        return remoteMessageBundle;
    }

    private void printMessage(RemoteMessage remoteMessage) {
        logIfNotNull("From: ", remoteMessage.getFrom());
        if (remoteMessage.getData() != null) {
            for (Map.Entry<String,String> e : remoteMessage.getData().entrySet()) {
                logIfNotNull("DATA: ", e.getKey() + ": " + e.getValue());
            }
        }
        if (remoteMessage.getNotification() != null) {
            logIfNotNull("NOTIFICATION: Title: ", remoteMessage.getNotification().getTitle());
            logIfNotNull("NOTIFICATION: Body : ", remoteMessage.getNotification().getBody());
            logIfNotNull("NOTIFICATION: Icon : ", remoteMessage.getNotification().getIcon());
        }
    }

    private void logIfNotNull(String prefix, String value) {
        if (value != null) {
            Log.d(WebRTConstants.LOG_TAG, prefix + value);
        }
    }
}
