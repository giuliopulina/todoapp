package net.giuliopulina.stratospheric.cdk;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.cognito.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.Protocol;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.*;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.constructs.Construct;
import software.amazon.awscdk.services.sqs.Queue;


import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static software.amazon.awscdk.services.ec2.SubnetType.PRIVATE_ISOLATED;


public class MainAppStack extends Stack {

    public MainAppStack(final Construct scope, final String id, final StackProps props, final MainAppParameters parameters) {
        super(scope, id, props);

        // Check this link to save costs bypassing NAT gateway creation
        // https://github.com/aws/aws-cdk/issues/18720#issuecomment-1024521766

        // Create VPC with a AZ limit of two.
        Vpc vpc = new Vpc(this, "MyVpc", VpcProps.builder()
                .maxAzs(2)
                .natGateways(0)
                .subnetConfiguration(Arrays.asList(
                        SubnetConfiguration.builder()
                                .subnetType(SubnetType.PUBLIC)
                                .name("publicSubnet")
                                .build(),
                        SubnetConfiguration.builder()
                                .subnetType(PRIVATE_ISOLATED)
                                .name("isolatedSubnet")
                                .build()
                ))
                .build());

        vpc.applyRemovalPolicy(RemovalPolicy.DESTROY);

        // Create the ECS Service
        Cluster cluster = new Cluster(this, "Ec2Cluster", ClusterProps
                .builder()
                .vpc(vpc)
                .build());
        //cluster.enableFargateCapacityProviders();
        cluster.applyRemovalPolicy(RemovalPolicy.DESTROY);

        CertificateAndHostedZone certificateAndHostedZone = createCerticateAndHostedZone(parameters);

        final CognitoOutput cognitoOutput = setupCognito(parameters);
        final DatabaseOutput databaseOutput = setupPostgres(vpc);
        final SQSOutput sqsOutput = setupSQS(parameters);

        IRepository ecrRepository = Repository.fromRepositoryName(this, "ecrRepository", parameters.dockerRepositoryName());
        ContainerImage image = RepositoryImage.fromEcrRepository(ecrRepository, parameters.dockerImageTag());

        // Use the ECS Network Load Balanced Fargate Service construct to create a ECS service.
        // Running in public subnet to avoid create the expensive NAT Gateway (
        //      assignPublicIp = true AND taskSubnets = PUBLIC
        // )
        ApplicationLoadBalancedFargateService fargateService = createFargateService(parameters, cluster, certificateAndHostedZone, image, databaseOutput, cognitoOutput, sqsOutput);
        fargateService.getService().applyRemovalPolicy(RemovalPolicy.DESTROY);

        // Open port 443 inbound to IPs within VPC to allow network load balancer to connect to the service
        ISecurityGroup ecsSecurityGroup = fargateService.getService()
                .getConnections()
                .getSecurityGroups()
                .get(0);

        ecsSecurityGroup.addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(443), "allow https inbound from vpc");

