package io.mysmarthome.platforms.mqtt;

import io.mysmarthome.device.Device;
import io.mysmarthome.platform.message.OnReceive;
import io.mysmarthome.platform.message.ReceivedMessage;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Data
public class MqttDeviceHandler {

    @Setter(AccessLevel.NONE)
    private Device device;

    @Setter(AccessLevel.NONE)
    private OnReceive callback;

    private List<OnReceive> tempCallback;


    @Builder
    public MqttDeviceHandler(Device device, OnReceive callback) {
        this.device = device;
        this.callback = callback;
        tempCallback = new CopyOnWriteArrayList<>();
    }

    public void addTempCallback(OnReceive callback) {
        tempCallback.add(callback);
    }

    public void broadcastMessage(MqttMessage mqttMsg) {
        ReceivedMessage message = ReceivedMessage.builder()
                .message(new String(mqttMsg.getPayload()))
                .build();

        try {
            if (callback != null) {
                callback.onReceive(device, message);
            }
            tempCallback.forEach(c -> c.onReceive(device, message));
        } catch (Throwable t) {
            log.error("Error while handle the mqtt response", t);
        }
        tempCallback.clear();
    }
}
