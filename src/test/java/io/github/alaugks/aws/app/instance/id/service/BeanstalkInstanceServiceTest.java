// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id.service;

import com.sun.net.httpserver.HttpServer;
import io.github.alaugks.aws.app.instance.id.InstanceIdAWSException;
import io.github.alaugks.aws.app.instance.id.InstanceService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeanstalkInstanceServiceTest {

    private HttpServer server;
    private HttpClient client;
    private int tokenStatus;
    private final List<String> requests = new ArrayList<>();
    private String instanceIdTokenHeader;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws IOException {
        this.tokenStatus = 200;

        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/latest/api/token", exchange -> {
            this.requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            respond(exchange, this.tokenStatus, "test-token");
        });
        this.server.createContext("/latest/meta-data/instance-id", exchange -> {
            this.requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
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
    void test_instanceId_shouldThrow_whenTokenRequestFails() {
        this.tokenStatus = 500;
        BeanstalkInstanceService service = BeanstalkInstanceService.builder().httpClient(this.client).build();

        assertThatThrownBy(service::instanceId)
            .isInstanceOf(InstanceIdAWSException.class)
            .hasMessageContaining("500");
        assertThat(this.requests).containsExactly("PUT /latest/api/token");
    }

    @Test
    void test_instanceId_shouldThrow_whenMetadataServiceIsUnreachable() {
        this.server.stop(0);
        BeanstalkInstanceService service = BeanstalkInstanceService.builder().httpClient(this.client).build();

        assertThatThrownBy(service::instanceId)
            .isInstanceOf(InstanceIdAWSException.class)
            .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void test_instanceService_shouldUseFallback_whenTimeoutIsExceeded() {
        this.server.removeContext("/latest/api/token");
        this.server.createContext("/latest/api/token", exchange -> {
            // Blocks until the test is over, so the request runs into the timeout
            try {
                this.release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        InstanceService service = InstanceService.builder(
                BeanstalkInstanceService.builder()
                    .httpClient(this.client)
                    .timeout(Duration.ofMillis(200))
                    .build())
            .fallback(e -> "fallback-id")
            .build();

        assertThat(service.instanceId()).isEqualTo("fallback-id");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
