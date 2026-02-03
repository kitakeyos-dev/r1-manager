package com.phicomm.r1manager.server.client;

import com.phicomm.r1manager.util.AppLog;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Service to interact with Zing MP3 API (Cleaned & Refactored)
 */
public class ZingMp3Client {

    private static final String TAG = "ZingMp3Client";

    // --- API Configuration ---
    private static final String URL_API = "https://zingmp3.vn";
    private static final String URL_AC = "https://ac.zingmp3.vn"; // Fast autocomplete API

    private static final String AC_API_KEY = "X5BM3w8N7MKozC0B85o4KMlzLZKhV00y";
    private static final String SECRET_KEY = "acOrvUS15XRW2o9JksiK1KgQ6Vbds8ZW"; // Zing 1.17.5
    private static final String VERSION = "1.17.5";

    // --- State ---
    private static volatile ZingMp3Client instance;
    private String cookies = "";
    private final ConcurrentHashMap<String, String> ipCache = new ConcurrentHashMap<>();

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

    private ZingMp3Client() {
    }

    public static ZingMp3Client getInstance() {
        if (instance == null) {
            synchronized (ZingMp3Client.class) {
                if (instance == null) {
                    instance = new ZingMp3Client();
                }
            }
        }
        return instance;
    }

    // =================================================================================================
    // Public API
    // =================================================================================================

    /**
     * Search for songs using the fast Autocomplete API
     */
    public JSONObject search(String keyword) throws Exception {
        String encodedKeyword = java.net.URLEncoder.encode(keyword, "UTF-8");
        long ctime = System.currentTimeMillis() / 1000;

        // Generate signature: HMAC-SHA512(path + hash256(params), SECRET_KEY)
        String path = "/v1/web/ac-suggestions";
        String sigData = "ctime=" + ctime + "version=" + VERSION;
        String hash256 = com.phicomm.r1manager.util.SecurityUtils.getHash256(sigData);
        String sig = com.phicomm.r1manager.util.SecurityUtils.getHmac512(path + hash256, SECRET_KEY);

        String fullUrl = URL_AC + path + "?num=10&query=" + encodedKeyword
                + "&language=vi&ctime=" + ctime
                + "&version=" + VERSION
                + "&sig=" + sig
                + "&apiKey=" + AC_API_KEY;

        AppLog.d(TAG, "Search URL: " + fullUrl);
        return requestUrl(fullUrl, false);
    }

    /**
     * Get Song Streaming Information
     */
    public JSONObject getFullInfo(String id) throws Exception {
        ensureCookies();
        long ctime = System.currentTimeMillis() / 1000;

        String path = "/api/v2/song/get/streaming";
        // Params sorted key-alphabetically
        String sigData = "ctime=" + ctime + "id=" + id + "version=" + VERSION;
        String hash256 = com.phicomm.r1manager.util.SecurityUtils.getHash256(sigData);
        String sig = com.phicomm.r1manager.util.SecurityUtils.getHmac512(path + hash256, SECRET_KEY);

        String fullUrl = URL_API + path + "?id=" + id
                + "&ctime=" + ctime
                + "&version=" + VERSION
                + "&sig=" + sig
                + "&apiKey=" + AC_API_KEY;

        AppLog.d(TAG, "Streaming URL: " + fullUrl);
        return requestUrl(fullUrl, true);
    }

    /**
     * Get an open HttpURLConnection for streaming (Proxy use)
     * Handles IPv4 resolution and SSL configuration
     */
    public HttpURLConnection getStreamConnection(String url) throws Exception {
        return openConnection(url);
    }

    public String getCookies() {
        return cookies;
    }

    // =================================================================================================
    // Network Helpers
    // =================================================================================================

    private JSONObject requestUrl(String fullUrl, boolean useCookie) throws Exception {
        HttpURLConnection conn = openConnection(fullUrl);
        conn.setRequestMethod("GET");

        if (useCookie && !cookies.isEmpty()) {
            conn.setRequestProperty("Cookie", cookies);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            conn.disconnect();
            return new JSONObject(content.toString());
        } else {
            conn.disconnect();
            throw new Exception("HTTP Error: " + responseCode);
        }
    }

    private HttpURLConnection openConnection(String originalUrl) throws Exception {
        URL urlObj = new URL(originalUrl);
        String host = urlObj.getHost();
        String ip = getIPv4Raw(host);

        // Bypass DNS by replacing host with IP in URL
        String newUrl = originalUrl.replace(host, ip);
        URL url = new URL(newUrl);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(getUnsafeSslSocketFactory());
            ((HttpsURLConnection) conn).setHostnameVerifier(getUnsafeHostnameVerifier());
        }

        // Host header is mandatory when using IP in URL
        conn.setRequestProperty("Host", host);
        conn.setConnectTimeout(3000); // Fast fail
        conn.setReadTimeout(5000);
        conn.setInstanceFollowRedirects(false); // We handle redirects manually in proxy
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

        return conn;
    }

    private void ensureCookies() {
        if (!cookies.isEmpty())
            return;

        try {
            AppLog.d(TAG, "Fetching initial cookies...");
            HttpURLConnection conn = openConnection(URL_API);
            conn.connect();

            Map<String, List<String>> headerFields = conn.getHeaderFields();
            List<String> cookiesHeader = headerFields.get("Set-Cookie");

            if (cookiesHeader != null) {
                StringBuilder cookieBuilder = new StringBuilder();
                for (String cookie : cookiesHeader) {
                    if (cookieBuilder.length() > 0)
                        cookieBuilder.append("; ");
                    cookieBuilder.append(cookie.split(";")[0]);
                }
                cookies = cookieBuilder.toString();
                AppLog.d(TAG, "Cookies fetched: " + cookies);
            } else {
                cookies = "zmp3_rqid=VN.1.1.1.1; zw1=1"; // Fallback
            }
            conn.disconnect();
        } catch (Exception e) {
            AppLog.e(TAG, "Cookie fetch failed: " + e.getMessage());
            cookies = "zmp3_rqid=VN.1.1.1.1; zw1=1";
        }
    }

    private String getIPv4Raw(String host) {
        if (ipCache.containsKey(host))
            return ipCache.get(host);

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr instanceof Inet4Address) {
                    String ip = addr.getHostAddress();
                    ipCache.put(host, ip);
                    return ip;
                }
            }
        } catch (Exception e) {
            AppLog.w(TAG, "DNS resolution failed for " + host + ": " + e.getMessage());
        }
        return host; // Fallback
    }

    // =================================================================================================
    // SSL / TLS Compatibility (Android 5.1)
    // =================================================================================================

    private javax.net.ssl.SSLSocketFactory getUnsafeSslSocketFactory() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[] {};
                        }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return new com.phicomm.r1manager.util.Tls12SocketFactory(sslContext.getSocketFactory());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private javax.net.ssl.HostnameVerifier getUnsafeHostnameVerifier() {
        return (hostname, session) -> true;
    }
}
