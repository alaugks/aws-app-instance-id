// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id;

/**
 * Thrown when the instance ID cannot be determined, e.g. because the AWS metadata service
 * is unreachable or responds with an unexpected status.
 * <p>
 * This is an unchecked exception. Where available, the original error is attached as cause.
 */
public class InstanceIdAWSException extends RuntimeException {

	/**
	 * Creates an exception with the given message.
	 *
	 * @param message the detail message
	 */
	public InstanceIdAWSException(String message) {
		super(message);
	}

	/**
	 * Creates an exception with the given message and cause.
	 *
	 * @param message the detail message
	 * @param cause   the original error
	 */
	public InstanceIdAWSException(String message, Throwable cause) {
		super(message, cause);
	}
}
