package net.i2cat.seg.webrtcat4.sampleapp.util;

import org.json.JSONArray;
import org.json.JSONObject;

public class JSONUtils {

    public static interface JSONErrorConsumer {
        void onError(String errorMessage);
    }

    public static interface JSONObjectConsumer extends JSONErrorConsumer {
        void onJSON(JSONObject obj);
    }

    public static interface JSONArrayConsumer extends JSONErrorConsumer {
        void onJSON(JSONArray arr);
    }
}
