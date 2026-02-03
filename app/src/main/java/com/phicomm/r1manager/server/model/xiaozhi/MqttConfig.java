package com.phicomm.r1manager.server.model.xiaozhi;

import com.google.gson.annotations.SerializedName;

public class MqttConfig {
    public String endpoint;
    @SerializedName("client_id")
    public String clientId;
    public String username;
    public String password;
    @SerializedName("publish_topic")
    public String publishTopic;
    @SerializedName("subscribe_topic")
    public String subscribeTopic;
}
