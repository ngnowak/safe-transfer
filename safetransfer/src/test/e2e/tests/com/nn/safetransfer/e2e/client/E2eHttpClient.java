package com.nn.safetransfer.e2e.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class E2eHttpClient {

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public E2eHttpClient(String baseUrl, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
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
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));

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

    private <T> T toResponseBody(HttpResponse<String> response, int expectedStatusCode, Class<T> responseType) throws IOException {
        assertThat(response.statusCode())
                .withFailMessage("Expected HTTP %s but got %s. Body: %s", expectedStatusCode, response.statusCode(), response.body())
                .isEqualTo(expectedStatusCode);
        return objectMapper.readValue(response.body(), responseType);
    }
}
