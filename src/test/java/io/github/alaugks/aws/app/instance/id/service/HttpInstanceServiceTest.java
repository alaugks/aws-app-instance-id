// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id.service;

import io.github.alaugks.aws.app.instance.id.AWSInstanceIdException;
import io.github.alaugks.aws.app.instance.id.InstanceService;
import io.github.alaugks.aws.app.instance.id.InstanceServiceInterface;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests that apply to every service based on {@link HttpInstanceService}. */
abstract class HttpInstanceServiceTest {

    /** Released when the test is over, so that blocked requests of the test server end. */
    protected final CountDownLatch release = new CountDownLatch(1);

    /** Returns a service whose requests are answered successfully by a test server. */
    abstract InstanceServiceInterface service() throws IOException;

    /** Returns a service whose requests are never answered by a test server, until {@link #release} is released. */
    abstract InstanceServiceInterface blockedService(Duration timeout) throws IOException;

    @Test
    void test_instanceId_shouldThrowAndKeepInterruptFlag_whenThreadIsInterrupted() throws IOException {
        InstanceServiceInterface service = this.service();

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(service::instanceId)
                .isInstanceOf(AWSInstanceIdException.class)
                .hasMessageContaining("Interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void test_instanceService_shouldUseFallback_whenTimeoutIsExceeded() throws IOException {
        InstanceService service = InstanceService.builder(this.blockedService(Duration.ofMillis(200)))
            .fallback(e -> "fallback-id")
            .build();

        assertThat(service.instanceId()).isEqualTo("fallback-id");
    }

    /** Blocks until the test is over, so the request runs into the timeout. */
    protected void awaitRelease() {
        try {
            this.release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
