// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id.service;

import io.github.alaugks.aws.app.instance.id.InstanceIdAWSException;
import io.github.alaugks.aws.app.instance.id.InstanceServiceInterface;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link InstanceServiceInterface} for applications running on AWS Fargate.
 * The instance ID is the ID of the ECS task, read from the ECS Task Metadata Endpoint (version 4).
 * Requires the environment variable {@code ECS_CONTAINER_METADATA_URI_V4}, which ECS sets in every container.
 */
public class FargateInstanceService implements InstanceServiceInterface {

	/** Environment variable with the base URL of the ECS Task Metadata Endpoint, set by ECS in every container. */
	private static final String METADATA_URI_ENV = "ECS_CONTAINER_METADATA_URI_V4";

	/** Default timeout for the request to the metadata endpoint, which is local to the task. */
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(1);

	private static final Pattern TASK_ARN = Pattern.compile("\"TaskARN\"\\s*:\\s*\"([^\"]+)\"");

	private final HttpClient client;

	/** Request for the task metadata; {@code null} if neither configured nor the environment variable is set. */
	private final HttpRequest.Builder taskRequest;

	private FargateInstanceService(HttpRequest.Builder taskRequest, Duration timeout) {
		this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
		this.taskRequest = taskRequest;
	}

	/**
	 * Creates a new {@link Builder}.
	 *
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns the task ID from the ECS Task Metadata Endpoint.
	 *
	 * @return the task ID
	 * @throws InstanceIdAWSException if the task ID cannot be determined
	 */
	@Override
	public String instanceId() {
		if (this.taskRequest == null) {
			throw new InstanceIdAWSException("Environment variable " + METADATA_URI_ENV + " is not set");
		}
		try {
			return this.fetchTaskId();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InstanceIdAWSException("Interrupted while fetching the task ID", e);
		} catch (IOException e) {
			throw new InstanceIdAWSException("Could not fetch the task ID: " + e.getMessage(), e);
		}
	}

	/** Reads the task ARN from the task metadata and returns its last segment. */
	private String fetchTaskId() throws IOException, InterruptedException {
		HttpRequest request = this.taskRequest.copy().build();
		HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new InstanceIdAWSException("Unexpected status " + response.statusCode() + " from " + request.uri());
		}
		Matcher matcher = TASK_ARN.matcher(response.body());
		if (!matcher.find()) {
			throw new InstanceIdAWSException("No TaskARN in the response from " + request.uri());
		}
		String taskArn = matcher.group(1);
		return taskArn.substring(taskArn.lastIndexOf('/') + 1);
	}

	/** Builds {@code GET <metadataUri>/task}; {@code null} if the metadata URI is not set. */
	static HttpRequest.Builder defaultTaskRequest(String metadataUri, Duration timeout) {
		if (metadataUri == null || metadataUri.isBlank()) {
			return null;
		}
		return HttpRequest.newBuilder()
			.uri(URI.create(metadataUri + "/task"))
			.timeout(timeout)
			.GET();
	}

	/**
	 * Builder for {@link FargateInstanceService}.
	 */
	public static class Builder {

		private HttpRequest.Builder taskRequest;

		private Duration timeout = DEFAULT_TIMEOUT;

		private Builder() {
		}

		/**
		 * Sets the timeout for connecting and for the response. Defaults to 1 second. If exceeded, an
		 * {@link InstanceIdAWSException} is thrown.
		 *
		 * @param timeout the timeout
		 * @return this builder
		 */
		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		/** Replaces the task request, used by the tests to redirect it to a test server. */
		Builder taskRequest(HttpRequest.Builder request) {
			this.taskRequest = request;
			return this;
		}

		/**
		 * Builds the {@link FargateInstanceService}.
		 *
		 * @return the service
		 */
		public FargateInstanceService build() {
			HttpRequest.Builder request = this.taskRequest;
			if (request == null) {
				request = defaultTaskRequest(System.getenv(METADATA_URI_ENV), this.timeout);
			}
			return new FargateInstanceService(request, this.timeout);
		}
	}
}
