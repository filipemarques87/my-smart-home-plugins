package io.mysmarthome.platforms.mqtt.client;

import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Objects;
import java.util.concurrent.Executors;

import static io.mysmarthome.util.SneakyException.sneakyException;

public class SimpleMqttClient extends MqttClient {

    public SimpleMqttClient(String url) throws MqttException {
        super(url, MqttClient.generateClientId(), new MemoryPersistence());
    }

    @Override
    public void connect() throws MqttException {
        // default options
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        super.setTimeToWait(1000);

        super.connect(options);
    }

    @Override
    public void close() throws MqttException {
        if (isConnected()) {
            disconnect();
        }
        super.close(true);
    }

    @RequiredArgsConstructor
    public static class Publisher {
        private final SimpleMqttClient mqttClient;
        private String topic;
        private MqttMessage msg;

        public Publisher topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Publisher msg(String msg) {
            this.msg = prepareMessage(msg);
            return this;
        }

        private MqttMessage prepareMessage(String payload) {
            MqttMessage msg = Objects.isNull(payload) ?
                    new MqttMessage() :
                    new MqttMessage(payload.getBytes());

            msg.setQos(0);
            msg.setRetained(true);
            return msg;
        }

        public void publish() {
            Executors.defaultThreadFactory()
                    .newThread(sneakyException(() -> mqttClient.publish(topic, msg)));
        }
    }

    public Publisher publisher() {
        return new Publisher(this);
    }
}
