// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id.service;

import io.github.alaugks.aws.app.instance.id.InstanceServiceInterface;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

/**
 * {@link InstanceServiceInterface} for applications running on AWS Elastic Beanstalk (EC2-based).
 * The instance ID is read from the EC2 Instance Metadata Service using IMDSv2.
 */
public class BeanstalkInstanceService extends HttpInstanceService {

	/** Request for the IMDSv2 session token. */
	private final HttpRequest.Builder tokenRequest;

	/** Request for the instance ID; the token header is added before sending. */
	private final HttpRequest.Builder instanceIdRequest;

	private BeanstalkInstanceService(
		HttpClient client,
		HttpRequest.Builder tokenRequest,
		HttpRequest.Builder instanceIdRequest
	) {
		super(client);
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

	/** Reads the EC2 instance ID from the metadata service (IMDSv2). */
	@Override
	String fetchInstanceId() throws IOException, InterruptedException {
		return this.fetchInstanceId(this.fetchIMDSToken());
	}

	/** Requests an IMDSv2 session token. */
	private String fetchIMDSToken() throws IOException, InterruptedException {
		return this.send(this.tokenRequest.copy().build());
	}

	/** Reads the instance ID from the metadata service using the given token. */
	private String fetchInstanceId(String token) throws IOException, InterruptedException {
		return this.send(this.instanceIdRequest.copy().header("X-aws-ec2-metadata-token", token).build());
	}

	/**
	 * Builder for {@link BeanstalkInstanceService}.
	 */
	public static class Builder extends AbstractBuilder<Builder> {

		private Builder() {
		}

		/**
		 * Builds the {@link BeanstalkInstanceService}.
		 *
		 * @return the service
		 */
		public BeanstalkInstanceService build() {
			HttpRequest.Builder token = HttpRequest.newBuilder()
				.uri(URI.create("http://169.254.169.254/latest/api/token"))
				.header("X-aws-ec2-metadata-token-ttl-seconds", "21600")
				.timeout(this.timeout())
				.PUT(HttpRequest.BodyPublishers.noBody());

			HttpRequest.Builder instanceId = HttpRequest.newBuilder()
				.uri(URI.create("http://169.254.169.254/latest/meta-data/instance-id"))
				.timeout(this.timeout())
				.GET();

			return new BeanstalkInstanceService(this.httpClient(), token, instanceId);
		}
	}
}
