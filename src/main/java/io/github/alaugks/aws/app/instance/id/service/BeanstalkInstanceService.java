// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id.service;

import io.github.alaugks.aws.app.instance.id.AWSInstanceIdException;
import io.github.alaugks.aws.app.instance.id.InstanceServiceInterface;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * {@link InstanceServiceInterface} for applications running on AWS Elastic Beanstalk (EC2-based).
 * The instance ID is read from the EC2 Instance Metadata Service using IMDSv2.
 */
public class BeanstalkInstanceService implements InstanceServiceInterface {

	/** Default timeout for the requests to the metadata service, which is local to the instance. */
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(1);

	/** Client used for the requests to the metadata service. */
	private final HttpClient client;

	/** Request for the IMDSv2 session token. */
	private final HttpRequest.Builder tokenRequest;

	/** Request for the instance ID; the token header is added before sending. */
	private final HttpRequest.Builder instanceIdRequest;

	private BeanstalkInstanceService(
		HttpClient client,
		HttpRequest.Builder tokenRequest,
		HttpRequest.Builder instanceIdRequest
	) {
		this.client = client;
		this.tokenRequest = tokenRequest;
		this.instanceIdRequest = instanceIdRequest;
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
	 * Returns the EC2 instance ID from the metadata service (IMDSv2).
	 *
	 * @return the EC2 instance ID
	 * @throws AWSInstanceIdException if the instance ID cannot be determined
	 */
	@Override
	public String instanceId() {
		try {
			return this.fetchInstanceId(this.fetchIMDSToken());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AWSInstanceIdException("Interrupted while fetching the instance ID", e);
		} catch (IOException e) {
			throw new AWSInstanceIdException("Could not fetch the instance ID: " + e.getMessage(), e);
		}
	}

	/** Requests an IMDSv2 session token. */
	private String fetchIMDSToken() throws IOException, InterruptedException {
		return this.send(this.tokenRequest.copy().build());
	}

	/** Reads the instance ID from the metadata service using the given token. */
	private String fetchInstanceId(String token) throws IOException, InterruptedException {
		return this.send(this.instanceIdRequest.copy().header("X-aws-ec2-metadata-token", token).build());
	}

	/** Sends the request and returns the body; any status other than 200 is treated as an error. */
	private String send(HttpRequest request) throws IOException, InterruptedException {
		HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new AWSInstanceIdException("Unexpected status " + response.statusCode() + " from " + request.uri());
		}
		return response.body();
	}

	/**
	 * Builder for {@link BeanstalkInstanceService}.
	 */
	public static class Builder {

		private HttpClient client;

		private Duration timeout = DEFAULT_TIMEOUT;

		private Builder() {
		}

		/**
		 * Sets the timeout for connecting and for each response. Defaults to 1 second. If exceeded, an
		 * {@link AWSInstanceIdException} is thrown.
		 *
		 * @param timeout the timeout
		 * @return this builder
		 */
		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		/** Replaces the client, used by the tests to redirect the requests to a test server. */
		Builder httpClient(HttpClient client) {
			this.client = client;
			return this;
		}

		/**
		 * Builds the {@link BeanstalkInstanceService}.
		 *
		 * @return the service
		 */
		public BeanstalkInstanceService build() {
			HttpClient httpClient = this.client != null
				? this.client
				: HttpClient.newBuilder().connectTimeout(this.timeout).build();

			HttpRequest.Builder token = HttpRequest.newBuilder()
				.uri(URI.create("http://169.254.169.254/latest/api/token"))
				.header("X-aws-ec2-metadata-token-ttl-seconds", "21600")
				.timeout(this.timeout)
				.PUT(HttpRequest.BodyPublishers.noBody());

			HttpRequest.Builder instanceId = HttpRequest.newBuilder()
				.uri(URI.create("http://169.254.169.254/latest/meta-data/instance-id"))
				.timeout(this.timeout)
				.GET();

			return new BeanstalkInstanceService(httpClient, token, instanceId);
		}
	}
}
