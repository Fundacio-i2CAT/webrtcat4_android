package net.i2cat.seg.webrtcat4.stats;

import org.json.JSONObject;

import static net.i2cat.seg.webrtcat4.WebRTUtils.jsonPut;

public class WebRTCVideoStats extends WebRTCMediaStats {
    private Integer frameWidth;
    private Integer frameHeight;
    private Integer frameRate;

    public WebRTCVideoStats(MediaDirection mediaDirection) {
        super(MediaType.VIDEO, mediaDirection);
    }

    public Integer getFrameHeight() {
        return frameHeight;
    }

    public void setFrameHeight(Integer frameHeight) {
        this.frameHeight = frameHeight;
    }

    public Integer getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(Integer frameRate) {
        this.frameRate = frameRate;
    }

    public Integer getFrameWidth() {
        return frameWidth;
    }

    public void setFrameWidth(Integer recvFrameWidth) {
        this.frameWidth = recvFrameWidth;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        if ((frameHeight != null) && (frameWidth != null)) {
            sb.append(" (").append(frameWidth).append("x").append(frameHeight);
            if (frameRate != null) {
                sb.append(" at ").append(frameRate).append("fps");
            }
            sb.append(")");
        }
        return sb.toString();
    }

    public JSONObject toJSON(Long elapsedTimeMillis) {
        JSONObject json = super.toJSON(elapsedTimeMillis);
        if ((frameHeight != null) && (frameWidth != null)) {
            jsonPut(json, "frameWidth", frameWidth);
            jsonPut(json, "frameHeight", frameHeight);
            if (frameRate != null) {
                jsonPut(json, "frameRate", frameRate);
            }
        }
        return json;
    }
}
