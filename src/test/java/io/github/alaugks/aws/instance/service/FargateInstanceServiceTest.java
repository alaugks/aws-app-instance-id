// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.instance.service;

import com.sun.net.httpserver.HttpServer;
import io.github.alaugks.aws.instance.InstanceIdAWSException;
import io.github.alaugks.aws.instance.InstanceService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FargateInstanceServiceTest {

    // https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-metadata-endpoint-v4-fargate-examples.html#task-metadata-endpoint-v4-fargate-example-task-metadata-response
    private static final String RESPONSE = """
        {
          "Cluster": "arn:aws:ecs:us-east-1:123456789012:cluster/MyEmptyCluster",
          "TaskARN": "arn:aws:ecs:us-east-1:123456789012:task/MyEmptyCluster/bfa2636268144d039771334145e490c5",
          "Family": "sample-fargate",
          "Revision": "5",
          "DesiredStatus": "RUNNING",
          "KnownStatus": "RUNNING",
          "Limits": {
            "CPU": 0.25,
            "Memory": 512\s
          },
          "PullStartedAt": "2023-07-21T15:45:33.532811081Z",
          "PullStoppedAt": "2023-07-21T15:45:38.541068435Z",
          "AvailabilityZone": "us-east-1d",
          "Containers": [
            {
              "DockerId": "bfa2636268144d039771334145e490c5-1117626119",
              "Name": "curl-image",
              "DockerName": "curl-image",
              "Image": "curlimages/curl",
              "ImageID": "sha256:daf3f46a2639c1613b25e85c9ee4193af8a1d538f92483d67f9a3d7f21721827",
              "Labels": {
                "com.amazonaws.ecs.cluster": "arn:aws:ecs:us-east-1:123456789012:cluster/MyEmptyCluster",
                "com.amazonaws.ecs.container-name": "curl-image",
                "com.amazonaws.ecs.task-arn": "arn:aws:ecs:us-east-1:123456789012:task/MyEmptyCluster/bfa2636268144d039771334145e490c5",
                "com.amazonaws.ecs.task-definition-family": "sample-fargate",
                "com.amazonaws.ecs.task-definition-version": "5"
              },
              "DesiredStatus": "RUNNING",
              "KnownStatus": "RUNNING",
              "Limits": { "CPU": 128 },
              "CreatedAt": "2023-07-21T15:45:44.91368314Z",
              "StartedAt": "2023-07-21T15:45:44.91368314Z",
              "Type": "NORMAL",
              "Networks": [
                {
                  "NetworkMode": "awsvpc",
                  "IPv4Addresses": ["172.31.42.189"],
                  "AttachmentIndex": 0,
                  "MACAddress": "0e:98:9f:33:76:d3",
                  "IPv4SubnetCIDRBlock": "172.31.32.0/20",
                  "DomainNameServers": ["172.31.0.2"],
                  "DomainNameSearchList": ["ec2.internal"],
                  "PrivateDNSName": "ip-172-31-42-189.ec2.internal",
                  "SubnetGatewayIpv4Address": "172.31.32.1/20"
                }
              ],
              "ContainerARN": "arn:aws:ecs:us-east-1:123456789012:container/MyEmptyCluster/bfa2636268144d039771334145e490c5/da6cccf7-1178-400c-afdf-7536173ee209",
              "Snapshotter": "overlayfs"
            },
            {
              "DockerId": "bfa2636268144d039771334145e490c5-3681984407",
              "Name": "fargate-app",
              "DockerName": "fargate-app",
              "Image": "public.ecr.aws/docker/library/httpd:latest",
              "ImageID": "sha256:8059bdd0058510c03ae4c808de8c4fd2c1f3c1b6d9ea75487f1e5caa5ececa02",
              "Labels": {
                "com.amazonaws.ecs.cluster": "arn:aws:ecs:us-east-1:123456789012:cluster/MyEmptyCluster",
                "com.amazonaws.ecs.container-name": "fargate-app",
                "com.amazonaws.ecs.task-arn": "arn:aws:ecs:us-east-1:123456789012:task/MyEmptyCluster/bfa2636268144d039771334145e490c5",
                "com.amazonaws.ecs.task-definition-family": "sample-fargate",
                "com.amazonaws.ecs.task-definition-version": "5"
              },
              "DesiredStatus": "RUNNING",
              "KnownStatus": "RUNNING",
              "Limits": { "CPU": 2 },
              "CreatedAt": "2023-07-21T15:45:44.954460255Z",
              "StartedAt": "2023-07-21T15:45:44.954460255Z",
              "Type": "NORMAL",
              "Networks": [
                {
                  "NetworkMode": "awsvpc",
                  "IPv4Addresses": ["172.31.42.189"],
                  "AttachmentIndex": 0,
                  "MACAddress": "0e:98:9f:33:76:d3",
                  "IPv4SubnetCIDRBlock": "172.31.32.0/20",
                  "DomainNameServers": ["172.31.0.2"],
                  "DomainNameSearchList": ["ec2.internal"],
                  "PrivateDNSName": "ip-172-31-42-189.ec2.internal",
                  "SubnetGatewayIpv4Address": "172.31.32.1/20"
                }
              ],
              "ContainerARN": "arn:aws:ecs:us-east-1:123456789012:container/MyEmptyCluster/bfa2636268144d039771334145e490c5/f65b461d-aa09-4acb-a579-9785c0530cbc",
              "Snapshotter": "overlayfs"
            }
          ],
          "LaunchType": "FARGATE",
          "ClockDrift": {
            "ClockErrorBound": 0.446931,
            "ReferenceTimestamp": "2023-07-21T16:09:17Z",
            "ClockSynchronizationStatus": "SYNCHRONIZED"
          },
          "EphemeralStorageMetrics": {
            "Utilized": 261,
            "Reserved": 20496
          }
        }
        """;

    private HttpServer server;
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void tearDown() {
        this.release.countDown();
        if (this.server != null) {
            this.server.stop(0);
        }
    }

    private HttpRequest.Builder startMetadataServer(int status, String body) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/task", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        this.server.start();
        return HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + this.server.getAddress().getPort() + "/task"))
            .GET();
    }

    @Test
    void test_instanceId_shouldReturnTaskId_whenMetadataEndpointIsAvailable() throws IOException {
        HttpRequest.Builder request = this.startMetadataServer(200, RESPONSE);
        FargateInstanceService service = FargateInstanceService.builder()
            .taskRequest(request)
            .build();

        assertThat(service.instanceId()).isEqualTo("bfa2636268144d039771334145e490c5");
    }

    @Test
    void test_instanceId_shouldThrow_whenMetadataEndpointRespondsWithError() throws IOException {
        HttpRequest.Builder request = this.startMetadataServer(500, "");
        FargateInstanceService service = FargateInstanceService.builder().taskRequest(request).build();

        assertThatThrownBy(service::instanceId)
            .isInstanceOf(InstanceIdAWSException.class)
            .hasMessageContaining("500");
    }

    @Test
    void test_instanceId_shouldThrow_whenMetadataEndpointIsNotConfigured() {
        Assumptions.assumeTrue(System.getenv("ECS_CONTAINER_METADATA_URI_V4") == null);
        FargateInstanceService service = FargateInstanceService.builder().build();

        assertThatThrownBy(service::instanceId)
            .isInstanceOf(InstanceIdAWSException.class)
            .hasMessageContaining("ECS_CONTAINER_METADATA_URI_V4");
    }

    @Test
    void test_defaultTaskRequest_shouldBuildTaskRequestWithTimeout() {
        HttpRequest request = FargateInstanceService.defaultTaskRequest("http://169.254.170.2/v4/abc", Duration.ofMillis(300)).build();

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri()).hasToString("http://169.254.170.2/v4/abc/task");
        assertThat(request.timeout()).contains(Duration.ofMillis(300));
    }

    @Test
    void test_instanceService_shouldUseFallback_whenTimeoutIsExceeded() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/task", exchange -> {
            try {
                this.release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        this.server.start();
        HttpRequest.Builder request = FargateInstanceService.defaultTaskRequest(
            "http://127.0.0.1:" + this.server.getAddress().getPort(),
            Duration.ofMillis(200)
        );

        InstanceService service = InstanceService.builder(
                FargateInstanceService.builder().taskRequest(request).timeout(Duration.ofMillis(200)).build())
            .fallback(e -> "fallback-id")
            .build();

        assertThat(service.instanceId()).isEqualTo("fallback-id");
    }

    @Test
    void test_defaultTaskRequest_shouldReturnNull_whenMetadataUriIsMissing() {
        assertThat(FargateInstanceService.defaultTaskRequest(null, Duration.ofSeconds(1))).isNull();
        assertThat(FargateInstanceService.defaultTaskRequest(" ", Duration.ofSeconds(1))).isNull();
    }
}
