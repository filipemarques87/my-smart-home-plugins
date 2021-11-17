package io.mysmarthome.platforms.mqtt;

import io.mysmarthome.device.Device;
import io.mysmarthome.util.TypedValue;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MqttDevice implements Device {

    private final Device device;

    @Override
    public String getDeviceId() {
        return device.getDeviceId();
    }

    @Override
    public TypedValue getCustomInfo(String key) {
        return device.getCustomInfo(key);
    }

    public String getActionTopic() {
        return getCustomInfo("actionTopic").asString();
    }

    public String getListenTopic() {
        return getCustomInfo("listenTopic").asString();
    }
}
