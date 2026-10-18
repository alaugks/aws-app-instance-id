// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id;

import java.util.function.Function;

/**
 * Provides the instance ID of an {@link InstanceServiceInterface} and optionally
 * replaces it by a fallback if it cannot be determined.
 */
public class InstanceService {

	private final InstanceServiceInterface service;
	private final Function<InstanceIdAWSException, String> fallback;

	private InstanceService(InstanceServiceInterface service, Function<InstanceIdAWSException, String> fallback) {
		this.service = service;
		this.fallback = fallback;
	}

	/**
	 * Creates a new {@link Builder}.
	 *
	 * @param instanceService the service that determines the instance ID
	 * @return a new builder
	 */
	public static Builder builder(InstanceServiceInterface instanceService) {
		return new Builder(instanceService);
	}

	/**
	 * Returns the instance ID, or the fallback ID if the instance ID cannot be determined
	 * and a fallback is configured.
	 *
	 * @return the instance ID
	 * @throws InstanceIdAWSException if the instance ID cannot be determined and no fallback is configured
	 */
	public String instanceId() {
		try {
			return this.service.instanceId();
		} catch (InstanceIdAWSException e) {
			if (this.fallback == null) {
				throw e;
			}
			return this.fallback.apply(e);
		}
	}

	/**
	 * Builder for {@link InstanceService}.
	 */
	public static class Builder {

		private final InstanceServiceInterface instanceService;

		private Function<InstanceIdAWSException, String> fallback;

		private Builder(InstanceServiceInterface instanceService) {
			this.instanceService = instanceService;
		}

		/**
		 * Sets the fallback. Optional; without it, the exception is passed on to the caller.
		 *
		 * @param fallback receives the exception and returns the instance ID to use instead
		 * @return this builder
		 */
		public Builder fallback(Function<InstanceIdAWSException, String> fallback) {
			this.fallback = fallback;
			return this;
		}

		/**
		 * Builds the {@link InstanceService}.
		 *
		 * @return the service
		 */
		public InstanceService build() {
			return new InstanceService(this.instanceService, this.fallback);
		}
	}
}
