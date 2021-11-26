package io.mysmarthome.platforms.openweathermap;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import lombok.*;

import java.io.IOException;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize(using = OpenWeatherResponse.OpenWeatherResponseDeserializer.class)
public class OpenWeatherResponse implements Serializable {

    private String location;
    private String weather;
    private String icon;
    private double temperature;
    private double humidity;
    private double windSpeed;
    private double snow;
    private double rain;
    private double clouds;
    private Units units;

    static class OpenWeatherResponseDeserializer extends StdDeserializer<OpenWeatherResponse> {

        protected OpenWeatherResponseDeserializer() {
            super(OpenWeatherResponse.class);
        }

        protected OpenWeatherResponseDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public OpenWeatherResponse deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode jsonNode = jsonParser.getCodec().readTree(jsonParser);
            return OpenWeatherResponse.builder()
                    .location(jsonNode.at("/name").asText())
                    .weather(jsonNode.at("/weather/0/main").asText())
                    .icon(jsonNode.at("/weather/0/icon").asText())
                    .temperature(jsonNode.at("/main/temp").asDouble())
                    .humidity(jsonNode.at("/main/humidity").asDouble())
                    .windSpeed(jsonNode.at("/wind/speed").asDouble())
                    .snow(jsonNode.at("/snow/1h").asDouble())
                    .rain(jsonNode.at("/rain/1h").asDouble())
                    .clouds(jsonNode.at("/clouds/all").asDouble())
                    .build();
        }
    }
}
