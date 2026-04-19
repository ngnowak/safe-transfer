package com.nn.safetransfer.e2e.client;

import org.springframework.http.HttpHeaders;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public abstract class E2eHttpClient {

    private static final String ACCEPT_LANGUAGE_VALUE = Locale.ENGLISH.toLanguageTag();

    private final String baseUrl;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    protected E2eHttpClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.jsonMapper = new JsonMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    protected JsonMapper getJsonMapper() {
        return jsonMapper;
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header(ACCEPT, APPLICATION_JSON_VALUE)
                .header(ACCEPT_LANGUAGE, ACCEPT_LANGUAGE_VALUE)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public <T> T get(String path, int expectedStatusCode, Class<T> responseType) throws IOException, InterruptedException {
        return toResponseBody(get(path), expectedStatusCode, responseType);
    }

    public HttpResponse<String> post(String path, Object body, Map<String, String> headers) throws IOException, InterruptedException {
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header(ACCEPT, APPLICATION_JSON_VALUE)
                .header(ACCEPT_LANGUAGE, ACCEPT_LANGUAGE_VALUE)
                .header(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)));

        headers.forEach(requestBuilder::header);

        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public <T> T post(
            String path,
            Object body,
            Map<String, String> headers,
            int expectedStatusCode,
            Class<T> responseType
    ) throws IOException, InterruptedException {
        return toResponseBody(post(path, body, headers), expectedStatusCode, responseType);
    }

    private <T> T toResponseBody(HttpResponse<String> response, int expectedStatusCode, Class<T> responseType) {
        assertThat(response.statusCode())
                .withFailMessage("Expected HTTP %s but got %s. Body: %s", expectedStatusCode, response.statusCode(), response.body())
                .isEqualTo(expectedStatusCode);
        return jsonMapper.readValue(response.body(), responseType);
    }
}
