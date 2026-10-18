// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.instance;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstanceServiceTest {

    @Mock
    private InstanceServiceInterface mockInstanceServiceInterface;

    @Test
    void test_instanceId_shouldReturnInstanceIdFromInterface() {
        InstanceService instanceService = InstanceService.builder(this.mockInstanceServiceInterface).build();
        
        String expectedInstanceId = "i-1234567890abcdef0";
        when(this.mockInstanceServiceInterface.instanceId()).thenReturn(expectedInstanceId);

        String result = instanceService.instanceId();

        assertThat(result).isEqualTo(expectedInstanceId);
        verify(this.mockInstanceServiceInterface, times(1)).instanceId();
    }

    @Test
    void test_instanceId_shouldReturnNull_whenInterfaceReturnsNull() {
        InstanceService instanceService = InstanceService.builder(this.mockInstanceServiceInterface).build();
        
        when(this.mockInstanceServiceInterface.instanceId()).thenReturn(null);

        String result = instanceService.instanceId();

        assertThat(result).isNull();
        verify(this.mockInstanceServiceInterface, times(1)).instanceId();
    }

    @Test
    void test_instanceId_shouldReturnEmptyString_whenInterfaceReturnsEmptyString() {
        InstanceService instanceService = InstanceService.builder(this.mockInstanceServiceInterface).build();
        
        when(this.mockInstanceServiceInterface.instanceId()).thenReturn("");

        String result = instanceService.instanceId();

        assertThat(result).isEmpty();
        verify(this.mockInstanceServiceInterface, times(1)).instanceId();
    }

    @Test
    void test_instanceId_shouldPropagateException_whenNoFallbackIsConfigured() {
        InstanceService instanceService = InstanceService.builder(this.mockInstanceServiceInterface).build();

        InstanceIdAWSException exception = new InstanceIdAWSException("failed");
        when(this.mockInstanceServiceInterface.instanceId()).thenThrow(exception);

        assertThatThrownBy(instanceService::instanceId).isSameAs(exception);
    }

    @Test
    void test_instanceId_shouldReturnFallback_whenServiceThrows() {
        AtomicReference<InstanceIdAWSException> received = new AtomicReference<>();
        InstanceService instanceService = InstanceService.builder(this.mockInstanceServiceInterface)
            .fallback(e -> {
                received.set(e);
                return "fallback-1";
            })
            .build();

        InstanceIdAWSException exception = new InstanceIdAWSException("failed");
        when(this.mockInstanceServiceInterface.instanceId()).thenThrow(exception);

        assertThat(instanceService.instanceId()).isEqualTo("fallback-1");
        assertThat(received.get()).isSameAs(exception);
    }

    @Test
    void test_instanceId_shouldNotUseFallback_whenServiceSucceeds() {
        InstanceService instanceService = InstanceService
            .builder(this.mockInstanceServiceInterface)
            .fallback(e -> "fallback-1").build();

        when(this.mockInstanceServiceInterface.instanceId()).thenReturn("i-123");

        assertThat(instanceService.instanceId()).isEqualTo("i-123");
    }
}
