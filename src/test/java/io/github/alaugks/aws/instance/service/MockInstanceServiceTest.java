// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.instance.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockInstanceServiceTest {

    @Test
    void test_instanceId_shouldReturnConfiguredValue() {
        assertThat(MockInstanceService.builder("mock-1").build().instanceId()).isEqualTo("mock-1");
    }
}
