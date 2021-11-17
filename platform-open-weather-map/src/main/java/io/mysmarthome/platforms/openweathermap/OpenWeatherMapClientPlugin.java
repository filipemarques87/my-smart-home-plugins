package io.mysmarthome.platforms.openweathermap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mysmarthome.configuration.ApplicationProperties;
import io.mysmarthome.device.Device;
import io.mysmarthome.platforms.http.BaseHttpClient;
import io.mysmarthome.platforms.http.HttpClientPlugin;
import io.mysmarthome.platforms.http.HttpDevice;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.util.Optional;

@Slf4j
public class OpenWeatherMapClientPlugin extends Plugin {

    public OpenWeatherMapClientPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class OpenWeatherMapClient extends BaseHttpClient<HttpDevice> {

        private String appKey;
        private String endpoint;
        private final ObjectMapper mapper = new ObjectMapper();

        public OpenWeatherMapClient() {
            super(HttpDevice.class);
        }

        @Override
        public void start(ApplicationProperties config) {
            super.start(config);
            endpoint = config.getString("openweathermap.endpoint");
            appKey = config.getString("openweathermap.appkey");
        }

        @Override
        public String getName() {
            return "openweathermap";
        }

        protected OpenWeatherResponse prepareMessage(String responseBody) throws JsonProcessingException {
            return mapper.readValue(responseBody, OpenWeatherResponse.class);
        }

        protected String getUrl(Device device) {
            String city = device.getCustomInfo("city").asString();
            String units = Optional.ofNullable(device.getCustomInfo("units").asString())
                    .orElse("metric");
            return String.format("%s?q=%s&units=%s&appid=%s", endpoint, city, units, appKey);
        }

        protected String getHttpMethod(Device device) {
            return "get";
        }
    }
}



