package com.nn.safetransfer.e2e.client;

import com.nn.safetransfer.common.metrics.MetricName;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ActuatorApiClient extends E2eHttpClient {
    private static final String GET_HEALTH_PATH = "/actuator/health";
    private static final String GET_METRICS_PATH = "/actuator/metrics/%s?tag=%s";

    public ActuatorApiClient(String baseUrl) {
        super(baseUrl);
    }

    public HttpResponse<String> getHealthStatus() throws IOException, InterruptedException {
        return get(GET_HEALTH_PATH);
    }

    public double metricCount(MetricName metricName, String tag) throws Exception {
        var encodedTag = URLEncoder.encode(tag, UTF_8);
        var response = get(GET_METRICS_PATH.formatted(metricName.getValue(), encodedTag));

        if (response.statusCode() == HttpStatus.NOT_FOUND.value()) {
            return 0.0d;
        }

        assertThat(response.statusCode())
                .withFailMessage("Expected metric endpoint to return 200 but got %s. Body: %s", response.statusCode(), response.body())
                .isEqualTo(HttpStatus.OK.value());
        var body = getJsonMapper().readTree(response.body());
        return body.path("measurements").get(0).path("value").asDouble();
    }
}
