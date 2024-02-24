package net.giuliopulina.stratospheric.cdk.construct;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.synthetics.Runtime;
import software.amazon.awscdk.services.synthetics.*;
import software.constructs.Construct;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.singletonList;

public class Canaries extends Construct {
    public Canaries(@NotNull Construct scope, @NotNull String id, InputParameters inputParameters) {
        super(scope, id);
        createResources(inputParameters);
    }

    private void createResources(InputParameters inputParameters) {
        Bucket canaryBucket = Bucket.Builder.create(this, "canaryBucket")
                .bucketName("stratospheric-app-giuliopulina-canary-bucket")
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build();

        Role executionRole = Role.Builder.create(this, "canaryExecutionRole")
                .roleName("canary-execution-role")
                .assumedBy(new AnyPrincipal())
                .inlinePolicies(Map.of(
                        "canaryExecutionRolePolicy",
                        PolicyDocument.Builder.create()
                                .statements(singletonList(PolicyStatement.Builder.create()
                                        .effect(Effect.ALLOW)
                                        .resources(singletonList("*"))
                                        .actions(Arrays.asList(
                                                "s3:PutObject",
                                                "s3:GetBucketLocation",
                                                "s3:ListAllMyBuckets",
                                                "cloudwatch:PutMetricData",
                                                "logs:CreateLogGroup",
                                                "logs:CreateLogStream",
                                                "logs:PutLogEvents"))
                                        .build()))
                                .build()))
                .build();

        executionRole.applyRemovalPolicy(RemovalPolicy.DESTROY);

        String canaryName = "canary";

        final Canary canary = Canary.Builder.create(this, "canary")
                .canaryName(canaryName)
                .runtime(Runtime.SYNTHETICS_NODEJS_PUPPETEER_6_2)
                .artifactsBucketLocation(ArtifactsBucketLocation.builder().bucket(canaryBucket).build())
                .startAfterCreation(Boolean.TRUE)
                .role(executionRole)
                .schedule(Schedule.rate(Duration.minutes(5)))
                .environmentVariables(Map.of(
                        "TARGET_URL", inputParameters.applicationUrl(),
                        "USER_NAME", inputParameters.canaryUsername(),
                        "PASSWORD", inputParameters.canaryPassword()
                ))
                .successRetentionPeriod(Duration.days(1))
                .failureRetentionPeriod(Duration.days(1))
                .test(Test.custom(CustomTestOptions.builder()
                        .code(Code.fromInline(getScriptCode("canaries/create-todo-canary.js")))
                        .handler("index.handler").build()))
                .build();

        canary.applyRemovalPolicy(RemovalPolicy.DESTROY);

        final Alarm canaryAlarm = new Alarm(this, "canaryAlarm", AlarmProps.builder()
                .alarmName("canary-failed-alarm")
                .alarmDescription("Alert on multiple Canary failures")
                .metric(new Metric(MetricProps.builder()
                        .namespace("CloudWatchSynthetics")
                        .metricName("Failed")
                        .dimensionsMap(
                                Map.of("CanaryName", canaryName)
                        )
                        .region(inputParameters.region())
                        .period(Duration.minutes(50))
                        .statistic("sum")
                        .build()))
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .evaluationPeriods(1)
                .threshold(3)
                .actionsEnabled(false)
                .build());

        canaryAlarm.applyRemovalPolicy(RemovalPolicy.DESTROY);
    }

    private String getScriptCode(String path) {
        try {
            return new String(Files.readAllBytes(
                    Paths.get(Objects.requireNonNull(getClass()
                            .getClassLoader().getResource(path)).toURI())));
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record InputParameters(String applicationName, String region, String applicationUrl, String canaryUsername, String canaryPassword) {
    }
}
