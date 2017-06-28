/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package net.i2cat.seg.webrtcat4.sampleapp.activity;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;

import net.i2cat.seg.webrtcat4.sampleapp.util.AndroidUtils;
import net.i2cat.seg.webrtcat4.WebRTCat;
import net.i2cat.seg.webrtcat4.WebRTCatErrorCode;
import net.i2cat.seg.webrtcat4.WebRTCatParams;
import net.i2cat.seg.webrtcat4.WebRTUtils;
import net.i2cat.seg.webrtcat4.stats.WebRTStats;
import net.i2cat.seg.webrtcat4.sampleapp.R;
import net.i2cat.seg.webrtcat4.sampleapp.WebRTCallControlFragment;
import net.i2cat.seg.webrtcat4.sampleapp.WebRTCatUserManager;
import net.i2cat.seg.webrtcat4.sampleapp.service.WebRTCatMessagingService;
import net.i2cat.seg.webrtcat4.sampleapp.util.WebRTConstants;

import org.appspot.apprtc.PercentFrameLayout;
import org.appspot.apprtc.UnhandledExceptionHandler;
import org.webrtc.SurfaceViewRenderer;

import java.text.DecimalFormat;

public class WebRTCallActivity extends Activity
    implements WebRTCat.WebRTCatCallbacks, WebRTCallControlFragment.WebRTCallControlEvents {

    // Properties copied from AppRTC CallActivity
    public static final String EXTRA_ROOMID =
            "org.appspot.apprtc.ROOMID";
    public static final String EXTRA_VIDEO_WIDTH =
            "org.appspot.apprtc.VIDEO_WIDTH";
    public static final String EXTRA_VIDEO_HEIGHT =
            "org.appspot.apprtc.VIDEO_HEIGHT";
    public static final String EXTRA_VIDEO_FPS =
            "org.appspot.apprtc.VIDEO_FPS";
    public static final String EXTRA_VIDEO_BITRATE =
            "org.appspot.apprtc.VIDEO_BITRATE";
    public static final String EXTRA_VIDEOCODEC =
            "org.appspot.apprtc.VIDEOCODEC";
    public static final String EXTRA_HWCODEC_ENABLED =
            "org.appspot.apprtc.HWCODEC";
    public static final String EXTRA_CAPTURETOTEXTURE_ENABLED =
            "org.appspot.apprtc.CAPTURETOTEXTURE";
    public static final String EXTRA_AUDIO_BITRATE =
            "org.appspot.apprtc.AUDIO_BITRATE";
    public static final String EXTRA_AUDIOCODEC =
            "org.appspot.apprtc.AUDIOCODEC";
    public static final String EXTRA_NOAUDIOPROCESSING_ENABLED =
            "org.appspot.apprtc.NOAUDIOPROCESSING";
    public static final String EXTRA_AECDUMP_ENABLED =
            "org.appspot.apprtc.AECDUMP";
    public static final String EXTRA_OPENSLES_ENABLED =
            "org.appspot.apprtc.OPENSLES";
    public static final String EXTRA_DISPLAY_HUD =
            "org.appspot.apprtc.DISPLAY_HUD";
    public static final String EXTRA_TRACING = "org.appspot.apprtc.TRACING";
    public static final String EXTRA_DISABLE_BUILT_IN_AEC =
            "org.appspot.apprtc.DISABLE_BUILT_IN_AEC";
    public static final String EXTRA_DISABLE_BUILT_IN_AGC =
            "org.appspot.apprtc.DISABLE_BUILT_IN_AGC";
    public static final String EXTRA_DISABLE_BUILT_IN_NS =
            "org.appspot.apprtc.DISABLE_BUILT_IN_NS";
    public static final String EXTRA_ENABLE_LEVEL_CONTROL =
            "org.appspot.apprtc.ENABLE_LEVEL_CONTROL";

    public static final String WEBRTCAT_PEER_NAME = "webrtcat_peerName";
    public static final String WEBRTCAT_CALLER_NAME = "webrtcat_callerName";
    public static final String WEBRTCAT_CALLEE_NAME = "webrtcat_calleeName";
    public static final String WEBRTCAT_NOTIF_TOKEN = "webrtcat_notifToken";
    public static final String WEBRTCAT_INCOMINGCALL = "webrtcat_incomingcall";

    private static final int MILLIS_IN_SEC = 1000;
    private static final int MILLIS_IN_MIN = 60*MILLIS_IN_SEC;
    private static final int MILLIS_IN_HOUR = 60*MILLIS_IN_MIN;

    // Controls
    private WebRTCallControlFragment callControlsFragment;
    private IncomingCallFragment incomingCallFragment;

    private WebRTCat webrtcat;
    private boolean callControlFragmentVisible;

    private String callerName;
    private String calleeName;
    private String notifToken;
    private boolean isCallConnected;
    private boolean isIncomingCall;

    public static void startIncomingCall(Context context, Bundle bundle) {
        String roomName = bundle.getString("roomName");
        if (roomName == null) {
            Log.e(WebRTConstants.LOG_TAG, "Attempt to start WebRTCallActivity without a roomname!");
            return;
        }
        String callerName = bundle.getString("callerName");
        // The callee in this case is this client itself.
        String calleeName = WebRTCatUserManager.getSavedUsername(context);

        startActivity(context, roomName, callerName, calleeName, null, true);
    }

    public static void startOutgoingCall(Context context, String roomId, String callerName,
                                         String calleeName, String calleeNotifToken) {
        startActivity(context, roomId, callerName, calleeName, calleeNotifToken, false);
    }

    private static void startActivity(Context context, String roomId, String callerName,
                                      String calleeName, String calleeNotifToken, boolean isIncomingCall) {
        // Create the Intent to start the CallActivity and set all the necessary parameters.
        Intent intent = new Intent(context, WebRTCallActivity.class);
        // Starting an Activity outside of an Activity requires this flag (otherwise you get an Exception)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // This flag prevents creating a new instance of the activity if it's already active on top of the history stack.
        // (see https://developer.android.com/guide/components/tasks-and-back-stack.html)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        Uri roomUri = Uri.parse(WebRTConstants.getWebRTCat4ServerURL());
        intent.setData(roomUri);

        // Get video resolution from settings.
        int videoWidth = 0;
        int videoHeight = 0;
        String resolution = context.getString(R.string.pref_resolution_default);
        String[] dimensions = resolution.split("[ x]+");
        if (dimensions.length == 2) {
            try {
                videoWidth = Integer.parseInt(dimensions[0]);
                videoHeight = Integer.parseInt(dimensions[1]);
            } catch (NumberFormatException e) {
                videoWidth = 0;
                videoHeight = 0;
                Log.e(WebRTConstants.LOG_TAG, "Wrong video resolution setting: " + resolution);
            }
        }

        // Get camera fps from settings.
        int cameraFps = 0;
        String fps = context.getString(R.string.pref_fps_default);
        String[] fpsValues = fps.split("[ x]+");
        if (fpsValues.length == 2) {
            try {
                cameraFps = Integer.parseInt(fpsValues[0]);
            } catch (NumberFormatException e) {
                Log.e(WebRTConstants.LOG_TAG, "Wrong camera fps setting: " + fps);
            }
        }

        // Get video and audio start bitrate.
        int videoStartBitrate = Integer.parseInt(context.getString(R.string.pref_startvideobitratevalue_default));
        int audioStartBitrate = Integer.parseInt(context.getString(R.string.pref_startaudiobitratevalue_default));

        intent.putExtra(WebRTCallActivity.EXTRA_ROOMID, roomId);
        intent.putExtra(WebRTCallActivity.EXTRA_VIDEO_WIDTH, videoWidth);
        intent.putExtra(WebRTCallActivity.EXTRA_VIDEO_HEIGHT, videoHeight);
        intent.putExtra(WebRTCallActivity.EXTRA_VIDEO_FPS, cameraFps);
        intent.putExtra(WebRTCallActivity.EXTRA_VIDEO_BITRATE, videoStartBitrate);
        intent.putExtra(WebRTCallActivity.EXTRA_VIDEOCODEC, context.getString(R.string.pref_videocodec_default));
        intent.putExtra(WebRTCallActivity.EXTRA_HWCODEC_ENABLED, Boolean.valueOf(context.getString(R.string.pref_hwcodec_default)));
        intent.putExtra(WebRTCallActivity.EXTRA_CAPTURETOTEXTURE_ENABLED, Boolean.valueOf(context.getString(R.string.pref_capturetotexture_default)));
        intent.putExtra(WebRTCallActivity.EXTRA_NOAUDIOPROCESSING_ENABLED, Boolean.valueOf(context.getString(R.string.pref_noaudioprocessing_default)));
        intent.putExtra(WebRTCallActivity.EXTRA_AECDUMP_ENABLED, Boolean.valueOf(context.getString(R.string.pref_aecdump_default)));
        intent.putExtra(WebRTCallActivity.EXTRA_OPENSLES_ENABLED, Boolean.valueOf(context.getString(R.string.pref_opensles_default)));
        intent.putExtra(WebRTCallActivity.EXTRA_AUDIO_BITRATE, audioStartBitrate);
        intent.putExtra(WebRTCallActivity.EXTRA_AUDIOCODEC, context.getString(R.string.pref_audiocodec_default));
        intent.putExtra(WebRTCallActivity.EXTRA_DISPLAY_HUD, Boolean.valueOf(context.getString(R.string.pref_displayhud_default)));
        intent.putExtra(WebRTCallActivity.EXTRA_TRACING, Boolean.valueOf(context.getString(R.string.pref_tracing_default)));
        intent.putExtra(WebRTCallActivity.EXTRA_DISABLE_BUILT_IN_AEC, true);
        intent.putExtra(WebRTCallActivity.EXTRA_DISABLE_BUILT_IN_AGC, true);
        intent.putExtra(WebRTCallActivity.EXTRA_DISABLE_BUILT_IN_NS, true);
        intent.putExtra(WebRTCallActivity.EXTRA_ENABLE_LEVEL_CONTROL, false);

        intent.putExtra(WEBRTCAT_INCOMINGCALL, isIncomingCall);
        intent.putExtra(WEBRTCAT_CALLER_NAME, callerName);
        intent.putExtra(WEBRTCAT_CALLEE_NAME, calleeName);
        intent.putExtra(WEBRTCAT_NOTIF_TOKEN, calleeNotifToken);
        if (isIncomingCall) {
            intent.putExtra(WEBRTCAT_PEER_NAME, callerName);
        } else {
            intent.putExtra(WEBRTCAT_PEER_NAME, calleeName);
        }

        Log.d(WebRTConstants.LOG_TAG, "Joining room " + roomId + " in " + roomUri);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

        if (!MainActivity.hasPermissions(this) && !MainActivity.hasWriteSettingsPermission(this)) {
            AndroidUtils.longToast(this, "Not all permissions were granted; can't receive calls.\n");
            finish();
            return;
        }

        // Set window styles for fullscreen-window size. Needs to be done before
        // adding content.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN
                            | LayoutParams.FLAG_KEEP_SCREEN_ON
                            | LayoutParams.FLAG_DISMISS_KEYGUARD
                            | LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_webrtcall);

        // Create fragment for call controls.
        callControlsFragment = new WebRTCallControlFragment();
        callControlsFragment.setCallControlEventsHandler(this);
        callControlsFragment.setArguments(getIntent().getExtras());

        // Show/hide call control fragment on view click.
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleCallControlFragmentVisibility();
            }
        };

        SurfaceViewRenderer localView = (SurfaceViewRenderer)findViewById(R.id.local_video_view);
        // Important: set to true in order for the local video view to always appear on top of the remote video view.
        localView.setZOrderMediaOverlay(true);

        localView.setOnClickListener(listener);
        findViewById(R.id.remote_video_view).setOnClickListener(listener);

        Intent intent = getIntent();
        isIncomingCall = intent.getBooleanExtra(WEBRTCAT_INCOMINGCALL, false);
        notifToken = intent.getStringExtra(WEBRTCAT_NOTIF_TOKEN);
        callerName = intent.getStringExtra(WEBRTCAT_CALLER_NAME);
        calleeName = intent.getStringExtra(WEBRTCAT_CALLEE_NAME);

        connectWebRTCat();
    }

    // Activity interfaces
    @Override
    public void onPause() {
        super.onPause();
        webrtcat.muteVideo();
    }

    @Override
    public void onResume() {
        super.onResume();
        webrtcat.unmuteVideo();
    }

    @Override
    protected void onDestroy() {
        webrtcat.disconnect(WebRTCat.DisconnectReason.CLIENT_SHUTDOWN);
        super.onDestroy();
    }

    // WebRTCallEvents interface implementation.
    @Override
    public void onCallHangUp() {
        // Hang up button in call fragment was pressed.
        // This will cause onHangup() to be called once the hangup code is done.
        webrtcat.hangup();
    }

    @Override
    public void onCameraSwitch() {
        webrtcat.switchCamera();
    }

    @Override
    public boolean onToggleAudioMute() {
        return webrtcat.toggleAudioMute();
    }

    // Helper functions.
    private void toggleCallControlFragmentVisibility() {
        // Show/hide call control fragment
        callControlFragmentVisible = !callControlFragmentVisible;
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (callControlFragmentVisible) {
            ft.show(callControlsFragment);
        } else {
            ft.hide(callControlsFragment);
        }
        // ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
    }

    private void connectWebRTCat() {
        webrtcat = new WebRTCat(this, this);

        String myUsername;
        if (isIncomingCall) {
            myUsername = calleeName;
        } else {
            myUsername = callerName;
        }
        webrtcat.setUsername(myUsername);

        // Get Intent parameters.
        final Intent intent = getIntent();
        Uri roomUri = intent.getData();
        if (roomUri == null) {
            Log.e(WebRTConstants.LOG_TAG, "Didn't get any URL in intent!");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        String roomId = intent.getStringExtra(WebRTCallActivity.EXTRA_ROOMID);
        if (roomId == null || roomId.length() == 0) {
            Log.e(WebRTConstants.LOG_TAG, "Incorrect room ID in intent!");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        WebRTCatParams webrtcatParams = new WebRTCatParams(roomUri.toString(), roomId);
        webrtcatParams.setAudioCodec(intent.getStringExtra(WebRTCallActivity.EXTRA_AUDIOCODEC));
        webrtcatParams.setAudioStartingBitrate(intent.getIntExtra(WebRTCallActivity.EXTRA_AUDIO_BITRATE, 0));
        webrtcatParams.setVideoCodec(intent.getStringExtra(WebRTCallActivity.EXTRA_VIDEOCODEC));
        webrtcatParams.setVideoWidth(intent.getIntExtra(WebRTCallActivity.EXTRA_VIDEO_WIDTH, 0));
        webrtcatParams.setVideoHeight(intent.getIntExtra(WebRTCallActivity.EXTRA_VIDEO_HEIGHT, 0));
        webrtcatParams.setVideoFps(intent.getIntExtra(WebRTCallActivity.EXTRA_VIDEO_FPS, 0));
        webrtcatParams.setVideoStartingBitrate(intent.getIntExtra(WebRTCallActivity.EXTRA_VIDEO_BITRATE, 0));

        webrtcat.connect(webrtcatParams);
    }

    @Override
    public boolean onRoomConnected(String roomId, boolean isInitiator) {
        Log.i(WebRTConstants.LOG_TAG, "*** CONNECTED TO ROOM " + roomId);
        if (isIncomingCall) {
            if (isInitiator) {
                // Invalid state: we expect to receive a call offer (because this activity was started
                // by a notification message) but instead signaling considers as the caller (initiator).
                Log.w(WebRTConstants.LOG_TAG, "Joined room " + roomId + " as callee but signaling thinks we're the caller! Disconnecting...");
                // Nothing else to do but finish this activity (we'll disconnect on onDestroy()).
                finish();
                return false;
            }
        } else {
            setWebRTCatViews(findViewById(android.R.id.content));
            webrtcat.call(calleeName);
            // Notify callee only when we are already in the room.
            WebRTCatMessagingService.sendNotification(this, notifToken, callerName, roomId);
        }
        return true;
    }

    @Override
    public void onIncomingCall() {
        // If we reach this point we already have the caller's offer SDP, so we can either
        // accept or reject this offer.
        incomingCallFragment = new IncomingCallFragment();
        incomingCallFragment.setCallerName(callerName);
        addFragment(R.id.incoming_call_fragment_container, incomingCallFragment);
    }

    @Override
    public void onIncomingCallCancelled() {
        // At this point the webrtcat object is already in disconnected state.
        String msg = "Missed call from Unknown caller";
        if (callerName != null) {
            msg = "Missed call from " + callerName;
        }
        AndroidUtils.longToast(this, msg);
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (incomingCallFragment == null) {
            return false;
        }

        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) || (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            incomingCallFragment.setRingtoneVolume(keyCode == KeyEvent.KEYCODE_VOLUME_UP);
            return true;
        }
        return false;
    }

    public void onAcceptCall() {
        removeFragment(incomingCallFragment);
        incomingCallFragment = null;
        setWebRTCatViews(findViewById(android.R.id.content));
        webrtcat.acceptCall();
    }

    public void onRejectCall() {
        removeFragment(incomingCallFragment);
        incomingCallFragment = null;
        webrtcat.rejectCall();
        // rejectCall() doesn't call onHangup() since we never picked up the call to begin with,
        // so just exit now.
        finish();
    }

    @Override
    public void onCallConnected() {
        isCallConnected = true;
        Log.i(WebRTConstants.LOG_TAG, "Call connected!");
    }

    @Override
    public void onCallOfferFailed() {
        // At this point the webrtcat object is already in disconnected state.
        AndroidUtils.longToast(this, calleeName + " is not available to take your call.");
        finish();
    }

    @Override
    public void onHangup() {
        // At this point the webrtcat object is already in disconnected state.
        Log.i(WebRTConstants.LOG_TAG, "Hangup reported, ending activity");
        showCallStats("Call completed.");
        finish();
    }

    @Override
    public void onStats(WebRTStats stats) {
        StringBuilder statsSB = new StringBuilder();
        long elapsedTimeMillis = System.currentTimeMillis() - stats.getCallStartTime();
        if (stats.getCallStartTime() != null) {
            String elapsedTimeStr = formatDuration(elapsedTimeMillis, false);
            statsSB.append("Call duration: ").append(elapsedTimeStr).append("\n");
        }
        statsSB.append(stats.toString(elapsedTimeMillis));
        // Log.i("WebRTStat", statsSB.toString());
        callControlsFragment.setLatestCallInfo(statsSB.toString());
    }

    @Override
    public void onError(WebRTCatErrorCode errorCode) {
        // By the time onError() is called the webrtcat client is already disconnected
        if (isCallConnected) {
            if (webrtcat.getLastStats() != null) {
                showCallStats("Call was dropped (" + errorCode + ")");
            } else {
                AndroidUtils.longToast(this, "Call was dropped (" + errorCode + ")");
            }
        } else {
            AndroidUtils.longToast(this, "We're sorry, your call could not be completed at this time. Please try again later.\n" +
                                         errorCode);
        }
        // Nothing more to do here but end the activity.
        finish();
    }

    private void setWebRTCatViews(View parentView) {
        SurfaceViewRenderer localRender = (SurfaceViewRenderer) parentView.findViewById(R.id.local_video_view);
        SurfaceViewRenderer remoteRender = (SurfaceViewRenderer) parentView.findViewById(R.id.remote_video_view);
        PercentFrameLayout localRenderLayout = (PercentFrameLayout) parentView.findViewById(R.id.local_video_layout);
        PercentFrameLayout remoteRenderLayout = (PercentFrameLayout) parentView.findViewById(R.id.remote_video_layout);
        webrtcat.setViews(localRender, remoteRender, localRenderLayout, remoteRenderLayout);
        addFragment(R.id.call_fragment_container, callControlsFragment);
    }

    private void addFragment(final int containerId, final Fragment fragment) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try  {
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.add(containerId, fragment);
                    ft.commitAllowingStateLoss();
                } catch (Exception e) {
                    Log.e(WebRTConstants.LOG_TAG, "Unable to add fragment", e);
                }
            }
        });
    }

    private void removeFragment(final Fragment fragment) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.remove(fragment);
                    ft.commitAllowingStateLoss();
                } catch (Exception e) {
                    Log.e(WebRTConstants.LOG_TAG, "Unable to remove fragment", e);
                }
            }
        });
    }

    private void showCallStats(String messagePrefix) {
        WebRTStats lastStats = webrtcat.getLastStats();
        if ((lastStats != null) && (lastStats.getCallStartTime() != null)) {
            long duration = System.currentTimeMillis() - lastStats.getCallStartTime();
            StringBuilder callDurationSB = new StringBuilder(messagePrefix);
            callDurationSB.append("\nTotal call time was ").append(formatDuration(duration, true));

            double durationInSecs = duration / MILLIS_IN_SEC;
            // Compute total data consumed and average outgoing bitrate.
            long totalBytes = lastStats.getTotalAudioBytes() + lastStats.getTotalVideoBytes();
            callDurationSB.append("\nData consumed (in+out): ").append(WebRTUtils.formatBytes(totalBytes));
            callDurationSB.append("\nAvg in  video bitrate : ").append(WebRTUtils.formatBitratePerSecond(lastStats.getInVideoStatsBytes(), durationInSecs));
            callDurationSB.append("\nAvg out video bitrate : ").append(WebRTUtils.formatBitratePerSecond(lastStats.getOutVideoStatsBytes(), durationInSecs));
            callDurationSB.append("\nAvg in  audio bitrate : ").append(WebRTUtils.formatBitratePerSecond(lastStats.getInAudioStatsBytes(), durationInSecs));
            callDurationSB.append("\nAvg out audio bitrate : ").append(WebRTUtils.formatBitratePerSecond(lastStats.getOutAudioStatsBytes(), durationInSecs));
            Log.i("WebRTStat", callDurationSB.toString());
            AndroidUtils.longToast(this, callDurationSB.toString());
        }
    }

    private String formatDuration(long duration, boolean isLongFormat) {
        StringBuilder sb = new StringBuilder();
        int hours = (int)Math.floor(duration / MILLIS_IN_HOUR);
        String hourDur = formatDuration(hours, isLongFormat, "hour", "hours");
        duration = duration % MILLIS_IN_HOUR;

        int mins = (int)Math.floor(duration / MILLIS_IN_MIN);
        String minDur = formatDuration(mins, isLongFormat, "minute", "minutes");
        duration = duration % MILLIS_IN_MIN;

        int secs =  (int)Math.floor(duration / MILLIS_IN_SEC);
        String secDur = formatDuration(secs, isLongFormat, "second", "seconds");

        if ((secDur != null) || (minDur != null) || (hourDur != null)) {
            if (isLongFormat) {
                if (hourDur != null) {
                    sb.append(hourDur);
                }
                if (minDur != null) {
                    if (hourDur != null) {
                        sb.append(" ");
                    }
                    sb.append(minDur);
                }
                if (secDur != null) {
                    if ((hourDur != null) || (minDur != null)) {
                        sb.append(" ");
                    }
                    sb.append(secDur);
                }
            } else {
                sb.append(hourDur).append(":").append(minDur).append(":").append(secDur);
            }
        }
        return sb.toString();
    }


    private String formatDuration(int duration, boolean isLongFormat, String singular, String plural) {
        String durStr = null;
        if (isLongFormat) {
            if (duration > 0) {
                durStr = Integer.toString(duration);
                if (duration > 1) {
                    durStr += " " + plural;
                } else {
                    durStr += " " + singular;
                }
            }
        } else {
            DecimalFormat df = new DecimalFormat("00");
            durStr = df.format(duration);
        }
        return durStr;
    }
}
