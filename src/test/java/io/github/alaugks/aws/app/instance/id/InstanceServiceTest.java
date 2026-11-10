// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstanceServiceTest {

    private static AtomicReference<String> fallback;
    private static final AWSInstanceIdException FAILURE = new AWSInstanceIdException("failed");

    private static InstanceServiceInterface failing() {
        return () -> {
            throw FAILURE;
        };
    }

    @BeforeAll
    static void setup() {
        fallback = new AtomicReference<>();
        fallback.set(UUID.randomUUID().toString());
    }

    @Test
    void test_instanceId_shouldNotThrow() {
        InstanceServiceInterface service = () -> "foobar";
        AtomicReference<String> logged = new AtomicReference<>();

        InstanceService instanceService = InstanceService
            .builder(service)
            .onInstanceId(id -> logged.set("InstanceId: " + id))
            .build();

        assertThat(instanceService.instanceId()).isEqualTo("foobar");
        assertThat(logged.get()).isEqualTo("InstanceId: foobar");
    }

    @Test
    void test_instanceId_shouldNotLog_whenFallbackIsUsed() {
        AtomicReference<String> logged = new AtomicReference<>();

        InstanceService instanceService = InstanceService
            .builder(failing())
            .fallback(e -> "fallback-id")
            .onInstanceId(id -> logged.set(id))
            .build();

        assertThat(instanceService.instanceId()).isEqualTo("fallback-id");
        assertThat(logged.get()).isNull();
    }

    @Test
    void test_instanceId_shouldRethrow_whenNoFallbackIsConfigured() {
        InstanceService service = InstanceService.builder(failing()).build();

        assertThatThrownBy(service::instanceId).isSameAs(FAILURE);
    }

    @Test
    void test_instanceId_shouldThrow_whenFallbackIsConfigured() {
        InstanceService service = InstanceService.builder(failing()).fallback(e -> fallback.get()).build();

        assertThat(service.instanceId()).isEqualTo(fallback.get());
    }
}
