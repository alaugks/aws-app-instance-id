# Instance ID for AWS

This package provides a unique instance ID for applications running on AWS Elastic Beanstalk (EC2) or AWS Fargate, e.g. to tell the instances of a horizontally scaled application apart in logs, caches or job locks.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.alaugks/aws-instance-id.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.alaugks/aws-instance-id)

## Table of Contents

- [How it works](#how-it-works)
- [Dependency](#dependency)
  - [Maven](#maven)
  - [Gradle](#gradle)
- [Instance Services](#instance-services)
  - [BeanstalkInstanceService](#beanstalkinstanceservice)
    - [Usage](#usage)
  - [FargateInstanceService](#fargateinstanceservice)
    - [Usage](#usage-1)
  - [MockInstanceService](#mockinstanceservice)
- [InstanceService and Error Handling](#instanceservice-and-error-handling)
- [Example Spring Configuration](#example-spring-configuration)
  - [Beanstalk](#beanstalk)
  - [Fargate](#fargate)
- [License](#license)

## How it works

An instance service determines the instance ID for the platform the application runs on. `InstanceService` wraps an instance service and optionally replaces the ID with a fallback if it cannot be determined.

| Instance service           | Platform                          | Source of the instance ID                              |
|----------------------------|-----------------------------------|--------------------------------------------------------|
| `BeanstalkInstanceService` | AWS Elastic Beanstalk (EC2-based) | EC2 Instance Metadata Service (IMDSv2)                 |
| `FargateInstanceService`   | AWS Fargate                       | ECS Task Metadata Endpoint (task ID)                    |
| `MockInstanceService`      | Local development and tests       | Configured value                                       |

## Dependency

### Maven

```xml
<dependency>
    <groupId>io.github.alaugks</groupId>
    <artifactId>aws-instance-id</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle

```text
implementation group: 'io.github.alaugks', name: 'aws-instance-id', version: '0.1.0'
```

## Instance Services

### BeanstalkInstanceService

Retrieves the instance ID via the EC2 Instance Metadata Service (IMDSv2). Elastic Beanstalk environments run on EC2 instances, so the EC2 Instance Metadata Service documentation applies. The service first requests an IMDSv2 session token (`PUT /latest/api/token`) and then reads `GET /latest/meta-data/instance-id` with that token. A response with a status other than `200` is treated as an error.

Created via `BeanstalkInstanceService.builder()`. The requests to the metadata service have a timeout of 1 second by default.

| Method                      | Default   | Description                                                      |
|-----------------------------|-----------|------------------------------------------------------------------|
| `timeout(Duration timeout)` | 1 second  | Timeout for connecting to and for each response of the service.  |
| `build()`                   | —         | Builds the service.                                              |

#### Usage

```java
BeanstalkInstanceService.builder().build();
```

#### AWS documentation

- [Use the Instance Metadata Service to access instance metadata](https://docs.aws.amazon.com/en_us/AWSEC2/latest/UserGuide/configuring-instance-metadata-service.html): IMDSv2 token request and metadata retrieval
- [Access instance metadata for an EC2 instance](https://docs.aws.amazon.com/en_us/AWSEC2/latest/UserGuide/instancedata-data-retrieval.html): retrieval paths, including the `latest` path
- [Configure the Instance Metadata Service options](https://docs.aws.amazon.com/en_us/AWSEC2/latest/UserGuide/configuring-instance-metadata-options.html): IMDSv1/IMDSv2 settings
- [Configuring Amazon EC2 instances using the Elastic Beanstalk console](https://docs.aws.amazon.com/en_us/elasticbeanstalk/latest/dg/using-features.managing.ec2.console.html): IMDS setting of a Beanstalk environment

### FargateInstanceService

Retrieves the task ID of the ECS task via the [ECS Task Metadata Endpoint version 4](https://docs.aws.amazon.com/en_us/AmazonECS/latest/developerguide/task-metadata-endpoint-v4-fargate.html). Fargate has no EC2 instance ID, the task is the unit that identifies an instance of the application.

ECS sets the environment variable `ECS_CONTAINER_METADATA_URI_V4` in every container. The service reads `GET ${ECS_CONTAINER_METADATA_URI_V4}/task` and returns the last segment of the `TaskARN` (e.g. `0123456789abcdef` from `arn:aws:ecs:eu-central-1:123456789012:task/my-cluster/0123456789abcdef`). A response with a status other than `200` or without `TaskARN` is treated as an error and throws an `InstanceIdAWSException`.

If the environment variable is not set (the application does not run on ECS), an `InstanceIdAWSException` is thrown. Use `MockInstanceService` or the fallback of `InstanceService` for local development.

Created via `FargateInstanceService.builder()`. The request to the metadata endpoint has a timeout of 1 second by default.

| Method                      | Default   | Description                                                                |
|-----------------------------|-----------|----------------------------------------------------------------------------|
| `timeout(Duration timeout)` | 1 second  | Timeout for connecting to and for the response of the metadata endpoint.   |
| `build()`                   | —         | Builds the service.                                                        |

#### Usage

```java
FargateInstanceService.builder().build();
```

#### AWS documentation

- [Amazon ECS task metadata endpoint version 4 for tasks on Fargate](https://docs.aws.amazon.com/en_us/AmazonECS/latest/developerguide/task-metadata-endpoint-v4-fargate.html): the environment variable `ECS_CONTAINER_METADATA_URI_V4` and the metadata paths, including `/task`
- [Amazon ECS task metadata v4 JSON response for tasks on Fargate](https://docs.aws.amazon.com/en_us/AmazonECS/latest/developerguide/task-metadata-endpoint-v4-fargate-response.html): fields of the response, including `TaskARN`
- [Amazon ECS task metadata v4 examples for tasks on Fargate](https://docs.aws.amazon.com/en_us/AmazonECS/latest/developerguide/task-metadata-endpoint-v4-fargate-examples.html): example JSON response of the `/task` path

### MockInstanceService

Returns a configurable mock instance ID. Suitable for local development and tests.

```java
MockInstanceService.builder("mock-instance-id").build();
```

## InstanceService and Error Handling

The instance services throw an `InstanceIdAWSException` if the instance ID cannot be determined, including when the timeout is exceeded. It is an unchecked exception (`RuntimeException`) and, where available, carries the original error as cause. The library does not log.

`InstanceService` wraps an instance service and optionally accepts a fallback via `fallback(...)`. The fallback receives the exception and returns the ID to use instead:

```java
// Throws InstanceIdAWSException if the ID cannot be determined
InstanceService strict = InstanceService
    .builder(
        BeanstalkInstanceService.builder().build()
    )
    .build();

// Returns the fallback ID instead of throwing
InstanceService withFallback = InstanceService
    .builder(
        BeanstalkInstanceService.builder().build()
    )
    .fallback(e -> {
        log.warn("Could not determine instance ID", e);
        return "aws-fallback-" + UUID.randomUUID();
    })
    .build();
```

> [!TIP]
> Create the fallback ID once (as above) if it should stay stable for the lifetime of the application.

## Example Spring Configuration

* On `prod` and `stage` the instance ID is determined by AWS.
* On `dev` and `test` a mock instance ID is used.

### Beanstalk

```java
import io.github.alaugks.aws.instance.InstanceService;
import io.github.alaugks.aws.instance.service.BeanstalkInstanceService;
import io.github.alaugks.aws.instance.service.MockInstanceService;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class AwsInstanceConfig {
	
    @Bean
    @Profile({"prod", "stage"})
    public InstanceService awsProdInstanceService() {
        return InstanceService
            .builder(
                BeanstalkInstanceService.builder().build()
            )
            .build();
    }

    @Bean
    @Profile({"dev", "test"})
    public InstanceService mockTestInstanceService() {
        return InstanceService
            .builder(
                MockInstanceService.builder("mock-" + UUID.randomUUID()).build()
            )
            .build();
    }
}
```

### Fargate

```java
import io.github.alaugks.aws.instance.InstanceService;
import io.github.alaugks.aws.instance.service.FargateInstanceService;
import io.github.alaugks.aws.instance.service.MockInstanceService;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class AwsInstanceConfig {
	
    @Bean
    @Profile({"prod", "stage"})
    public InstanceService awsProdInstanceService() {
        return InstanceService
            .builder(
                FargateInstanceService.builder().build()
            )
            .build();
    }

    @Bean
    @Profile({"dev", "test"})
    public InstanceService mockTestInstanceService() {
        return InstanceService
            .builder(
                MockInstanceService.builder("mock-" + UUID.randomUUID()).build()
            )
            .build();
    }
}
```

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
