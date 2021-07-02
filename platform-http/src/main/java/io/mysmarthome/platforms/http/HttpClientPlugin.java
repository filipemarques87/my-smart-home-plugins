package io.mysmarthome.platforms.http;


import com.fasterxml.jackson.core.JsonProcessingException;
import io.mysmarthome.configuration.ApplicationProperties;
import io.mysmarthome.device.Device;
import io.mysmarthome.platform.PlatformPlugin;
import io.mysmarthome.platform.message.DeviceHandler;
import io.mysmarthome.platform.message.OnReceive;
import io.mysmarthome.platform.message.ReceivedMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.mysmarthome.util.SneakyException.sneakyException;

@Slf4j
public class HttpClientPlugin extends Plugin {

    public HttpClientPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class HttpClient implements PlatformPlugin {

        private OkHttpClient client;
        private final Map<String, DeviceHandler> handlers = new HashMap<>();
        private Map<String, Function<Device, Request.Builder>> httpMethods;

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
        public void registerDevice(Device device, final OnReceive callback) {
            log.info("Register device '{}' for {} plugin", device.getDeviceId(), getName());
            handlers.putIfAbsent(device.getDeviceId(), DeviceHandler.builder()
                    .device(device)
                    .callback(callback)
                    .build());
        }

        @SneakyThrows
        @Override
        public CompletableFuture<Optional<ReceivedMessage>> send(Device device, Object payload) {
            log.info("Sending request ...");
            Request request = buildRequest(device);
            Response response = client.newCall(request).execute();
            String body = Optional.ofNullable(response.body())
                    .map(b -> sneakyException(b::string).get())
                    .orElse(null);

            log.info("Response: {}", body);

            if (response.code() >= 400) {
                log.error("Http call with error : {}, body : {}", response.code(), body);
                throw new HttpCallException(response.code(), body);
            }

            ReceivedMessage message = ReceivedMessage.builder()
                    .message(prepareMessage(body))
                    .build();

            if (handlers.containsKey(device.getDeviceId())) {
                handlers.get(device.getDeviceId()).broadcastMessage(message);
            }
            return CompletableFuture.supplyAsync(sneakyException(() -> Optional.ofNullable(message)));
        }

        @Override
        public String getName() {
            return "http";
        }

        protected String getUrl(Device device) {
            return device.getCustomInfo("url").asString();
        }

        protected String getHttpMethod(Device device) {
            return device.getCustomInfo("method").asString("get");
        }

        protected String getPayload(Device device) {
            return device.getCustomInfo("payload").asString();
        }

        protected Headers getHeaders(Device device) {
            return Headers.of((Map<String, String>) (device.getCustomInfo("headers").as(Map.class)));
        }

        private Request buildRequest(Device device) {
            return httpMethods.get(getHttpMethod(device)).apply(device)
                    .url(getUrl(device))
                    .headers(getHeaders(device))
                    .build();
        }

        private Request.Builder get(Device device) {
            return new Request.Builder()
                    .get();
        }

        private Request.Builder post(Device device) {
            MediaType mediaType = Optional.ofNullable(getHeaders(device).get("Content-Type"))
                    .map(MediaType::parse)
                    .orElse(MediaType.parse("text/plain"));
            return new Request.Builder()
                    .post(RequestBody.create(getPayload(device), mediaType));
        }

        protected Object prepareMessage(String responseBody) throws JsonProcessingException {
            return responseBody;
        }
    }
}



