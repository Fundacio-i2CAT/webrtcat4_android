package net.i2cat.seg.webrtcat4;

public class WebRTCatParams {
    private String roomServerUri;
    private String roomName;
    private String videoCodec = "VP8";
    private int videoWidth = 0;                 // Use AppRTC default.
    private int videoHeight = 0;                // Use AppRTC default.
    private int videoFps = 0;                   // Use AppRTC default.
    private int videoStartingBitrate = 1024;    // in kbps
    private String audioCodec = "OPUS";
    private int audioStartingBitrate = 32;      // in kbps

    public WebRTCatParams(String roomServerUri, String roomName) {
        this.roomServerUri = roomServerUri;
        this.roomName = roomName;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getRoomServerUri() {
        return roomServerUri;
    }

    public void setAudioCodec(String audioCodec) {
        this.audioCodec = audioCodec;
    }

    public void setAudioStartingBitrate(int audioStartingBitrate) {
        this.audioStartingBitrate = audioStartingBitrate;
    }

    public void setVideoCodec(String videoCodec) {
        this.videoCodec = videoCodec;
    }

    public void setVideoFps(int videoFps) {
        this.videoFps = videoFps;
    }

    public void setVideoHeight(int videoHeight) {
        this.videoHeight = videoHeight;
    }

    public void setVideoStartingBitrate(int videoStartingBitrate) {
        this.videoStartingBitrate = videoStartingBitrate;
    }

    public void setVideoWidth(int videoWidth) {
        this.videoWidth = videoWidth;
    }

    public WebRTCatPeerConnectionClient.PeerConnectionParameters getPCParameters() {
        return new WebRTCatPeerConnectionClient.PeerConnectionParameters(
                true,           /* is video call */
                false,          /* loopback */
                false,          /* tracing */
                false,          /* use camera 2 */
                videoWidth,
                videoHeight,
                videoFps,
                videoStartingBitrate,
                videoCodec,
                true,           /* h/w codec enabled */
                false,          /* capture to texture enabled */
                audioStartingBitrate,
                audioCodec,
                false,          /* no audio processing enabled (false = audio processing enabled) */
                false,          /* AEC dump enabled */
                false,          /* OpenSLES enabled */
                true,           /* disable built in Automatic Echo Cancellation */
                true,           /* disable built in Automatic Gain Control */
                true,           /* disable built in Noise Cancellation */
                false);         /* enable level control */
    }
}
