package net.i2cat.seg.webrtcat4.stats;

import org.json.JSONObject;

import java.text.DecimalFormat;

import static net.i2cat.seg.webrtcat4.WebRTUtils.formatBitratePerSecond;
import static net.i2cat.seg.webrtcat4.WebRTUtils.formatBytes;
import static net.i2cat.seg.webrtcat4.WebRTUtils.jsonPut;

public class WebRTCMediaStats {
    public enum MediaType {AUDIO, VIDEO};
    public enum MediaDirection {INCOMING, OUTGOING}

    private MediaType mediaType;
    private MediaDirection mediaDirection;
    private Long bytes;
    private String codec;
    private Long packetsLost;
    private Long packetsSentRecvd;

    protected WebRTCMediaStats(MediaType mediaType, MediaDirection mediaDirection) {
        this.mediaType = mediaType;
        this.mediaDirection = mediaDirection;
    }

    public long getBytes() {
        return bytes;
    }

    public void setBytes(long bytes) {
        this.bytes = bytes;
    }

    public String getCodec() {
        return codec;
    }

    public void setCodec(String codec) {
        this.codec = codec;
    }

    public Long getPacketsSentRecvd() {
        return packetsSentRecvd;
    }

    public void setPacketsSentRecvd(Long packetsSentRecvd) {
        this.packetsSentRecvd = packetsSentRecvd;
    }

    public Long getPacketsLost() {
        return packetsLost;
    }

    public void setPacketsLost(Long packetsLost) {
        this.packetsLost = packetsLost;
    }

    public Double getPacketLossRatio() {
        if ((packetsLost == null) || (packetsSentRecvd == null)) {
            return null;
        }

        if (packetsSentRecvd > 0) {
            return (double)packetsLost/packetsSentRecvd;
        }
        return null;
    }

    @Override
    public String toString() {
        return toString(null);
    }

    public String toString(Long elapsedTimeMillis) {
        StringBuilder sb = new StringBuilder();

        sb.append((mediaDirection == MediaDirection.INCOMING) ? "IN" : "OUT")
          .append((mediaType == MediaType.AUDIO) ? " AUDIO": " VIDEO")
          .append(" (").append(codec).append("): ");

        if (elapsedTimeMillis != null) {
            sb.append(formatBitratePerSecond(bytes, (elapsedTimeMillis / 1000.0)));
        } else {
            sb.append(bytes).append(" bytes");
        }

        String pktLossStr = "???";
        Double packetLoss = getPacketLossRatio();
        if (packetLoss != null) {
            pktLossStr = new DecimalFormat("##0.00").format(packetLoss * 100) + "%";
        }
        sb.append(" (pkt loss: ").append(pktLossStr).append(")");
        return sb.toString();
    }

    public JSONObject toJSON(Long elapsedTimeMillis) {
        JSONObject json = new JSONObject();
        jsonPut(json, "bytes", bytes);
        jsonPut(json, "formattedBytes", formatBytes(bytes));
        jsonPut(json, "codec", codec);
        if (elapsedTimeMillis != null) {
            jsonPut(json, "avgBitrate", formatBitratePerSecond(bytes, (elapsedTimeMillis / 1000.0)));
        }
        if (packetsLost != null) {
            jsonPut(json, "packetsLost", packetsLost);
        }
        if (packetsSentRecvd != null) {
            jsonPut(json, ((mediaDirection == MediaDirection.INCOMING) ? "packetsRecvd" : "packetsSent"), packetsSentRecvd);
        }
        return json;
    }
}
