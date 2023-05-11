package link.giuliopulina;

import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.cognito.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.Protocol;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.rds.CfnDBInstance;
import software.amazon.awscdk.services.rds.CfnDBSubnetGroup;
import software.amazon.awscdk.services.route53.*;
import software.amazon.awscdk.services.secretsmanager.CfnSecretTargetAttachment;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.constructs.Construct;

import java.util.*;

import static java.util.Collections.singletonList;


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
                                .subnetType(SubnetType.PRIVATE_ISOLATED)
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

        IHostedZone hostedZone = HostedZone.fromLookup(this, "HostedZone", HostedZoneProviderProps.builder()
                .domainName(parameters.hostedZoneDomain())
                .build());

        IHostedZone appHostedZone = HostedZone.fromLookup(this, "AppHostedZone", HostedZoneProviderProps.builder()
                .domainName(parameters.applicationDomain())
                .build());

        Certificate appCertificate = Certificate.Builder.create(this, "AppCertificate")
                .domainName(parameters.hostedZoneDomain())
                .subjectAlternativeNames(List.of(parameters.applicationDomain()))
                .validation(CertificateValidation.fromDnsMultiZone(
                        Map.of(parameters.hostedZoneDomain(), hostedZone, parameters.applicationDomain(), appHostedZone)
                ))
                .build();

        appCertificate.applyRemovalPolicy(RemovalPolicy.DESTROY);

        final CognitoOutput cognitoOutput = setupCognito(parameters);
        final DatabaseOutput databaseOutput = setupPostgres(vpc);

        Role.Builder roleBuilder = Role.Builder.create(this, "ecsTaskRole")
                .assumedBy(ServicePrincipal.Builder.create("ecs-tasks.amazonaws.com").build())
                .path("/");

        roleBuilder.inlinePolicies(Map.of(
                    "ecsTaskRolePolicy",
                    PolicyDocument.Builder.create()
                            .statements(singletonList(
                                    PolicyStatement.Builder.create()
                                            .sid("AllowCreatingUsers")
                                            .effect(Effect.ALLOW)
                                            .resources(
                                                    List.of(String.format("arn:aws:cognito-idp:%s:%s:userpool/%s", parameters.region(),
                                                            parameters.accountId(), cognitoOutput.userPoolId()))
                                            )
                                            .actions(List.of("cognito-idp:AdminCreateUser"))
                                            .build()
                            ))
                            .build()));

        final Role ecsTaskRole = roleBuilder.build();

        IRepository ecrRepository = Repository.fromRepositoryName(this, "ecrRepository", parameters.dockerRepositoryName());
        ContainerImage image = RepositoryImage.fromEcrRepository(ecrRepository, parameters.dockerImageTag());

        // Use the ECS Network Load Balanced Fargate Service construct to create a ECS service.
        // Running in public subnet to avoid create the expensive NAT Gateway (
        //      assignPublicIp = true AND taskSubnets = PUBLIC
        // )
        ApplicationLoadBalancedFargateService fargateService = new ApplicationLoadBalancedFargateService(
                this,
                "FargateService",
                ApplicationLoadBalancedFargateServiceProps.builder()
                        .cluster(cluster)
                        .assignPublicIp(true)
                        /*.capacityProviderStrategies(
                                singletonList(CapacityProviderStrategy.builder()
                                        .capacityProvider("FARGATE") // or FARGATE_SPOT
                                        .weight(1)
                                        .build()
                                ))*/
                        .protocol(ApplicationProtocol.HTTPS)
                        .listenerPort(443)
                        .publicLoadBalancer(true)
                        .certificate(appCertificate)
                        .domainName(parameters.applicationDomain())
                        .domainZone(appHostedZone)
                        .desiredCount(1)
                        .cpu(512)
                        .memoryLimitMiB(1024)
                        .healthCheckGracePeriod(Duration.seconds(120))
                        .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                                .image(image)
                                .environment(appEnvironmentVariables(parameters, databaseOutput, cognitoOutput))
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
                        .unhealthyThresholdCount(2)
                        .protocol(Protocol.HTTP)
                        .port("traffic-port")
                        .timeout(Duration.seconds(10))
                        .build());

        // Open port 443 inbound to IPs within VPC to allow network load balancer to connect to the service
        ISecurityGroup ecsSecurityGroup = fargateService.getService()
                .getConnections()
                .getSecurityGroups()
                .get(0);

        ecsSecurityGroup.addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(443), "allow https inbound from vpc");

        // allow ingress to database from ecs security group
        allowIngressFromEcs(singletonList(databaseOutput.securityGroupId()), ecsSecurityGroup);

        fargateService.getService().applyRemovalPolicy(RemovalPolicy.DESTROY);
    }

    private Map<String, String> appEnvironmentVariables(MainAppParameters parameters, DatabaseOutput databaseOutput, CognitoOutput cognitoOutput) {

        final Map<String, String> vars = new HashMap<>();
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

        return vars;
    }

    private DatabaseOutput setupPostgres(Vpc vpc) {

        // TODO: harcoded values, can be parametrized
        int storageInGb = 20;
        String instanceClass = "db.t2.micro";
        String postgresVersion = "12.9";

        String username = "dbUser";

        CfnSecurityGroup databaseSecurityGroup = CfnSecurityGroup.Builder.create(this, "databaseSecurityGroup")
                .vpcId(vpc.getVpcId())
                .groupDescription("Security Group for the database instance")
                .groupName("dbSecurityGroup")
                .build();

        // This will generate a JSON object with the keys "username" and "password".
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

        CfnDBSubnetGroup subnetGroup = CfnDBSubnetGroup.Builder.create(this, "dbSubnetGroup")
                .dbSubnetGroupDescription("Subnet group for the RDS instance")
                .dbSubnetGroupName("dbSubnetGroup")
                .subnetIds(vpc.getIsolatedSubnets().stream().map(ISubnet::getSubnetId).toList())
                .build();

        subnetGroup.applyRemovalPolicy(RemovalPolicy.DESTROY);

        CfnDBInstance dbInstance = CfnDBInstance.Builder.create(this, "postgresInstance")
                .dbInstanceIdentifier("todoappdb")
                .allocatedStorage(String.valueOf(storageInGb))
                .availabilityZone(vpc.getAvailabilityZones().get(0))
                .dbInstanceClass(instanceClass)
                .dbName("todoapp")
                .dbSubnetGroupName(subnetGroup.getDbSubnetGroupName())
                .engine("postgres")
                .engineVersion(postgresVersion)
                .masterUsername(username)
                .masterUserPassword(databaseSecret.secretValueFromJson("password").unsafeUnwrap())
                .publiclyAccessible(false)
                .vpcSecurityGroups(Collections.singletonList(databaseSecurityGroup.getAttrGroupId()))
                .build();

        CfnSecretTargetAttachment.Builder.create(this, "secretTargetAttachment")
                .secretId(databaseSecret.getSecretArn())
                .targetId(dbInstance.getRef())
                .targetType("AWS::RDS::DBInstance")
                .build();

        return new DatabaseOutput(
                dbInstance.getAttrEndpointAddress(),
                dbInstance.getAttrEndpointPort(),
                dbInstance.getDbName(),
                databaseSecret,
                databaseSecurityGroup.getAttrGroupId(),
                dbInstance.getDbInstanceIdentifier());

    }

    private CognitoOutput setupCognito(MainAppParameters parameters) {

        String applicationUrl = "https://" + parameters.applicationDomain();

        final UserPool userPool = UserPool.Builder.create(this, "userPool")
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

    private void allowIngressFromEcs(List<String> securityGroupIds, ISecurityGroup ecsSecurityGroup) {
        int i = 1;
        for (String securityGroupId : securityGroupIds) {
            CfnSecurityGroupIngress ingress = CfnSecurityGroupIngress.Builder.create(this, "securityGroupIngress" + i)
                    .sourceSecurityGroupId(ecsSecurityGroup.getSecurityGroupId())
                    .groupId(securityGroupId)
                    .ipProtocol("-1")
                    .build();
            i++;
        }
    }

    record CognitoOutput(String userPoolId, String userPoolClientId, SecretValue userPoolClientSecret, String logoutUrl, String providerUrl) {

    }

    record DatabaseOutput(String endpointAddress, String endpointPort, String dbName, ISecret credentialsJson, String securityGroupId, String instanceId) {}
}
