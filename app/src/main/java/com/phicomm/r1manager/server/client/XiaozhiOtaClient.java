package com.phicomm.r1manager.server.client;

import android.content.Context;
import com.phicomm.r1manager.util.AppLog;

import com.google.gson.Gson;
import com.phicomm.r1manager.config.XiaozhiConfig;
import com.phicomm.r1manager.server.model.xiaozhi.Activation;
import com.phicomm.r1manager.server.model.xiaozhi.DeviceInfo;
import com.phicomm.r1manager.server.model.xiaozhi.OtaResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class XiaozhiOtaClient {
    private static final String TAG = "XiaozhiOtaClient";
    private Context context;

    public XiaozhiOtaClient(Context context) {
        this.context = context;
    }

    public OtaResult checkVersion(String deviceId, String uuid, String qtaUrl) {
        XiaozhiConfig config = XiaozhiConfig.getInstance(context);
        try {
            DeviceInfo info = DeviceInfo.generate(deviceId, uuid);
            String jsonPayload = new Gson().toJson(info);

            URL url = new URL(qtaUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Device-Id", deviceId);
            conn.setRequestProperty("Client-Id", uuid);
            conn.setRequestProperty("X-Language", "Chinese");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("UTF-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            AppLog.i(TAG, "OTA Response Code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                String jsonResponse = response.toString();
                AppLog.i(TAG, "OTA Response: " + jsonResponse);

                OtaResult result = new Gson().fromJson(jsonResponse, OtaResult.class);

                // Save MQTT config if present
                if (result.mqttConfig != null) {
                    config.setMqttConfig(result.mqttConfig);
                    config.setTransportType("mqtt");
                    AppLog.i(TAG, "Updated MQTT Config");
                }
                return result;
            } else {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                String jsonResponse = response.toString();
                AppLog.e(TAG, "OTA Request Failed: " + responseCode + " - " + jsonResponse);

                try {
                    OtaResult errorResult = new Gson().fromJson(jsonResponse, OtaResult.class);
                    if (errorResult != null)
                        return errorResult;
                } catch (Exception e) {
                    // ignore parse error for non-json error pages
                }

                OtaResult error = new OtaResult();
                error.code = responseCode;
                error.message = "HTTP Error " + responseCode + (jsonResponse.isEmpty() ? "" : ": " + jsonResponse);
                return error;
            }
        } catch (Exception e) {
            AppLog.e(TAG, "OTA Error", e);
        }
        return null;
    }
}
