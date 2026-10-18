// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.instance.service;

import io.github.alaugks.aws.instance.InstanceServiceInterface;

/**
 * {@link InstanceServiceInterface} that returns a configured instance ID.
 * Suitable for local development and tests.
 */
public class MockInstanceService implements InstanceServiceInterface {

	private final String mockInstanceId;

	private MockInstanceService(String mockInstanceId) {
		this.mockInstanceId = mockInstanceId;
	}

	/**
	 * Returns the configured instance ID.
	 *
	 * @return the configured instance ID
	 */
	@Override
	public String instanceId() {
		return this.mockInstanceId;
	}

	/**
	 * Creates a new {@link Builder}.
	 *
	 * @param mockInstanceId the instance ID to return
	 * @return a new builder
	 */
	public static Builder builder(String mockInstanceId) {
		return new Builder(mockInstanceId);
	}

	/**
	 * Builder for {@link MockInstanceService}.
	 */
	public static class Builder {

		private final String mockInstanceId;

		private Builder(String mockInstanceId) {
			this.mockInstanceId = mockInstanceId;
		}

		/**
		 * Builds the {@link MockInstanceService}.
		 *
		 * @return the service
		 */
		public MockInstanceService build() {
			return new MockInstanceService(this.mockInstanceId);
		}
	}
}
