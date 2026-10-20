// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.aws.app.instance.id.service;

import io.github.alaugks.aws.app.instance.id.AWSInstanceIdException;
import io.github.alaugks.aws.app.instance.id.InstanceServiceInterface;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Base class for {@link InstanceServiceInterface} implementations that read the instance ID
 * from a metadata endpoint over HTTP.
 */
abstract class HttpInstanceService implements InstanceServiceInterface {

	/** Default timeout for the requests to the metadata endpoint, which is local to the instance. */
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(1);

	/** Client used for the requests to the metadata endpoint. */
	private final HttpClient client;

	HttpInstanceService(HttpClient client) {
		this.client = client;
	}

	/**
	 * Returns the instance ID from the metadata endpoint.
	 *
	 * @return the instance ID
	 * @throws AWSInstanceIdException if the instance ID cannot be determined
	 */
	@Override
	public final String instanceId() {
		try {
			return this.fetchInstanceId();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AWSInstanceIdException("Interrupted while fetching the instance ID", e);
		} catch (IOException e) {
			throw new AWSInstanceIdException("Could not fetch the instance ID: " + e.getMessage(), e);
		}
	}

	/** Reads the instance ID from the metadata endpoint. */
	abstract String fetchInstanceId() throws IOException, InterruptedException;

	/** Sends the request and returns the body; any status other than 200 is treated as an error. */
	final String send(HttpRequest request) throws IOException, InterruptedException {
		HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new AWSInstanceIdException("Unexpected status " + response.statusCode() + " from " + request.uri());
		}
		return response.body();
	}

	/**
	 * Base class for the builders of the services.
	 *
	 * @param <B> the type of the concrete builder
	 */
	abstract static class AbstractBuilder<B extends AbstractBuilder<B>> {

		private HttpClient client;

		private Duration timeout = DEFAULT_TIMEOUT;

		/**
		 * Sets the timeout for connecting and for each response. Defaults to 1 second. If exceeded, an
		 * {@link AWSInstanceIdException} is thrown.
		 *
		 * @param timeout the timeout
		 * @return this builder
		 */
		public B timeout(Duration timeout) {
			this.timeout = timeout;
			return (B) this;
		}

		/** Replaces the client, used by the tests to redirect the requests to a test server. */
		B httpClient(HttpClient client) {
			this.client = client;
			return (B) this;
		}

		/** Returns the configured timeout. */
		Duration timeout() {
			return this.timeout;
		}

		/** Returns the configured client, or a new one with the connect timeout if none is configured. */
		final HttpClient httpClient() {
			return this.client != null
				? this.client
				: HttpClient.newBuilder().connectTimeout(this.timeout).build();
		}
	}
}
