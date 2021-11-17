package io.mysmarthome.platforms.mqtt.client;

public class SimpleMqttClientException extends RuntimeException {

    public SimpleMqttClientException() {
    }

    public SimpleMqttClientException(String message) {
        super(message);
    }

    public SimpleMqttClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public SimpleMqttClientException(Throwable cause) {
        super(cause);
    }

    protected SimpleMqttClientException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
