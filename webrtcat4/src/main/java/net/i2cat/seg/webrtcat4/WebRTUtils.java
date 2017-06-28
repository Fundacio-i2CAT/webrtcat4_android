package net.i2cat.seg.webrtcat4;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;

public class WebRTUtils {
    private static int BITS_IN_BYTE = 8;
    private static long KB = 1024L;
    private static long KBITS = KB*BITS_IN_BYTE;
    private static long MB = 1024*KB;
    private static long MBITS = MB*BITS_IN_BYTE;
    private static long GB = 1024*MB;
    private static long GBITS = GB*BITS_IN_BYTE;

    public static String formatBytes(double bytes) {
        DecimalFormat df = new DecimalFormat("#.###");
        if (bytes > GB) {
            return df.format((double)bytes / GB) + " GB";
        } else if (bytes > MB) {
            return df.format((double)bytes / MB) + " MB";
        } else if (bytes > KB) {
            return df.format((double)bytes / KB) + " KB";
        }
        return df.format(bytes) + " bytes";
    }

    public static String formatBitratePerSecond(long totalBytes, double durationInSecs) {
        if (durationInSecs > 0) {
            double bitrate = ((totalBytes * BITS_IN_BYTE)/durationInSecs);
            DecimalFormat df = new DecimalFormat("#.##");
            if (bitrate > GBITS) {
                return df.format((double)bitrate / GBITS) + " Gbps";
            } else if (bitrate > MBITS) {
                return df.format((double)bitrate / MBITS) + " Mbps";
            } else if (bitrate > KBITS) {
                return df.format((double)bitrate / KBITS) + " Kbps";
            }
            return df.format(totalBytes) + " bps";
        }
        return "0.0 Kbps";
    }

    // Put a |key|->|value| mapping in |json|.
    public static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

}
