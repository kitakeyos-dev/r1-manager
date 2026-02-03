package com.phicomm.r1manager.server.manager;

import android.content.Context;
import com.phicomm.r1manager.config.XiaozhiConfig;
import com.phicomm.r1manager.server.client.XiaozhiOtaClient;
import com.phicomm.r1manager.server.model.xiaozhi.OtaResult;
import com.phicomm.r1manager.server.model.xiaozhi.XiaozhiBotProfile;
import com.phicomm.r1manager.server.service.XiaozhiService;
import com.phicomm.r1manager.util.AppLog;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * XiaozhiManager - Orchestrates Xiaozhi assistant logic.
 * Thins out XiaozhiController by handling conversation and OTA orchestration.
 */
public class XiaozhiManager {
    private final Context context;
    private final XiaozhiOtaClient otaClient;

    public XiaozhiManager(Context context) {
        this.context = context;
        this.otaClient = new XiaozhiOtaClient(context);
    }

    public boolean startConversation() {
        XiaozhiService service = MusicServiceManager.getXiaozhiService();
        return service != null && service.startConversation();
    }

    public void stopConversation() {
        XiaozhiService service = MusicServiceManager.getXiaozhiService();
        if (service != null) {
            service.stopConversation();
        }
    }

    public String getStatus() {
        XiaozhiService service = MusicServiceManager.getXiaozhiService();
        return service != null ? service.getStatus() : "Service not available";
    }

    public void updateConfig(Map<String, String> body) {
        XiaozhiConfig config = XiaozhiConfig.getInstance(context);
        XiaozhiBotProfile active = config.getActiveProfile();

        if (body.containsKey("voice_bot_enabled")) {
            boolean enabled = Boolean.parseBoolean(body.get("voice_bot_enabled"));
            config.setVoiceBotEnabled(enabled);
            // If we toggled, we definitely need to reload service, which happens at the end
        }

        if (active == null) {
            return;
        }

        updateProfileFromMap(active, body);
        replaceProfileInList(config, active);
        reloadServiceIfNeeded(config);
    }

    public List<XiaozhiBotProfile> getBotProfiles() {
        return XiaozhiConfig.getInstance(context).getBotProfiles();
    }

    public String getActiveBotId() {
        return XiaozhiConfig.getInstance(context).getActiveBotId();
    }

    public void addOrUpdateBot(XiaozhiBotProfile profile) {
        XiaozhiConfig config = XiaozhiConfig.getInstance(context);
        List<XiaozhiBotProfile> profiles = config.getBotProfiles();

        if (isNewBot(profile)) {
            initializeNewBot(profile);
            profiles.add(profile);
        } else {
            enforceDefaultBotRules(profile);
            updateOrAddProfile(profiles, profile);
        }

        config.setBotProfiles(profiles);
        reloadServiceIfActiveBot(profile.id, config);
    }

    public void deleteBot(String botId) {
        if (botId == null || "default".equals(botId)) {
            return;
        }

        XiaozhiConfig config = XiaozhiConfig.getInstance(context);
        List<XiaozhiBotProfile> profiles = config.getBotProfiles();

        profiles.removeIf(p -> p.id != null && p.id.equals(botId));
        config.setBotProfiles(profiles);

        if (botId.equals(config.getActiveBotId())) {
            handleActiveBotDeletion(config, profiles);
        }
    }

    public void switchActiveBot(String botId) {
        XiaozhiConfig config = XiaozhiConfig.getInstance(context);
        config.setActiveBotId(botId);
        reloadServiceIfNeeded(config);
    }

    public OtaResult checkOta(String deviceId, String uuid, String qtaUrl) {
        XiaozhiConfig config = XiaozhiConfig.getInstance(context);
        OtaParameters params = resolveOtaParameters(deviceId, uuid, qtaUrl, config);

        if (!params.isValid()) {
            return null;
        }

        OtaResult result = otaClient.checkVersion(params.deviceId, params.uuid, params.qtaUrl);
        if (isActivationSuccessful(result)) {
            reloadServiceIfNeeded(config);
        }
        return result;
    }

