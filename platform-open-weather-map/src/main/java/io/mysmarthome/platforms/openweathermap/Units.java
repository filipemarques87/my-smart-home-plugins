package io.mysmarthome.platforms.openweathermap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Units {
    String temperature;
    String humidity;
    String windSpeed;
    String clouds;
    String rain;
    String snow;
}
