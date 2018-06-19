package net.i2cat.seg.webrtcat4;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;

import net.i2cat.seg.webrtcat4.stats.WebRTCAudioStats;
import net.i2cat.seg.webrtcat4.stats.WebRTCMediaStats;
import net.i2cat.seg.webrtcat4.stats.WebRTCVideoStats;
import net.i2cat.seg.webrtcat4.stats.WebRTStats;

import org.appspot.apprtc.AppRTCAudioManager;
import org.appspot.apprtc.AppRTCClient;
import org.appspot.apprtc.AppRTCClient.RoomConnectionParameters;
import org.appspot.apprtc.AppRTCClient.SignalingParameters;
import org.appspot.apprtc.PercentFrameLayout;
import org.appspot.apprtc.util.LooperExecutor;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WebRTCat {

    private static final String TAG = "WebRTCat";

    // Peer connection statistics callback period in ms.
    private static final int STAT_CALLBACK_PERIOD = 1000;
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;

    public enum WebRTCatState {
        DISCONNECTED,
        READY,
        CALLING,
        IN_CALL_OFFER,
        IN_CALL
    }

    public enum DisconnectReason {
        HANGUP,
        CLIENT_SHUTDOWN,
        ICE_DISCONNECT,
        CHANNEL_ERROR,
        CALL_REJECT
    }

    private Activity owningActivity;
    private WebRTCatPeerConnectionClient peerConnectionClient = null;
    private AppRTCClient appRtcClient;
    private SignalingParameters signalingParameters;
    private AppRTCAudioManager audioManager = null;
    private EglBase rootEglBase;
    private SurfaceViewRenderer localRender;
    private SurfaceViewRenderer remoteRender;
    private PercentFrameLayout localRenderLayout;
    private PercentFrameLayout remoteRenderLayout;
    private RoomConnectionParameters roomConnectionParameters;
    private boolean iceConnected;
    private boolean isError;
    private long callStartedTimeMs = 0;
    // The current state of this object. This should not be referenced directly;
    // instead use getState() to read or transitionState() to write
    private WebRTCatState webrtcatState;
    private String username;
    private String destClientName;
    private boolean isAudioEnabled = true;
    private Executor cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    private Long lastCallStartTime;
    private WebRTStats lastStats;
    // If true, indicates that this object was connected (i.e. was in state=READY) at least once.
    // Used to distinguish between legitimate programming errors or callbacks being invoked after we have already disconnected, for example.
    private boolean wasConnected;

    public interface WebRTCatCallbacks {
        boolean onRoomConnected(String roomId,          // Client has entered the room -- can place call.
                                boolean isInitiator);   // isInitiator is true if this client is the first client in the room.
        void onIncomingCall();                          // Client has incoming call -- can accept or reject it.
        void onIncomingCallCancelled();                 // Caller has abandoned the call offer.
        void onCallConnected();                         // Call connected successfully.
        void onCallOfferFailed();                       // Call offer could not be completed (e.g. callee rejected the call)
        void onHangup();                                // Call was disconnected because of hangup() call by either party
        void onStats(WebRTStats stats);                 // Statistics about the call are available.
        void onError(WebRTCatErrorCode errCode);        // Error was encountered while setting up or during the call.
    }

    private WebRTCatCallbacks callbacks;

    public WebRTCat(Activity owningActivity, WebRTCatCallbacks callbacks) {
        this.owningActivity = owningActivity;
        this.callbacks = callbacks;
        this.webrtcatState = WebRTCatState.DISCONNECTED;
    }

    public void muteVideo() {
        if (peerConnectionClient != null) {
            peerConnectionClient.stopVideoSource();
        }
    }

    public void unmuteVideo() {
        if (peerConnectionClient != null) {
            peerConnectionClient.startVideoSource();
        }
    }

    public boolean toggleAudioMute() {
        if (peerConnectionClient != null) {
            isAudioEnabled = !isAudioEnabled;
            peerConnectionClient.setAudioEnabled(isAudioEnabled);
        }
        return isAudioEnabled;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @deprecated use connect(WebRTCatParams) instead.
     */
    @Deprecated
    public void connect(Uri roomUri, String roomId,
                        WebRTCatPeerConnectionClient.PeerConnectionParameters peerConnectionParameters) {
        WebRTCatParams params = new WebRTCatParams(roomUri.toString(), roomId);
        params.setAudioCodec(peerConnectionParameters.audioCodec);
        params.setAudioStartingBitrate(peerConnectionParameters.audioStartBitrate);
        params.setVideoCodec(peerConnectionParameters.videoCodec);
        params.setVideoFps(peerConnectionParameters.videoFps);
        params.setVideoHeight(peerConnectionParameters.videoHeight);
        params.setVideoWidth(peerConnectionParameters.videoWidth);
        params.setVideoStartingBitrate(peerConnectionParameters.videoStartBitrate);
        connect(params);
    }

    public void connect(WebRTCatParams webrtcatParams) {
        if (getState() != WebRTCatState.DISCONNECTED) {
            Log.e(TAG, "This method can only be called in state " + WebRTCatState.DISCONNECTED + " (current state is " + getState() + ")");
            callbacks.onError(WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
        }
        iceConnected = false;
        signalingParameters = null;
        rootEglBase = EglBase.create();

        audioManager = AppRTCAudioManager.create(owningActivity, new Runnable() {
                // This method will be called each time the audio state (number and
                // type of devices) has been changed.
                @Override
                public void run() {
                    // We don't do anything for now.
                }
            }
        );
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Initializing the audio manager...");
        audioManager.init();

        // Create connection client and connection parameters.
        appRtcClient = new WebRTCatClient(new SignallingEventHandler(), new LooperExecutor(), username);
        roomConnectionParameters = new RoomConnectionParameters(webrtcatParams.getRoomServerUri(),
                                                                webrtcatParams.getRoomName(),
                                                                false);

        peerConnectionClient = WebRTCatPeerConnectionClient.getInstance();
        peerConnectionClient.createPeerConnectionFactory(owningActivity, webrtcatParams.getPCParameters(),
                                                         new PeerConnectionEventHandler());
        isAudioEnabled = true;

        callStartedTimeMs = System.currentTimeMillis();

        // Start room connection.
        appRtcClient.connectToRoom(roomConnectionParameters);
    }

    public void disconnect(final DisconnectReason reason) {
        // Perform disconnect asynchronously.
        cleanupExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    long t = System.currentTimeMillis();
                    Log.d(TAG, "Begin async disconnect");
                    if (appRtcClient != null) {
                        ((WebRTCatClient)appRtcClient).setDisconnectReason(reason);
                        ((WebRTCatClient)appRtcClient).setLastStats(lastStats);
                        appRtcClient.disconnectFromRoom();
                        appRtcClient = null;
                    }
                    Log.d(TAG, "appRtcClient.disconnectFromRoom(): " + (System.currentTimeMillis() - t));
                    if (peerConnectionClient != null) {
                        peerConnectionClient.close();
                        peerConnectionClient = null;
                    }
                    Log.d(TAG, "peerConnectionClient.close(): " + (System.currentTimeMillis() - t));
                    if (localRender != null) {
                        localRender.release();
                        localRender = null;
                    }
                    Log.d(TAG, "localRender.release(): " + (System.currentTimeMillis() - t));
                    if (remoteRender != null) {
                        remoteRender.release();
                        remoteRender = null;
                    }
                    Log.d(TAG, "remoteRender.release(): " + (System.currentTimeMillis() - t));
                    if (audioManager != null) {
                        audioManager.close();
                        audioManager = null;
                    }
                    Log.d(TAG, "audioManager.close(): " + (System.currentTimeMillis() - t));
                    if (rootEglBase != null) {
                        rootEglBase.release();
                        rootEglBase = null;
                    }
                    Log.d(TAG, "rootEglBase.release(): " + (System.currentTimeMillis() - t));
                } catch(Exception e) {
                    Log.e(TAG, "Exception on disconnect(): ", e);
                }
            }
        });
        transitionState(WebRTCatState.DISCONNECTED);
    }

    public void hangup() {
        // TODO currently hanging up a call results in closing the apprtc client including (ws connection) -- look into hangup() only closing the peerconnection but leaving the client connected?
        disconnect(DisconnectReason.HANGUP);
        isAudioEnabled = true;
        callbacks.onHangup();
    }

    public void switchCamera() {
        if (peerConnectionClient != null) {
            peerConnectionClient.switchCamera();
        }
    }

    public void setViews(SurfaceViewRenderer localRender, SurfaceViewRenderer remoteRender,
                         PercentFrameLayout localRenderLayout, PercentFrameLayout remoteRenderLayout) {
        if (getState() == WebRTCatState.DISCONNECTED) {
            if (!wasConnected()) {
                Log.e(TAG, "This method can not be called in state " + WebRTCatState.DISCONNECTED);
                callbacks.onError(WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
            }
            return;
        }

        if ((peerConnectionClient == null) || (rootEglBase == null)) {
            if (!wasConnected()) {
                Log.e(TAG, "Can't set views with uninitialized PeerConnectionClient and/or EglBase");
                callbacks.onError(WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
            }
            return;
        }

        this.localRender = localRender;
        this.remoteRender = remoteRender;
        this.localRenderLayout = localRenderLayout;
        this.remoteRenderLayout = remoteRenderLayout;

        // Create video renderers.
        localRender.init(rootEglBase.getEglBaseContext(), null);
        remoteRender.init(rootEglBase.getEglBaseContext(), null);

        peerConnectionClient.setVideoRenderers(localRender, remoteRender);

        owningActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateVideoView();
            }
        });
    }

    // Must call webrtcat.connect() first
    public void call(String destClientName) {
        if (getState() != WebRTCatState.READY) {
            if (!wasConnected()) {
                Log.e(TAG, "This method can only be called in state " + WebRTCatState.READY + " (current state is " + getState() + ")");
                callbacks.onError(WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
            }
            return;
        }

        if (peerConnectionClient == null) {
            if (!wasConnected()) {
                Log.e(TAG, "Can't set views with uninitialized PeerConnectionClient");
                callbacks.onError(WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
            }
            return;
        }

        this.destClientName = destClientName;
        peerConnectionClient.addLocalMediaStream(rootEglBase.getEglBaseContext());
        // Create offer. Offer SDP will be sent to answering client in
        // PeerConnectionEvents.onLocalDescription event.
        Log.i(TAG, "Creating OFFER...");
        peerConnectionClient.createOffer();
        transitionState(WebRTCatState.CALLING);
    }

    public void acceptCall() {
        if (getState() != WebRTCatState.IN_CALL_OFFER) {
            if (!wasConnected()) {
                Log.e(TAG, "This method can only be called in state " + WebRTCatState.IN_CALL_OFFER + " (current state is " + getState() + ")");
                callbacks.onError(WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
            }
            return;
        }

        if (peerConnectionClient == null) {
            if (!wasConnected()) {
                Log.e(TAG, "Can't set views with uninitialized PeerConnectionClient");
                callbacks.onError(WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
            }
            return;
        }

        peerConnectionClient.addLocalMediaStream(rootEglBase.getEglBaseContext());
        // Create answer. Answer SDP will be sent to offering client in
        // PeerConnectionEvents.onLocalDescription event.
        Log.i(TAG, "Creating ANSWER...");
        peerConnectionClient.createAnswer();
    }

    public void rejectCall() {
        if (getState() != WebRTCatState.IN_CALL_OFFER) {
            if (!wasConnected()) {
                Log.e(TAG, "This method can only be called in state " + WebRTCatState.IN_CALL_OFFER + " (current state is " + getState() + ")");
                callbacks.onError(WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
            }
            return;
        }

        disconnect(DisconnectReason.CALL_REJECT);
    }

    public WebRTStats getLastStats() {
        return lastStats;
    }

    public synchronized WebRTCatState getState() { return webrtcatState; }

    // Only this object can set its state.
    private synchronized void transitionState(WebRTCatState newState) {
        Log.i(TAG, "WebRTCat state: " + webrtcatState + " -> " + newState);
        webrtcatState = newState;
        if (newState == WebRTCatState.READY) {
            wasConnected = true;
        }
    }

    private synchronized boolean wasConnected() {
        return wasConnected;
    }

    private void updateVideoView() {
        if ((remoteRender != null) && (remoteRenderLayout != null)) {
            remoteRenderLayout.setPosition(REMOTE_X, REMOTE_Y, REMOTE_WIDTH, REMOTE_HEIGHT);
            remoteRender.setScalingType(ScalingType.SCALE_ASPECT_FIT);
            remoteRender.setMirror(false);
        }

        if ((localRender != null) && (localRenderLayout != null)) {
            if (iceConnected) {
                localRenderLayout.setPosition(LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                        LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED);
                localRender.setScalingType(ScalingType.SCALE_ASPECT_FIT);
            } else {
                localRenderLayout.setPosition(LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                        LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING);
                localRender.setScalingType(ScalingType.SCALE_ASPECT_FIT);
            }
            localRender.setMirror(true);
        }

        if ((localRender != null) && (remoteRender != null)) {
            localRender.requestLayout();
            remoteRender.requestLayout();
        }
    }

    // Should be called from UI thread
    private void callConnected() {
        owningActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                transitionState(WebRTCatState.IN_CALL);
                final long delta = System.currentTimeMillis() - callStartedTimeMs;
                Log.i(TAG, "Call connected: delay=" + delta + "ms");
                if (peerConnectionClient == null || isError) {
                    Log.w(TAG, "Call is connected in closed or error state");
                    return;
                }
                // Update video view.
                updateVideoView();
                // Enable statistics callback.
                peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);
                lastCallStartTime = System.currentTimeMillis();
                callbacks.onCallConnected();
            }
        });
    }

    private void reportError(String description, WebRTCatErrorCode errorCode) {
        isError = true;
        Log.e(TAG, description);
        // Our error messages are considered implementation details and are not leaked to outside code
        // (only to logs), so we only surface the error code.
        callbacks.onError(errorCode);
    }

    // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
    private class SignallingEventHandler implements WebRTCatClient.SignalingEvents {
        @Override
        public void onConnectedToRoom(final SignalingParameters params) {
            transitionState(WebRTCatState.READY);

            final long delta = System.currentTimeMillis() - callStartedTimeMs;

            signalingParameters = params;
            // Create peer connection for the caller (initiator)
            Log.i(TAG, "Creating peer connection, delay=" + delta + "ms");
            // We'll set the local and remote renderers in a later call.
            peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(),
                    null, null, signalingParameters);

            if (callbacks.onRoomConnected(roomConnectionParameters.roomId, signalingParameters.initiator)) {
                // Signalling considers the first user in the room as the initiator.
                if (!signalingParameters.initiator) {
                    if (params.iceCandidates != null) {
                        // Add remote ICE candidates from room.
                        for (IceCandidate iceCandidate : params.iceCandidates) {
                            Log.i(TAG, "Adding REMOTE ICE candidate: " + iceCandidate);
                            peerConnectionClient.addRemoteIceCandidate(iceCandidate);
                        }
                    }
                    if (params.offerSdp != null) {
                        peerConnectionClient.setRemoteDescription(params.offerSdp);
                        // We now have the caller's official call offer -- notify client of the incoming call.
                        transitionState(WebRTCatState.IN_CALL_OFFER);
                        callbacks.onIncomingCall();
                    }
                }
            }
        }

        @Override
        public void onRemoteDescription(final SessionDescription sdp) {
            final long delta = System.currentTimeMillis() - callStartedTimeMs;
            if (peerConnectionClient == null) {
                Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
                return;
            }
            Log.i(TAG, "Received remote " + sdp.type + ", delay=" + delta + "ms");
            peerConnectionClient.setRemoteDescription(sdp);
            if (!signalingParameters.initiator) {
                // We now have the caller's remote description -- notify client of the incoming call.
                transitionState(WebRTCatState.IN_CALL_OFFER);
                callbacks.onIncomingCall();
            }
        }

        @Override
        public void onRemoteIceCandidate(final IceCandidate candidate) {
            if (peerConnectionClient == null) {
                Log.e(TAG, "Received ICE candidate for non-initilized peer connection.");
                return;
            }
            Log.i(TAG, "Adding REMOTE ICE candidate: " + candidate);
            peerConnectionClient.addRemoteIceCandidate(candidate);
        }

        @Override
        public void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates) {
            if (peerConnectionClient == null) {
                Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
                return;
            }
            peerConnectionClient.removeRemoteIceCandidates(candidates);
        }

        @Override
        public void onChannelClose() {
            Log.i(TAG, "Received remote hangup");
            WebRTCatState prevState = getState();
            disconnect(DisconnectReason.HANGUP);
            if (prevState == WebRTCatState.CALLING) {
                callbacks.onCallOfferFailed();
            } else if (prevState == WebRTCatState.IN_CALL_OFFER) {
                callbacks.onIncomingCallCancelled();
            } else {
                callbacks.onHangup();
            }
        }

        @Override
        public void onChannelError(final String description) {
            onChannelError(description, WebRTCatErrorCode.GENERAL_ERROR);
        }

        @Override
        public void onChannelError(String description, WebRTCatErrorCode errorCode) {
            WebRTCatState prevState = getState();
            disconnect(DisconnectReason.CHANNEL_ERROR);
            if ((prevState == WebRTCatState.CALLING) && (description.contains("RESPONSE_CALLEE_BUSY"))) {
                callbacks.onCallOfferFailed();
            } else {
                reportError(description, errorCode);
            }
        }
    }


    // -----Implementation of WebRTCatPeerConnectionClient.PeerConnectionEvents.---------
    private class PeerConnectionEventHandler implements WebRTCatPeerConnectionClient.PeerConnectionEvents {
        // Send local peer connection SDP and ICE candidates to remote party.
        @Override
        public void onLocalDescription(final SessionDescription sdp) {
            final long delta = System.currentTimeMillis() - callStartedTimeMs;
            if (appRtcClient != null) {
                Log.i(TAG, "Sending " + sdp.type + ", delay=" + delta + "ms");
                if (signalingParameters.initiator) {
                    ((WebRTCatClient)appRtcClient).sendOfferSdp(sdp, destClientName);
                } else {
                    appRtcClient.sendAnswerSdp(sdp);
                }
            }
        }

        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            if (appRtcClient != null) {
                Log.i(TAG, "Sending LOCAL ICE candidate: " + candidate);
                appRtcClient.sendLocalIceCandidate(candidate);
            }
        }

        public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
            if (appRtcClient != null) {
                appRtcClient.sendLocalIceCandidateRemovals(candidates);
            }
        }

        @Override
        public void onIceConnected() {
            final long delta = System.currentTimeMillis() - callStartedTimeMs;
            Log.i(TAG, "ICE connected, delay=" + delta + "ms");
            iceConnected = true;
            callConnected();
        }

        @Override
        public void onIceFailed() {
            reportError("ICE connection failed", WebRTCatErrorCode.ICE_CONNECTION_FAILED);
        }

        @Override
        public void onIceDisconnected() {
            Log.i(TAG, "ICE disconnected");
            iceConnected = false;
            disconnect(DisconnectReason.ICE_DISCONNECT);
            callbacks.onError(WebRTCatErrorCode.ICE_CONNECTION_FAILED);
        }

        @Override
        public void onPeerConnectionClosed() {
        }

        @Override
        public void onPeerConnectionStatsReady(final StatsReport[] reports) {
            WebRTStats stats = new WebRTStats();
            stats.setCallStartTime(lastCallStartTime);
            for (StatsReport report : reports) {
                Log.i(TAG, "*** STAT_REPORT: " + report.toString());

                if ("googCandidatePair".equals(report.type)) {
                    // Look for the "googActiveConnection=true" value to determine
                    // how the peers are connected.
                    if (getBooleanStatsReportValue(report.values, "googActiveConnection")) {
                        stats.setLocalConnectionType(translateCandidateType(getStatsReportValue(report.values, "googLocalCandidateType")));
                        stats.setLocalAddress(getStatsReportValue(report.values, "googLocalAddress"));
                        stats.setRemoteConnectionType(translateCandidateType(getStatsReportValue(report.values, "googRemoteCandidateType")));
                        stats.setRemoteAddress(getStatsReportValue(report.values, "googRemoteAddress"));
                        stats.setTransportType(getStatsReportValue(report.values, "googTransportType"));
                    }
                } else if ("ssrc".equals(report.type)) {
                    String codec = getStatsReportValue(report.values, "googCodecName");
                    Long bytesSent = getLongStatsReportValue(report.values, "bytesSent");
                    Long bytesReceived = getLongStatsReportValue(report.values, "bytesReceived");
                    Long packetsLost = getLongStatsReportValue(report.values, "packetsLost");
                    Long packetsSent = getLongStatsReportValue(report.values, "packetsSent");
                    Long packetsRecvd = getLongStatsReportValue(report.values, "packetsReceived");

                    if ("audio".equals(getStatsReportValue(report.values, "mediaType"))) {
                        WebRTCAudioStats audioStats = null;
                        if (bytesSent != null) {
                            // Outgoing audio
                            /* type: ssrc, timestamp: 1.474538353234613E12,
                               values: [audioInputLevel: 30], [bytesSent: 3330715], [mediaType: audio],
                                       [packetsLost: 42], [packetsSent: 34701], [ssrc: 2315521386],
                                       [transportId: Channel-audio-1], [googCodecName: opus],
                                       [googEchoCancellationQualityMin: -1], [googEchoCancellationEchoDelayMedian: -1],
                                       [googEchoCancellationEchoDelayStdDev: -1], [googEchoCancellationReturnLoss: -100],
                                       [googEchoCancellationReturnLossEnhancement: -100], [googJitterReceived: 10],
                                       [googRtt: 22], [googTrackId: ARDAMSa0], [googTypingNoiseState: false] */
                            audioStats = new WebRTCAudioStats(WebRTCMediaStats.MediaDirection.OUTGOING);
                            audioStats.setBytes(bytesSent);
                            audioStats.setPacketsLost(packetsLost);
                            audioStats.setPacketsSentRecvd(packetsSent);
                            stats.setOutAudioStats(audioStats);
                        } else if (bytesReceived != null) {
                            // Incoming audio
                            /* type: ssrc, timestamp: 1.474538353234613E12,
                               values: [audioOutputLevel: 32], [bytesReceived: 2988899], [mediaType: audio],
                               [packetsLost: 143], [packetsReceived: 34554], [ssrc: 379483459],
                               [transportId: Channel-audio-1], [googAccelerateRate: 0],
                               [googCaptureStartNtpTimeMs: 3683526459302],
                               [googCodecName: opus], [googCurrentDelayMs: 277], [googDecodingCNG: 0],
                               [googDecodingCTN: 69533], [googDecodingCTSG: 0], [googDecodingNormal: 68620],
                               [googDecodingPLC: 653], [googDecodingPLCCNG: 260], [googExpandRate: 0],
                               [googJitterBufferMs: 208], [googJitterReceived: 9], [googPreemptiveExpandRate: 0],
                               [googPreferredJitterBufferMs: 200], [googSecondaryDecodedRate: 0],
                               [googSpeechExpandRate: 0], [googTrackId: ARDAMSa0]
                             */
                            audioStats = new WebRTCAudioStats(WebRTCMediaStats.MediaDirection.INCOMING);
                            audioStats.setBytes(bytesReceived);
                            audioStats.setPacketsLost(packetsLost);
                            audioStats.setPacketsSentRecvd(packetsRecvd);
                            stats.setInAudioStats(audioStats);
                        }
                        if (audioStats != null) {
                            audioStats.setCodec(codec);
                        }
                    } else {
                        WebRTCVideoStats videoStats = null;
                        if (bytesSent != null) {
                            // Outgoing video
                            /* type: ssrc, timestamp: 1.474538353234613E12,
                            * values: [bytesSent: 66695634], [codecImplementationName: libvpx],
                            * [mediaType: video], [packetsLost: 105], [packetsSent: 63088],
                            * [ssrc: 1016918769], [transportId: Channel-audio-1], [googAdaptationChanges: 3],
                            * [googAvgEncodeMs: 24], [googBandwidthLimitedResolution: false],
                            * [googCodecName: VP8], [googCpuLimitedResolution: true],
                            * [googEncodeUsagePercent: 54], [googFirsReceived: 0],
                            * [googFrameHeightInput: 480], [googFrameHeightSent: 360],
                            * [googFrameRateInput: 20], [googFrameRateSent: 20], [googFrameWidthInput: 640],
                            * [googFrameWidthSent: 480], [googNacksReceived: 62], [googPlisReceived: 8],
                            * [googRtt: 27], [googTrackId: ARDAMSv0], [googViewLimitedResolution: false]
                            * */
                            videoStats = new WebRTCVideoStats(WebRTCMediaStats.MediaDirection.OUTGOING);
                            videoStats.setBytes(bytesSent);
                            videoStats.setPacketsLost(packetsLost);
                            videoStats.setPacketsSentRecvd(packetsSent);
                            videoStats.setFrameHeight(getIntegerStatsReportValue(report.values, "googFrameHeightSent"));
                            videoStats.setFrameWidth(getIntegerStatsReportValue(report.values, "googFrameWidthSent"));
                            videoStats.setFrameRate(getIntegerStatsReportValue(report.values, "googFrameRateSent"));
                            stats.setOutVideoStats(videoStats);
                        } else if (bytesReceived != null) {
                            // Incoming video
                            /* type: ssrc, timestamp: 1.474538353234613E12,
                               values: [bytesReceived: 175463036], [codecImplementationName: MediaCodec],
                               [mediaType: video], [packetsLost: 463], [packetsReceived: 160126],
                               [ssrc: 3799983763], [transportId: Channel-audio-1], [googCaptureStartNtpTimeMs: 3683526458992],
                               [googCodecName: VP8], [googCurrentDelayMs: 148], [googDecodeMs: 20], [googFirsSent: 0],
                               [googFrameHeightReceived: 1280], [googFrameRateDecoded: 31], [googFrameRateOutput: 31],
                               [googFrameRateReceived: 30], [googFrameWidthReceived: 720], [googJitterBufferMs: 100],
                               [googMaxDecodeMs: 38], [googMinPlayoutDelayMs: 41], [googNacksSent: 241], [googPlisSent: 0],
                               [googRenderDelayMs: 10], [googTargetDelayMs: 148], [googTrackId: ARDAMSv0]
                             */
                            videoStats = new WebRTCVideoStats(WebRTCMediaStats.MediaDirection.INCOMING);
                            videoStats.setBytes(bytesReceived);
                            videoStats.setPacketsLost(packetsLost);
                            videoStats.setPacketsSentRecvd(packetsRecvd);
                            videoStats.setFrameHeight(getIntegerStatsReportValue(report.values, "googFrameHeightReceived"));
                            videoStats.setFrameWidth(getIntegerStatsReportValue(report.values, "googFrameWidthReceived"));
                            videoStats.setFrameRate(getIntegerStatsReportValue(report.values, "googFrameRateReceived"));
                            stats.setInVideoStats(videoStats);
                        }
                        if (videoStats != null) {
                            videoStats.setCodec(codec);
                        }
                    }
                }
            }
            lastStats = stats;
            callbacks.onStats(stats);
        }

        @Override
        public void onPeerConnectionError(final String description) {
            // This is called for a bunch of possible reasons: failed to create peer connection or SDP,
            // weird media stream was received, etc.
            reportError(description, WebRTCatErrorCode.GENERAL_ERROR);
        }

        private String translateCandidateType(String googCandidateType) {
            switch (googCandidateType) {
                case "local":
                    return "host";
                case "stun":
                    return "srflx";
            }
            return googCandidateType;
        }

        private Integer getIntegerStatsReportValue(StatsReport.Value values[], String name) {
            String value = getStatsReportValue(values, name);
            if (value != null) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    // Ignored.
                }
            }
            return null;
        }

        private Long getLongStatsReportValue(StatsReport.Value values[], String name) {
            String value = getStatsReportValue(values, name);
            if (value != null) {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    // Ignored.
                }
            }
            return null;
        }

        private Boolean getBooleanStatsReportValue(StatsReport.Value values[], String name) {
            return "true".equals(getStatsReportValue(values, name));
        }

        private String getStatsReportValue(StatsReport.Value values[], String name) {
            for (StatsReport.Value v : values) {
                if (name.equals(v.name)) {
                    return v.value;
                }
            }
            return null;
        }
    }
}
