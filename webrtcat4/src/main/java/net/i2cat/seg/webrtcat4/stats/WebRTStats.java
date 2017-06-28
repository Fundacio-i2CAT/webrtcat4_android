package net.i2cat.seg.webrtcat4.stats;

import org.json.JSONObject;

import static net.i2cat.seg.webrtcat4.WebRTUtils.jsonPut;

public class WebRTStats {
    private Long callStartTime;
    private String localConnectionType;
    private String localAddress;
    private String remoteConnectionType;
    private String remoteAddress;
    private String transportType;
    private WebRTCAudioStats inAudioStats;
    private WebRTCAudioStats outAudioStats;
    private WebRTCVideoStats inVideoStats;
    private WebRTCVideoStats outVideoStats;

    public Long getCallStartTime() {
        return callStartTime;
    }

    public void setCallStartTime(Long callStartTime) {
        this.callStartTime = callStartTime;
    }

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public WebRTCAudioStats getInAudioStats() {
        return inAudioStats;
    }

    public long getInAudioStatsBytes() {
        return ((inAudioStats != null) ? inAudioStats.getBytes() : 0);
    }

    public void setInAudioStats(WebRTCAudioStats inAudioStats) {
        this.inAudioStats = inAudioStats;
    }

    public WebRTCVideoStats getInVideoStats() {
        return inVideoStats;
    }

    public long getInVideoStatsBytes() {
        return ((inVideoStats != null) ? inVideoStats.getBytes() : 0);
    }

    public void setInVideoStats(WebRTCVideoStats inVideoStats) {
        this.inVideoStats = inVideoStats;
    }

    public String getLocalConnectionType() {
        return localConnectionType;
    }

    public void setLocalConnectionType(String localConnectionType) {
        this.localConnectionType = localConnectionType;
    }

    public WebRTCMediaStats getOutAudioStats() {
        return outAudioStats;
    }

    public long getOutAudioStatsBytes() {
        return ((outAudioStats != null) ? outAudioStats.getBytes() : 0);
    }

    public void setOutAudioStats(WebRTCAudioStats outAudioStats) {
        this.outAudioStats = outAudioStats;
    }

    public WebRTCVideoStats getOutVideoStats() {
        return outVideoStats;
    }

    public long getOutVideoStatsBytes() {
        return ((outVideoStats != null) ? outVideoStats.getBytes() : 0);
    }

    public void setOutVideoStats(WebRTCVideoStats outVideoStats) {
        this.outVideoStats = outVideoStats;
    }

    public long getTotalAudioBytes() {
        return getInAudioStatsBytes() + getOutAudioStatsBytes();
    }

    public long getTotalVideoBytes() {
        return getInVideoStatsBytes() + getOutVideoStatsBytes();
    }

    public String getRemoteConnectionType() {
        return remoteConnectionType;
    }

    public void setRemoteConnectionType(String remoteConnectionType) {
        this.remoteConnectionType = remoteConnectionType;
    }

    @Override
    public String toString() {
        return toString(null);
    }

    public String toString(Long elapsedTimeMillis) {
        StringBuilder sb = new StringBuilder();
        sb.append("LOCAL : ").append(localAddress).append(" (").append(localConnectionType).append(" over ").append(transportType).append(")\n");
        sb.append("REMOTE: ").append(remoteAddress).append(" (").append(remoteConnectionType).append(" over ").append(transportType).append(")\n");
        if (inAudioStats != null) {
            sb.append(inAudioStats.toString(elapsedTimeMillis)).append("\n");
        }
        if (outAudioStats != null) {
            sb.append(outAudioStats.toString(elapsedTimeMillis)).append("\n");
        }
        if (inVideoStats != null) {
            sb.append(inVideoStats.toString(elapsedTimeMillis)).append("\n");
        }
        if (outVideoStats != null) {
            sb.append(outVideoStats.toString(elapsedTimeMillis)).append("\n");
        }
        return sb.toString();
    }

    public JSONObject toJSON() {
        JSONObject stats = new JSONObject();
        jsonPut(stats, "address", localAddress);
        jsonPut(stats, "transport", transportType);

        Long elapsedTimeMillis = null;
        if (callStartTime != null) {
            elapsedTimeMillis = System.currentTimeMillis() - callStartTime;
        }
        JSONObject audioStats = new JSONObject();
        if (inAudioStats != null) {
            jsonPut(audioStats, "in", inAudioStats.toJSON(elapsedTimeMillis));
        }
        if (outAudioStats != null) {
            jsonPut(audioStats, "out", outAudioStats.toJSON(elapsedTimeMillis));
        }
        jsonPut(stats, "audio", audioStats);

        JSONObject videoStats = new JSONObject();
        if (inVideoStats != null) {
            jsonPut(videoStats, "in", inVideoStats.toJSON(elapsedTimeMillis));
        }
        if (outVideoStats != null) {
            jsonPut(videoStats, "out", outVideoStats.toJSON(elapsedTimeMillis));
        }
        jsonPut(stats, "video", videoStats);
        return stats;
    }

}
