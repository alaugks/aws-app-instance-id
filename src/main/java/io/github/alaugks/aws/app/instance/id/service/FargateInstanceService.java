// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id.service;

import io.github.alaugks.aws.app.instance.id.AWSInstanceIdException;
import io.github.alaugks.aws.app.instance.id.InstanceServiceInterface;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link InstanceServiceInterface} for applications running on AWS Fargate.
 * The instance ID is the ID of the ECS task, read from the ECS Task Metadata Endpoint (version 4).
 * Requires the environment variable {@code ECS_CONTAINER_METADATA_URI_V4}, which ECS sets in every container.
 */
public class FargateInstanceService extends HttpInstanceService {

	/** Environment variable with the base URL of the ECS Task Metadata Endpoint, set by ECS in every container. */
	private static final String METADATA_URI_ENV = "ECS_CONTAINER_METADATA_URI_V4";

	private static final Pattern TASK_ARN = Pattern.compile("\"TaskARN\"\\s*:\\s*\"([^\"]+)\"");

	/** Request for the task metadata; {@code null} if the environment variable is not set. */
	private final HttpRequest.Builder taskRequest;

	private FargateInstanceService(HttpClient client, HttpRequest.Builder taskRequest) {
		super(client);
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

	/** Reads the task ID from the ECS Task Metadata Endpoint. */
	@Override
	String fetchInstanceId() throws IOException, InterruptedException {
		if (this.taskRequest == null) {
			throw new AWSInstanceIdException("Environment variable " + METADATA_URI_ENV + " is not set");
		}
		return this.fetchTaskId();
	}

	/** Reads the task ARN from the task metadata and returns its last segment. */
	private String fetchTaskId() throws IOException, InterruptedException {
		HttpRequest request = this.taskRequest.copy().build();
		Matcher matcher = TASK_ARN.matcher(this.send(request));
		if (!matcher.find()) {
			throw new AWSInstanceIdException("No TaskARN in the response from " + request.uri());
		}
		String taskArn = matcher.group(1);
		return taskArn.substring(taskArn.lastIndexOf('/') + 1);
	}

	/**
	 * Builder for {@link FargateInstanceService}.
	 */
	public static class Builder extends AbstractBuilder<Builder> {

		private Builder() {
		}

		/**
		 * Builds the {@link FargateInstanceService}.
		 *
		 * @return the service
		 */
		public FargateInstanceService build() {
			String metadataUri = System.getenv(METADATA_URI_ENV);
			HttpRequest.Builder request = null;
			if (metadataUri != null && !metadataUri.isBlank()) {
				request = HttpRequest.newBuilder().uri(URI.create(metadataUri + "/task")).timeout(this.timeout()).GET();
			}
			return new FargateInstanceService(this.httpClient(), request);
		}
	}
}
