package net.giuliopulina.stratospheric.cdk.construct;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.logs.FilterPattern;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.MetricFilter;
import software.amazon.awscdk.services.logs.MetricFilterProps;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.TopicProps;
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription;
import software.constructs.Construct;

import java.util.Map;

public class CloudwatchAlerts extends Construct {
    public CloudwatchAlerts(
            final Construct scope,
            final String id,
            final InputParameters inputParameters
    ) {
        super(scope, id);

        final Topic snsAlarmingTopic = createAlarmNotificationSNSTopic(inputParameters);
        snsAlarmingTopic.applyRemovalPolicy(RemovalPolicy.DESTROY);

        createAlarmForElbSlowResponseTime(inputParameters, snsAlarmingTopic);
        createCompositeAlarmForLB5xxErrorsAndErrorInLogs(inputParameters, snsAlarmingTopic);
    }

    private void createCompositeAlarmForLB5xxErrorsAndErrorInLogs(InputParameters inputParameters, Topic snsAlarmingTopic) {
        Alarm elb5xxAlarm = new Alarm(this, "elb5xxAlarm", AlarmProps.builder()
                .alarmName("5xx-backend-alarm")
                .alarmDescription("Alert on multiple HTTP 5xx ELB responses." +
                        "See the runbook for a diagnosis and mitigation hints: https://github.com/stratospheric-dev/stratospheric/blob/main/docs/runbooks/elb5xxAlarm.md")
                .metric(new Metric(MetricProps.builder()
                        .namespace("AWS/ApplicationELB")
                        .metricName("HTTPCode_ELB_5XX_Count")
                        .dimensionsMap(Map.of(
                                "LoadBalancer", inputParameters.loadBalancerName()
                        ))
                        .region(inputParameters.region)
                        .period(Duration.minutes(5))
                        .statistic("sum")
                        .build()))
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .evaluationPeriods(3)
                .datapointsToAlarm(3)
                .threshold(5)
                .actionsEnabled(false)
                .build());

        elb5xxAlarm.applyRemovalPolicy(RemovalPolicy.DESTROY);

        MetricFilter errorLogsMetricFilter = new MetricFilter(this, "errorLogsMetricFilter",
                MetricFilterProps.builder()
                        .metricName("backend-error-logs")
                        .metricNamespace("stratospheric")
                        .metricValue("1")
                        .defaultValue(0)
                        .logGroup(inputParameters.applicationLogGroup())
                        .filterPattern(FilterPattern.stringValue("$.level", "=", "ERROR")) // { $.level = "ERROR" }
                        .build());

        errorLogsMetricFilter.applyRemovalPolicy(RemovalPolicy.DESTROY);

        Metric errorLogsMetric = errorLogsMetricFilter.metric(MetricOptions.builder()
                .period(Duration.minutes(5))
                .statistic("sum")
                .region(inputParameters.region())
                .build());


        Alarm errorLogsAlarm = errorLogsMetric.createAlarm(this, "errorLogsAlarm", CreateAlarmOptions.builder()
                .alarmName("backend-error-logs-alarm")
                .alarmDescription("Alert on multiple ERROR backend logs")
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .evaluationPeriods(3)
                .threshold(5)
                .actionsEnabled(false)
                .build());

        errorLogsAlarm.applyRemovalPolicy(RemovalPolicy.DESTROY);

        CompositeAlarm compositeAlarm = new CompositeAlarm(this, "basicCompositeAlarm",
                CompositeAlarmProps.builder()
                        .actionsEnabled(true)
                        .compositeAlarmName("backend-api-failure")
                        .alarmDescription("Showcasing a Composite Alarm")
                        .alarmRule(AlarmRule.allOf(
                                        AlarmRule.fromAlarm(elb5xxAlarm, AlarmState.ALARM),
                                        AlarmRule.fromAlarm(errorLogsAlarm, AlarmState.ALARM)
                                )
                        )
                        .build());

        compositeAlarm.addAlarmAction(new SnsAction(snsAlarmingTopic));
        compositeAlarm.applyRemovalPolicy(RemovalPolicy.DESTROY);

    }

    @NotNull
    private Topic createAlarmNotificationSNSTopic(InputParameters inputParameters) {
        Topic snsAlarmingTopic = new Topic(this, "snsAlarmingTopic", TopicProps.builder()
                .topicName(inputParameters.applicationName + "-alarming-topic")
                .displayName("SNS Topic to further route Amazon CloudWatch Alarms")
                .fifo(false)
                .build());

        snsAlarmingTopic.addSubscription(EmailSubscription.Builder
                .create(inputParameters.alarmNotificationEmail)
                .build()
        );
        return snsAlarmingTopic;
    }

    private void createAlarmForElbSlowResponseTime(InputParameters inputParameters, Topic snsAlarmingTopic) {

        Alarm elbSlowResponseTimeAlarm = new Alarm(this, "elbSlowResponseTimeAlarm",
                AlarmProps.builder()
                        .alarmName("slow-api-response-alarm")
                        .alarmDescription("Indicating potential problems with the Spring Boot Backend")
                        .metric(new Metric(MetricProps.builder()
                                .namespace("AWS/ApplicationELB")
                                .metricName("TargetResponseTime")
                                .dimensionsMap(Map.of(
                                        "LoadBalancer", inputParameters.loadBalancerName()
                                ))
                                .region(inputParameters.region())
                                .period(Duration.minutes(5))
                                .statistic("avg")
                                .build()))
                        .treatMissingData(TreatMissingData.NOT_BREACHING)
                        .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                        .evaluationPeriods(3)
                        .threshold(2)
                        .actionsEnabled(true)
                        .build());


        elbSlowResponseTimeAlarm.addAlarmAction(new SnsAction(snsAlarmingTopic));
        elbSlowResponseTimeAlarm.applyRemovalPolicy(RemovalPolicy.DESTROY);


    }

    public record InputParameters(String applicationName, String loadBalancerName, String region,
                           String alarmNotificationEmail, LogGroup applicationLogGroup) {

    }
}