    public OtaResult checkOta() {
        return checkOta(null, null, null);
    }

    // Helper methods

    private void updateProfileFromMap(XiaozhiBotProfile profile, Map<String, String> body) {
        if (body.containsKey("ws_url")) {
            profile.wsUrl = body.get("ws_url");
        }
        if (body.containsKey("qta_url")) {
            profile.qtaUrl = body.get("qta_url");
        }
        if (body.containsKey("device_id")) {
            profile.customMac = body.get("device_id");
        }
    }

    private void replaceProfileInList(XiaozhiConfig config, XiaozhiBotProfile active) {
        List<XiaozhiBotProfile> profiles = config.getBotProfiles();
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(active.id)) {
                profiles.set(i, active);
                break;
            }
        }
        config.setBotProfiles(profiles);
    }

    private boolean isNewBot(XiaozhiBotProfile profile) {
        return profile.id == null || profile.id.isEmpty();
    }

    private void initializeNewBot(XiaozhiBotProfile profile) {
        profile.id = UUID.randomUUID().toString();
        profile.uuid = UUID.randomUUID().toString();
        if (profile.macType == null) {
            profile.macType = "FAKE";
        }
    }

    private void enforceDefaultBotRules(XiaozhiBotProfile profile) {
        if ("default".equals(profile.id)) {
            profile.macType = "REAL";
        }
    }

    private void updateOrAddProfile(List<XiaozhiBotProfile> profiles, XiaozhiBotProfile profile) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(profile.id)) {
                profiles.set(i, profile);
                return;
            }
        }
        profiles.add(profile);
    }

    private void reloadServiceIfActiveBot(String botId, XiaozhiConfig config) {
        if (botId.equals(config.getActiveBotId())) {
            reloadServiceIfNeeded(config);
        }
    }

    private void reloadServiceIfNeeded(XiaozhiConfig config) {
        XiaozhiService service = MusicServiceManager.getXiaozhiService();
        if (service != null) {
            service.reloadConfig();
        }
    }

    private void handleActiveBotDeletion(XiaozhiConfig config, List<XiaozhiBotProfile> profiles) {
        if (!profiles.isEmpty()) {
            switchActiveBot(profiles.get(0).id);
        } else {
            config.setActiveBotId(null);
            XiaozhiService service = MusicServiceManager.getXiaozhiService();
            if (service != null) {
                service.stopConversation();
            }
        }
    }

    private OtaParameters resolveOtaParameters(String deviceId, String uuid, String qtaUrl, XiaozhiConfig config) {
        if (deviceId != null && uuid != null && qtaUrl != null) {
            return new OtaParameters(deviceId, uuid, qtaUrl);
        }

        XiaozhiBotProfile active = config.getActiveProfile();
        if (active == null) {
            return new OtaParameters(null, null, null);
        }

        String resolvedQta = qtaUrl != null ? qtaUrl : active.qtaUrl;
        String resolvedUuid = uuid != null ? uuid : active.uuid;
        String resolvedDeviceId = deviceId != null ? deviceId : getDeviceIdForProfile(active, config);

        return new OtaParameters(resolvedDeviceId, resolvedUuid, resolvedQta);
    }

    private String getDeviceIdForProfile(XiaozhiBotProfile profile, XiaozhiConfig config) {
        return "default".equals(profile.id) ? config.getDeviceId() : profile.customMac;
    }

    private boolean isActivationSuccessful(OtaResult result) {
        return result != null && (result.mqttConfig != null || result.code == 0);
    }

    private static class OtaParameters {
        final String deviceId;
        final String uuid;
        final String qtaUrl;

        OtaParameters(String deviceId, String uuid, String qtaUrl) {
            this.deviceId = deviceId;
            this.uuid = uuid;
            this.qtaUrl = qtaUrl;
        }

        boolean isValid() {
            return deviceId != null && uuid != null && qtaUrl != null;
        }
    }
}
