// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstanceServiceTest {

    private static final AWSInstanceIdException FAILURE = new AWSInstanceIdException("failed");

    private static InstanceServiceInterface failing() {
        return () -> {
            throw FAILURE;
        };
    }

    @Test
    void test_instanceId_shouldRethrow_whenNoFallbackIsConfigured_xxxxx() {
        InstanceServiceInterface service = () -> "foobar";

        assertThat(InstanceService.builder(service).build().instanceId()).isEqualTo("foobar");
    }

    @Test
    void test_instanceId_shouldRethrow_whenNoFallbackIsConfigured() {
        InstanceService service = InstanceService.builder(failing()).build();

        assertThatThrownBy(service::instanceId).isSameAs(FAILURE);
    }

    @Test
    void test_instanceId_shouldReturnFallback_whenServiceFails() {
        InstanceService service = InstanceService.builder(failing()).fallback(e -> "fallback-id").build();

        assertThat(service.instanceId()).isEqualTo("fallback-id");
    }

    @Test
    void test_instanceId_shouldPassExceptionToFallback() {
        InstanceService service = InstanceService.builder(failing()).fallback(e -> e.getMessage() + "-id").build();

        assertThat(service.instanceId()).isEqualTo("failed-id");
    }
}
