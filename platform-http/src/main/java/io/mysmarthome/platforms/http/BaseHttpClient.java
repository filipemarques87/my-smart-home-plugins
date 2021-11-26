package io.mysmarthome.platforms.http;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mysmarthome.configuration.ApplicationProperties;
import io.mysmarthome.device.Device;
import io.mysmarthome.platform.PlatformPlugin;
import io.mysmarthome.platform.message.DeviceHandler;
import io.mysmarthome.platform.message.OnReceive;
import io.mysmarthome.platform.message.ReceivedMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static io.mysmarthome.util.SneakyException.sneakyException;

@Slf4j
public abstract class BaseHttpClient<T extends HttpDevice> implements PlatformPlugin<T> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OkHttpClient client;
    private final Map<String, DeviceHandler> handlers = new HashMap<>();
    private Map<String, BiFunction<HttpDevice, Object, Request.Builder>> httpMethods;
    private final Class<T> type;

    protected BaseHttpClient(Class<T> type) {
        this.type = type;
    }

    @Override
    public void start(ApplicationProperties config) {
        httpMethods = Map.of(
                "get", this::get,
                "post", this::post);

        int timeout = config.getInt("http.connection.timeout", 10);
        client = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void onRegisterDevice(HttpDevice device, final OnReceive callback) {
        log.info("Register device '{}' for {} plugin", device.getDeviceId(), getName());
        handlers.putIfAbsent(device.getDeviceId(), DeviceHandler.builder()
                .device(device.getOriginalDevice())
                .callback(callback)
                .build());
    }

    @Override
    public T getPlatformSpecificDevice(Device device) {
        return type.cast(new HttpDevice(device));
    }

    @SneakyThrows
    @Override
    public CompletableFuture<Optional<ReceivedMessage>> onSend(HttpDevice device, Object payload) {
        log.info("Sending request for {}...", device.getDeviceId());

        return CompletableFuture.supplyAsync(sneakyException(() -> {
            Request request = buildRequest(device, payload);
            Response response = client.newCall(request).execute();
            String body = Optional.ofNullable(response.body())
                    .map(b -> sneakyException(b::string).get())
                    .orElse(null);

            log.debug("Response: {}", body);

            if (response.code() >= 400) {
                log.error("Http call with error : {}, body : {}, {}", response.code(), body, response);
                throw new HttpCallException(response.code(), body);
            }

            ReceivedMessage message = ReceivedMessage.builder()
                    .message(prepareMessage(body))
                    .build();

            if (handlers.containsKey(device.getDeviceId())) {
                handlers.get(device.getDeviceId()).broadcastMessage(message);
            }

            return Optional.ofNullable(message);
        }));
    }

    @Override
    public String getName() {
        return "http";
    }


    private Request buildRequest(HttpDevice device, Object payload) {
        return httpMethods.get(device.getMethod()).apply(device, payload)
                .url(device.getUrl())
                .headers(device.getHeaders())
                .build();
    }

    private Request.Builder get(HttpDevice device, Object payload) {
        return new Request.Builder()
                .get();
    }

    private Request.Builder post(HttpDevice device, Object payload) {
        MediaType mediaType = Optional.ofNullable(device.getHeaders().get("Content-Type"))
                .map(MediaType::parse)
                .orElse(MediaType.parse("application/json"));
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            body = "{}";
        }
        return new Request.Builder()
                .post(RequestBody.create(body, mediaType));
    }

    protected Object prepareMessage(String responseBody) throws JsonProcessingException {
        return objectMapper.readValue(responseBody, Map.class);
    }
}