        // allow ingress to database from ecs security group
        DatabaseInstance databaseInstance = databaseOutput.databaseInstance();
        databaseInstance.getConnections().allowFrom(ecsSecurityGroup, Port.tcp(5432), "Allow connection from ECS to Postgres on port 5432");
    }

    private SQSOutput setupSQS(MainAppParameters parameters) {
        Queue todoSharingDlq = Queue.Builder.create(this, "todoSharingDlq")
                .queueName("todo-sharing-dead-letter-queue")
                .retentionPeriod(Duration.days(14))
                .build();
        todoSharingDlq.applyRemovalPolicy(RemovalPolicy.DESTROY);

        Queue todoSharingQueue = Queue.Builder.create(this, "todoSharingQueue")
                .queueName("todo-sharing-queue")
                .visibilityTimeout(Duration.seconds(30))
                .retentionPeriod(Duration.days(14))
                .deadLetterQueue(DeadLetterQueue.builder()
                        .queue(todoSharingDlq)
                        .maxReceiveCount(3)
                        .build())
                .build();

        todoSharingQueue.applyRemovalPolicy(RemovalPolicy.DESTROY);

        // to be exposed to Spring Boot application
        String todoSharingQueueName = todoSharingQueue.getQueueName();

        return new SQSOutput(todoSharingQueueName);
    }

    @NotNull
    private ApplicationLoadBalancedFargateService createFargateService(MainAppParameters parameters, Cluster cluster, CertificateAndHostedZone certificateAndHostedZone, ContainerImage image, DatabaseOutput databaseOutput, CognitoOutput cognitoOutput, SQSOutput sqsOutput) {

        final Role ecsTaskRole = createEcsTaskRole(parameters, databaseOutput, cognitoOutput, sqsOutput);

        ApplicationLoadBalancedFargateService fargateService = new ApplicationLoadBalancedFargateService(
                this,
                "FargateService",
                ApplicationLoadBalancedFargateServiceProps.builder()
                        .cluster(cluster)
                        .assignPublicIp(true)
                        .protocol(ApplicationProtocol.HTTPS)
                        .listenerPort(443)
                        .publicLoadBalancer(true)
                        .certificate(certificateAndHostedZone.appCertificate())
                        .domainName(parameters.applicationDomain())
                        .domainZone(certificateAndHostedZone.appHostedZone())
                        .desiredCount(1)
                        .cpu(512)
                        .memoryLimitMiB(1024)
                        .healthCheckGracePeriod(Duration.seconds(120))
                        .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                                .image(image)
                                .environment(appEnvironmentVariables(parameters, databaseOutput, cognitoOutput, sqsOutput))
                                .containerPort(8080)
                                .taskRole(ecsTaskRole)
                                .build())
                        .taskSubnets(SubnetSelection.builder()
                                .subnetType(SubnetType.PUBLIC)
                                .build())
                        .redirectHttp(true)
                        .build());

        ApplicationTargetGroup targetGroup = fargateService.getTargetGroup();
        targetGroup.setAttribute("deregistration_delay.timeout_seconds", "30");
        targetGroup.enableCookieStickiness(Duration.minutes(30));
        targetGroup.configureHealthCheck(HealthCheck.builder()
                        .interval(Duration.seconds(60))
                        .path("/")
                        .healthyThresholdCount(2)
                        .unhealthyThresholdCount(5)
                        .protocol(Protocol.HTTP)
                        .port("traffic-port")
                        .timeout(Duration.seconds(30))
                        .build());
        return fargateService;
    }

    @NotNull
    private Role createEcsTaskRole(MainAppParameters parameters, DatabaseOutput databaseOutput, CognitoOutput cognitoOutput, SQSOutput sqsOutput) {
        Role.Builder roleBuilder = Role.Builder.create(this, "ecsTaskRole")
                .assumedBy(ServicePrincipal.Builder.create("ecs-tasks.amazonaws.com").build())
                .path("/")
                .inlinePolicies(Map.of("ecsTaskRolePolicy",
                        PolicyDocument.Builder.create()
                                .statements(List.of(
                                        PolicyStatement.Builder.create()
                                                .sid("AllowCreatingUsers")
                                                .effect(Effect.ALLOW)
                                                .resources(
                                                        List.of(String.format("arn:aws:cognito-idp:%s:%s:userpool/%s", parameters.region(),
                                                                parameters.accountId(), cognitoOutput.userPoolId()))
                                                )
                                                .actions(List.of("cognito-idp:AdminCreateUser"))
                                                .build(),
                                        PolicyStatement.Builder.create()
                                                .sid("AllowSQSAccess")
                                                .effect(Effect.ALLOW)
                                                .resources(List.of(
                                                        String.format("arn:aws:sqs:%s:%s:%s", parameters.region(), parameters.accountId(),
                                                                sqsOutput.queueName())
                                                ))
                                                .actions(Arrays.asList(
                                                        "sqs:DeleteMessage",
                                                        "sqs:GetQueueUrl",
                                                        "sqs:ListDeadLetterSourceQueues",
                                                        "sqs:ListQueues",
                                                        "sqs:ListQueueTags",
                                                        "sqs:ReceiveMessage",
                                                        "sqs:SendMessage",
                                                        "sqs:ChangeMessageVisibility",
                                                        "sqs:GetQueueAttributes"))
                                                .build()
                                ))
                                .build()));
        return roleBuilder.build();
    }

    @NotNull
    private MainAppStack.CertificateAndHostedZone createCerticateAndHostedZone(MainAppParameters parameters) {
        IHostedZone hostedZone = HostedZone.fromLookup(this, "HostedZone", HostedZoneProviderProps.builder()
                .domainName(parameters.hostedZoneDomain())
                .build());

        Certificate appCertificate = Certificate.Builder.create(this, "AppCertificate")
                .domainName(parameters.hostedZoneDomain())
                .subjectAlternativeNames(List.of(parameters.applicationDomain()))
                .validation(CertificateValidation.fromDns(hostedZone))
                .build();

        appCertificate.applyRemovalPolicy(RemovalPolicy.DESTROY);
        return new CertificateAndHostedZone(hostedZone, appCertificate);
    }

    private record CertificateAndHostedZone(IHostedZone appHostedZone, Certificate appCertificate) {
    }

    private Map<String, String> appEnvironmentVariables(MainAppParameters parameters, DatabaseOutput databaseOutput, CognitoOutput cognitoOutput, SQSOutput sqsOutput) {

        final Map<String, String> vars = new java.util.HashMap<>();
        final ISecret credentials = databaseOutput.credentialsJson();
        vars.put("SPRING_DATASOURCE_URL",
                String.format("jdbc:postgresql://%s:%s/%s",
                        databaseOutput.endpointAddress(),
                        databaseOutput.endpointPort(),
                        databaseOutput.dbName()));
        vars.put("SPRING_DATASOURCE_USERNAME",
                credentials.secretValueFromJson("username").unsafeUnwrap());
        vars.put("SPRING_DATASOURCE_PASSWORD",
                credentials.secretValueFromJson("password").unsafeUnwrap());

        vars.put("SPRING_PROFILES_ACTIVE", parameters.springProfile());

        vars.put("COGNITO_CLIENT_ID", cognitoOutput.userPoolClientId());
        vars.put("COGNITO_CLIENT_SECRET", cognitoOutput.userPoolClientSecret().unsafeUnwrap());
        vars.put("COGNITO_USER_POOL_ID", cognitoOutput.userPoolId());
        vars.put("COGNITO_LOGOUT_URL", cognitoOutput.logoutUrl());
        vars.put("COGNITO_PROVIDER_URL", cognitoOutput.providerUrl());
        vars.put("TODO_SHARING_QUEUE_NAME", sqsOutput.queueName());
        return vars;
    }

    private DatabaseOutput setupPostgres(Vpc vpc) {

        final String username = "dbUser";
        //database name must only contain alphanumeric characters!
        final String databaseName = "stratosphericapp";

        ISecret databaseSecret = Secret.Builder.create(this, "databaseSecret")
                .secretName("DatabaseSecret")
                .description("Credentials to the RDS instance")
                .generateSecretString(SecretStringGenerator.builder()
                        .secretStringTemplate(String.format("{\"username\": \"%s\"}", username))
                        .generateStringKey("password")
                        .passwordLength(32)
                        .excludeCharacters("@/\\\" ")
                        .build())
                .build();

        var dbInstance = new DatabaseInstance(this, "db-instance", DatabaseInstanceProps.builder()
                .vpc(vpc)
                .vpcSubnets(SubnetSelection
                        .builder()
                        .subnetType(PRIVATE_ISOLATED)
                        .build())
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_12_17)
                                .build()))
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MICRO))
                .credentials(Credentials.fromSecret(databaseSecret))
                .allocatedStorage(20)
                .maxAllocatedStorage(20)
                .allowMajorVersionUpgrade(false)
                .autoMinorVersionUpgrade(true)
                .backupRetention(Duration.days(0))
                .deleteAutomatedBackups(true)
                // TODO: be careful with removal policy once data needs to be retained
                .removalPolicy(RemovalPolicy.DESTROY)
                .deletionProtection(false)
                .databaseName(databaseName)
                .publiclyAccessible(false)
                .build());

        return new DatabaseOutput(
                dbInstance,
                dbInstance.getDbInstanceEndpointAddress(),
                dbInstance.getDbInstanceEndpointPort(),
                databaseName,
                databaseSecret,
                dbInstance.getInstanceIdentifier());

    }


    private CognitoOutput setupCognito(MainAppParameters parameters) {

        String applicationUrl = "https://" + parameters.applicationDomain();

        final UserPool userPool = UserPool.Builder.create(this, "pool")
                .userPoolName(parameters.applicationName() + "-user-pool")
                .selfSignUpEnabled(false)
                .accountRecovery(AccountRecovery.EMAIL_ONLY)
                .autoVerify(AutoVerifiedAttrs.builder().email(true).build())
                .signInAliases(SignInAliases.builder().username(true).email(true).build())
                .signInCaseSensitive(false)
                .standardAttributes(StandardAttributes.builder()
                        .email(StandardAttribute.builder().required(true).mutable(false).build())
                        .build())
                .mfa(Mfa.OFF)
                // TODO: be careful with removal policy once data needs to be retained
                .removalPolicy(RemovalPolicy.DESTROY)
                .passwordPolicy(PasswordPolicy.builder()
                        .requireLowercase(true)
                        .requireDigits(true)
                        .requireSymbols(true)
                        .requireUppercase(true)
                        .minLength(12)
                        .tempPasswordValidity(Duration.days(7))
                        .build())
                .build();

        final UserPoolClient userPoolClient = UserPoolClient.Builder.create(this, "userPoolClient")
                .userPoolClientName(parameters.applicationName() + "-client")
                .generateSecret(true)
                .userPool(userPool)
                .oAuth(OAuthSettings.builder()
                        .callbackUrls(Arrays.asList(
                                String.format("%s/login/oauth2/code/cognito", applicationUrl),
                                "http://localhost:8080/login/oauth2/code/cognito"
                        ))
                        .logoutUrls(Arrays.asList(applicationUrl, "http://localhost:8080"))
                        .flows(OAuthFlows.builder()
                                .authorizationCodeGrant(true)
                                .build())
                        .scopes(Arrays.asList(OAuthScope.EMAIL, OAuthScope.OPENID, OAuthScope.PROFILE))
                        .build())
                .supportedIdentityProviders(singletonList(UserPoolClientIdentityProvider.COGNITO))
                .build();

        final UserPoolDomain userPoolDomain = UserPoolDomain.Builder.create(this, "userPoolDomain")
                .userPool(userPool)
                .cognitoDomain(CognitoDomainOptions.builder()
                        .domainPrefix(parameters.loginPageDomainPrefix())
                        .build())
                .build();

        String logoutUrl = String.format("https://%s.auth.%s.amazoncognito.com/logout", parameters.loginPageDomainPrefix(), parameters.region());

        return new CognitoOutput(userPool.getUserPoolId(), userPoolClient.getUserPoolClientId(), userPoolClient.getUserPoolClientSecret(), logoutUrl, userPool.getUserPoolProviderUrl());
    }

    record CognitoOutput(String userPoolId, String userPoolClientId, SecretValue userPoolClientSecret, String logoutUrl, String providerUrl) {

    }

    record DatabaseOutput(DatabaseInstance databaseInstance, String endpointAddress, String endpointPort, String dbName, ISecret credentialsJson, String instanceId) {}

    record SQSOutput(String queueName) {}
}
