// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.instance;

/**
 * Determines the ID of the instance the application is running on.
 */
public interface InstanceServiceInterface {

	/**
	 * Returns the instance ID.
	 *
	 * @return the instance ID
	 * @throws InstanceIdAWSException if the instance ID cannot be determined
	 */
	String instanceId();
}
