package com.doodle.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.2-alpine3.23");

    static {
        POSTGRES.start();
    }

    @LocalServerPort
    protected int port;

    protected final RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.contexts", () -> "dev");
    }

    protected record TestUser(String email, String password, UUID id) {
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected JsonNode readJsonBody(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to parse JSON response body", ex);
        }
    }

    protected ResponseEntity<String> get(String path, TestUser user) {
        return exchange(HttpMethod.GET, path, null, user);
    }

    protected ResponseEntity<String> delete(String path, TestUser user) {
        return exchange(HttpMethod.DELETE, path, null, user);
    }

    protected ResponseEntity<String> post(String path, Object body, TestUser user) {
        return exchange(HttpMethod.POST, path, body, user);
    }

    protected ResponseEntity<String> patch(String path, Object body, TestUser user) {
        return exchange(HttpMethod.PATCH, path, body, user);
    }

    protected ResponseEntity<String> exchange(HttpMethod method, String path, Object body, TestUser user) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (user != null) {
                headers.setBasicAuth(user.email(), user.password(), StandardCharsets.UTF_8);
            }

            String jsonBody = body == null ? null : objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            return restTemplate.exchange(url(path), method, entity, String.class);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(ex.getResponseHeaders())
                    .body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            throw new RuntimeException("HTTP call failed for " + method + " " + path, ex);
        }
    }

    protected TestUser registerUser(String displayNamePrefix) {
        String email = displayNamePrefix.toLowerCase().replace(" ", ".")
                + "." + UUID.randomUUID() + "@example.com";
        String password = "password123";
        Map<String, Object> request = Map.of(
                "email", email,
                "password", password,
                "displayName", displayNamePrefix
        );

        ResponseEntity<String> response = post("/api/users/register", request, null);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to register test user: " + response);
        }
        UUID id = UUID.fromString(readJsonBody(response).get("id").asText());
        return new TestUser(email, password, id);
    }

    protected UUID createSlot(TestUser user, String startIso, String endIso) {
        Map<String, Object> request = Map.of("startTime", startIso, "endTime", endIso);
        ResponseEntity<String> response = post("/api/slots", request, user);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to create slot: " + response);
        }
        return UUID.fromString(readJsonBody(response).get("id").asText());
    }

    protected ResponseEntity<String> scheduleMeeting(TestUser user, UUID slotId, String title) {
        return scheduleMeeting(user, slotId, title, List.of());
    }

    protected ResponseEntity<String> scheduleMeeting(TestUser user, UUID slotId, String title, List<UUID> participantIds) {
        Map<String, Object> request = Map.of(
                "slotId", slotId,
                "title", title,
                "description", "integration test meeting",
                "participantIds", participantIds
        );
        return post("/api/meetings", request, user);
    }
}
