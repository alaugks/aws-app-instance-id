// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Provides the instance ID of an {@link InstanceServiceInterface}. Optionally, a callback
 * ({@link Builder#onInstanceId(Consumer)}) can work with the determined instance ID, and a fallback
 * ({@link Builder#fallback(Function)}) can replace it if it cannot be determined.
 */
public class InstanceService {

	private final InstanceServiceInterface service;
	private final Function<AWSInstanceIdException, String> fallback;
	private final Consumer<String> onInstanceId;

	private InstanceService(Builder builder) {
		this.service = builder.instanceService;
		this.fallback = builder.fallback;
		this.onInstanceId = builder.onInstanceId;
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
	 * <p>
	 * If an {@link Builder#onInstanceId(Consumer) onInstanceId} callback is configured, it is called with
	 * the determined instance ID before it is returned. It is not called when the fallback is used.
	 *
	 * @return the instance ID
	 * @throws AWSInstanceIdException if the instance ID cannot be determined and no fallback is configured
	 */
	public String instanceId() {
		try {
			String instanceId = this.service.instanceId();
			if (this.onInstanceId != null) {
				this.onInstanceId.accept(instanceId);
			}
			return instanceId;
		} catch (AWSInstanceIdException e) {
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

		private Function<AWSInstanceIdException, String> fallback;
		private Consumer<String> onInstanceId;

		private Builder(InstanceServiceInterface instanceService) {
			this.instanceService = instanceService;
		}

		/**
		 * Sets a callback to work with the instance ID, e.g. to log it. Optional.
		 * <p>
		 * The callback is called with the instance ID each time it was determined successfully. It is
		 * not called when the fallback is used. The instance ID itself is not changed by the callback.
		 *
		 * @param onInstanceId receives the determined instance ID
		 * @return this builder
		 */
		public Builder onInstanceId(Consumer<String> onInstanceId) {
			this.onInstanceId = onInstanceId;
			return this;
		}

		/**
		 * Sets the fallback. Optional; without it, the exception is passed on to the caller.
		 *
		 * @param fallback receives the exception and returns the instance ID to use instead
		 * @return this builder
		 */
		public Builder fallback(Function<AWSInstanceIdException, String> fallback) {
			this.fallback = fallback;
			return this;
		}

		/**
		 * Builds the {@link InstanceService}.
		 *
		 * @return the service
		 */
		public InstanceService build() {
			return new InstanceService(this);
		}
	}
}
