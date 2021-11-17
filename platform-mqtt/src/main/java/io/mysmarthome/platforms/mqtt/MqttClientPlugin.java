package io.mysmarthome.platforms.mqtt;

import io.mysmarthome.configuration.ApplicationProperties;
import io.mysmarthome.device.Device;
import io.mysmarthome.platform.PlatformPlugin;
import io.mysmarthome.platform.message.DeviceHandler;
import io.mysmarthome.platform.message.OnReceive;
import io.mysmarthome.platform.message.ReceivedMessage;
import io.mysmarthome.platforms.mqtt.client.SimpleMqttClient;
import io.mysmarthome.platforms.mqtt.client.SimpleMqttClientException;
import io.mysmarthome.util.ObjectHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.mysmarthome.util.SneakyException.sneakyException;

@Slf4j
public class MqttClientPlugin extends Plugin {

    public MqttClientPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class MqttClient implements PlatformPlugin<MqttDevice> {
        private SimpleMqttClient mqttClient = null;
        private final Map<String, DeviceHandler> handlers = new HashMap<>();

        @Override
        public void start(ApplicationProperties config) {
            initMqttClient(config.getString("mqtt.broker.url"));
        }

        @Override
        public void onRegisterDevice(MqttDevice device, final OnReceive callback) {
            log.info("Register device '{}' for mqtt plugin", device.getDeviceId());
            Optional.ofNullable(device.getListenTopic())
                    .ifPresent(topic -> handlers.put(topic, DeviceHandler.builder()
                            .device(device)
                            .callback(callback)
                            .build()));
        }

        private void initMqttClient(String url) {
            try {
                log.info("Mqtt url: {}", url);
                mqttClient = new SimpleMqttClient(url);
                mqttClient.connect();

                mqttClient.subscribe("#", (topic, message) -> {
                    if (handlers.containsKey(topic) && handlers.get(topic) != null) {
                        log.info("Receive data to topic {}", topic);
                        handlers.get(topic).broadcastMessage(ReceivedMessage.builder()
                                .message(new String(message.getPayload()))
                                .build());
                    }
                });
            } catch (MqttException e) {
                throw new SimpleMqttClientException("Error on initialize mqtt client", e);
            }
        }

        @Override
        public MqttDevice getPlatformSpecificDevice(Device device) {
            return new MqttDevice(device);
        }

        @Override
        public CompletableFuture<Optional<ReceivedMessage>> onSend(MqttDevice device, Object payload) {
            String pubTopic = device.getActionTopic();
            if (StringUtils.isBlank(pubTopic)) {
                return CompletableFuture.supplyAsync(Optional::empty);
            }

            String subTopic = device.getListenTopic();
            if (StringUtils.isBlank(subTopic)) {
                publish(pubTopic, payload);
                return CompletableFuture.supplyAsync(Optional::empty);
            }

            Object lock = new Object();
            ObjectHolder<ReceivedMessage> holder = new ObjectHolder<>();

            if (!handlers.containsKey(subTopic)) {
                handlers.put(subTopic, DeviceHandler.builder()
                        .device(device)
                        .callback(null)
                        .build());
            }

            handlers.get(subTopic).addTempCallback((d, msg) -> {
                synchronized (lock) {
                    holder.set(msg);
                    lock.notify();
                }
            });

            publish(pubTopic, payload);

            return CompletableFuture.supplyAsync(sneakyException(() -> {
                synchronized (lock) {
                    lock.wait();
                    return Optional.ofNullable(holder.get());
                }
            }));
        }

        private void publish(String topic, Object payload) {
            log.info("Send data on topic {}", topic);
            mqttClient.publisher()
                    .topic(topic)
                    .msg(payload == null ? null : Objects.toString(payload))
                    .publish();
        }

        @Override
        public void shutdown() {
            try {
                mqttClient.close();
            } catch (MqttException e) {
                log.info("Mqtt client shutdown");
            }
        }

        @Override
        public String getName() {
            return "mqtt";
        }
    }
}
