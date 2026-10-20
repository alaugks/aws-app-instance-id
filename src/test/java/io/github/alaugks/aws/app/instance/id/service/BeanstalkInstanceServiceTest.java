// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id.service;

import com.sun.net.httpserver.HttpServer;
import io.github.alaugks.aws.app.instance.id.AWSInstanceIdException;
import io.github.alaugks.aws.app.instance.id.InstanceServiceInterface;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeanstalkInstanceServiceTest extends HttpInstanceServiceTest {

    private HttpServer server;
    private HttpClient client;
    private int tokenStatus;
    private final List<String> requests = new ArrayList<>();
    private final List<String> hosts = new ArrayList<>();
    private String instanceIdTokenHeader;

    @BeforeEach
    void setUp() throws IOException {
        this.tokenStatus = 200;

        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/latest/api/token", exchange -> {
            this.requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            this.hosts.add(exchange.getRequestHeaders().getFirst("Host"));
            respond(exchange, this.tokenStatus, "test-token");
        });
        this.server.createContext("/latest/meta-data/instance-id", exchange -> {
            this.requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            this.hosts.add(exchange.getRequestHeaders().getFirst("Host"));
            this.instanceIdTokenHeader = exchange.getRequestHeaders().getFirst("X-aws-ec2-metadata-token");
            respond(exchange, 200, "i-1234567890abcdef0");
        });
        this.server.start();

        this.client = HttpClient.newBuilder()
            .proxy(ProxySelector.of(this.server.getAddress()))
            .build();
    }

    @AfterEach
    void tearDown() {
        this.release.countDown();
        this.server.stop(0);
    }

    @Test
    void test_instanceId_shouldReturnInstanceIdFromMetadataService() {
        BeanstalkInstanceService service = BeanstalkInstanceService.builder().httpClient(this.client).build();

        assertThat(service.instanceId()).isEqualTo("i-1234567890abcdef0");
        assertThat(this.requests).containsExactly(
            "PUT /latest/api/token",
            "GET /latest/meta-data/instance-id"
        );
        assertThat(this.instanceIdTokenHeader).isEqualTo("test-token");
    }

    @Test
    void test_instanceId_shouldUseMetadataServiceAddress() {
        BeanstalkInstanceService service = BeanstalkInstanceService.builder().httpClient(this.client).build();

        service.instanceId();

        assertThat(this.hosts).containsExactly("169.254.169.254", "169.254.169.254");
    }

    @Test
    void test_instanceId_shouldThrow_whenTokenRequestFails() {
        this.tokenStatus = 500;
        BeanstalkInstanceService service = BeanstalkInstanceService.builder().httpClient(this.client).build();

        assertThatThrownBy(service::instanceId)
            .isInstanceOf(AWSInstanceIdException.class)
            .hasMessageContaining("500");
        assertThat(this.requests).containsExactly("PUT /latest/api/token");
    }

    @Test
    void test_instanceId_shouldThrow_whenMetadataServiceIsUnreachable() {
        this.server.stop(0);
        BeanstalkInstanceService service = BeanstalkInstanceService.builder().httpClient(this.client).build();

        assertThatThrownBy(service::instanceId)
            .isInstanceOf(AWSInstanceIdException.class)
            .hasCauseInstanceOf(IOException.class);
    }

    @Override
    InstanceServiceInterface service() {
        return BeanstalkInstanceService.builder().httpClient(this.client).build();
    }

    @Override
    InstanceServiceInterface blockedService(Duration timeout) {
        this.server.removeContext("/latest/api/token");
        this.server.createContext("/latest/api/token", exchange -> {
            this.awaitRelease();
            exchange.close();
        });
        return BeanstalkInstanceService.builder().httpClient(this.client).timeout(timeout).build();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void test_build_shouldCreateDefaultHttpClient_whenNoneIsGiven() {
        assertThat(BeanstalkInstanceService.builder().timeout(Duration.ofMillis(100)).build()).isNotNull();
    }
}
