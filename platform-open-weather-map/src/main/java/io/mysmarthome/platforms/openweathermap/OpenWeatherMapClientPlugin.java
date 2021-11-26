package io.mysmarthome.platforms.openweathermap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mysmarthome.configuration.ApplicationProperties;
import io.mysmarthome.device.Device;
import io.mysmarthome.platform.PlatformPlugin;
import io.mysmarthome.platform.message.DeviceHandler;
import io.mysmarthome.platform.message.OnReceive;
import io.mysmarthome.platform.message.ReceivedMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.mysmarthome.util.SneakyException.sneakyException;

@Slf4j
public class OpenWeatherMapClientPlugin extends Plugin {

    public OpenWeatherMapClientPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class OpenWeatherMapClient implements PlatformPlugin<OpenWeatherMapDevice> {

        private static final Map<String, Units> UNITS;

        static {
            UNITS = new HashMap<>();
            UNITS.put("standard", Units.builder()
                    .temperature("K")
                    .humidity("%")
                    .windSpeed("m/s")
                    .clouds("%")
                    .rain("mm")
                    .snow("mm")
                    .build());
            UNITS.put("metric", Units.builder()
                    .temperature("ºC")
                    .humidity("%")
                    .windSpeed("m/s")
                    .clouds("%")
                    .rain("mm")
                    .snow("mm")
                    .build());
            UNITS.put("imperial", Units.builder()
                    .temperature("ºF")
                    .humidity("%")
                    .windSpeed("mi/h")
                    .clouds("%")
                    .rain("mm")
                    .snow("mm")
                    .build());
        }

        private final ObjectMapper mapper = new ObjectMapper();

        private String appId;
        private String url;
        private OkHttpClient client;
        private final Map<String, DeviceHandler> handlers = new HashMap<>();

        @Override
        public void start(ApplicationProperties config) {
            url = config.getString("openweathermap.url");
            appId = config.getString("openweathermap.appId");
            int timeout = config.getInt("http.connection.timeout", 10);
            client = new OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .build();
        }

        @Override
        public void onRegisterDevice(OpenWeatherMapDevice device, final OnReceive callback) {
            log.info("Register device '{}' for {} plugin", device.getDeviceId(), getName());
            handlers.putIfAbsent(device.getDeviceId(), DeviceHandler.builder()
                    .device(device.getOriginalDevice())
                    .callback(callback)
                    .build());
        }

        @Override
        public OpenWeatherMapDevice getPlatformSpecificDevice(Device device) {
            return new OpenWeatherMapDevice(device, url, appId);
        }

        @Override
        public String getName() {
            return "openweathermap";
        }

        @SneakyThrows
        @Override
        public CompletableFuture<Optional<ReceivedMessage>> onSend(OpenWeatherMapDevice device, Object payload) {
            log.info("Sending request for {}...", device.getDeviceId());

            return CompletableFuture.supplyAsync(sneakyException(() -> {
                Request request = buildRequest(device);
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
                        .message(prepareMessage(device, body))
                        .build();

                if (handlers.containsKey(device.getDeviceId())) {
                    handlers.get(device.getDeviceId()).broadcastMessage(message);
                }

                return Optional.ofNullable(message);
            }));
        }

        private Request buildRequest(OpenWeatherMapDevice device) {
            return new Request.Builder().get()
                    .url(device.getUrl())
                    .headers(device.getHeaders())
                    .build();
        }

        protected Map<String, Object> prepareMessage(OpenWeatherMapDevice device, String responseBody) throws JsonProcessingException {
            OpenWeatherResponse response = mapper.readValue(responseBody, OpenWeatherResponse.class);
            response.setUnits(UNITS.get(device.getUnits()));

            TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {
            };
            return mapper.convertValue(response, typeRef);
        }
    }
}



