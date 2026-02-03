package com.phicomm.r1manager.server.model.xiaozhi;

import com.google.gson.annotations.SerializedName;

public class OtaResult {
    @SerializedName("mqtt")
    public MqttConfig mqttConfig;
    public Activation activation;

    // Generic error fields
    public int code;
    public String message;
    // firmware and server_time omitted for now as not critical for auth
}
