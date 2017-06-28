package net.i2cat.seg.webrtcat4.sampleapp.util;

import android.os.AsyncTask;
import android.util.Log;

import com.goebl.david.Request;
import com.goebl.david.Response;
import com.goebl.david.Webb;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpClientUtils {
    private static final String TAG = "And2CatHttpUtils";

    private static boolean relaxedHttps = false;

    public static void setRelaxedHttps(boolean relaxedHttps) {
        HttpClientUtils.relaxedHttps = relaxedHttps;
    }

    public static void getJSON(String url, JSONUtils.JSONObjectConsumer consumer) {
        doHTTPJSON(url, false, null, consumer);
    }

    public static void getJSON(String url, JSONUtils.JSONArrayConsumer consumer) {
        doHTTPJSON(url, false, null, consumer);
    }

    public static void postJSON(String url, JSONObject body, JSONUtils.JSONObjectConsumer consumer) {
        doHTTPJSON(url, true, body, consumer);
    }

    public static void postJSON(final String url, final JSONObject body, final JSONUtils.JSONArrayConsumer consumer) {
        doHTTPJSON(url, true, body, consumer);
    }

    public static String buildUrl(String base, String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        StringBuilder sb = new StringBuilder(base);
        if (!base.endsWith("/")) {
            sb.append("/");
        }
        sb.append(path);
        return sb.toString();
    }

    private static void doHTTPJSON(final String url, final boolean doPOST,
                                   final JSONObject body, final JSONUtils.JSONErrorConsumer consumer) {
        if (doPOST) {
            Log.d(TAG, "POSTing to " + url + " with body:\n" + body);
        } else {
            Log.d(TAG, "GETting " + url + "\n");
        }

        AsyncTask<String, Void, Object> httpTask = new AsyncTask<String, Void, Object>() {
            private Exception execException;

            @Override
            protected Object doInBackground(String... params) {
                try {
                    Webb webb = Webb.create();
                    if (relaxedHttps) {
                        relaxHttps(webb);
                    }

                    Request req;
                    if (doPOST) {
                        req = webb.post(url)
                                  .body(body);         // JSONObject will be handled correctly
                    } else {
                        req = webb.get(url);
                    }

                    req.retry(1, false)    // at most one retry, no exponential backoff
                       .ensureSuccess();   // ensure HTTP 2xx response

                    Response<?> resp;
                    if (consumer instanceof JSONUtils.JSONArrayConsumer) {
                        resp = req.asJsonArray();
                    } else {
                        resp = req.asJsonObject();
                    }

                    return resp.getBody();
                } catch (Exception e) {
                    Log.e(TAG, "Error while attempting POST", e);
                    // Save this exception and report it in onPostExecute (i.e. when we are back in the main thread).
                    execException = e;
                }

                return null;
            }

            @Override
            protected void onPostExecute(Object res) {
                if (res != null) {
                    if (consumer instanceof JSONUtils.JSONArrayConsumer) {
                        ((JSONUtils.JSONArrayConsumer)consumer).onJSON((JSONArray)res);
                    } else if (consumer instanceof JSONUtils.JSONObjectConsumer) {
                        ((JSONUtils.JSONObjectConsumer)consumer).onJSON((JSONObject)res);
                    }
                } else if (execException != null) {
                    consumer.onError(execException.getLocalizedMessage());
                }
            }
        };

        httpTask.execute();
    }

    private static void relaxHttps(Webb webb) {
        try {
            // See https://developer.android.com/training/articles/security-ssl.html
            // To relax HTTPs restrictions, we need to do two things:
            // 1. relax certificate-chain verification (allows self-signed certificates to be used)
            TrustManager[] tms = { new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    Log.d(TAG, "Bypassing client cert verification: authType: " + authType + " chain length: " + chain.length);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    Log.d(TAG, "Bypassing server cert verification: authType: " + authType + " chain length: " + chain.length);
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tms, null);
            webb.setSSLSocketFactory(context.getSocketFactory());

            // 2. relax hostname verification
            webb.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    Log.d(TAG, "Bypassing verification of hostname: " + hostname);
                    return true;
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Unable to relax HTTPs restrictions", e);
        }
    }
}
