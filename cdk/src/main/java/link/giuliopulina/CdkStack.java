package link.giuliopulina;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.cognito.*;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcProps;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.route53.*;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;


public class CdkStack extends Stack {

    public CdkStack(final Construct scope, final String id, final StackProps props, final CdkParameters parameters) {
        super(scope, id, props);

        // Check this link to save costs bypassing NAT gateway creation
        // https://github.com/aws/aws-cdk/issues/18720#issuecomment-1024521766

        // Create VPC with a AZ limit of two.
        Vpc vpc = new Vpc(this, "MyVpc", VpcProps.builder().maxAzs(2).build());
        vpc.applyRemovalPolicy(RemovalPolicy.DESTROY);

        // Create the ECS Service
        Cluster cluster = new Cluster(this, "Ec2Cluster", ClusterProps.builder().vpc(vpc).build());
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

        // Use the ECS Network Load Balanced Fargate Service construct to create a ECS service
        ApplicationLoadBalancedFargateService fargateService = new ApplicationLoadBalancedFargateService(
                this,
                "FargateService",
                ApplicationLoadBalancedFargateServiceProps.builder()
                        .cluster(cluster)
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
                        .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                                .image(image)
                                .containerPort(8080)
                                .taskRole(ecsTaskRole)
                                .build())
                        .redirectHttp(true)
                        .build());

        // Open port 443 inbound to IPs within VPC to allow network load balancer to connect to the service
        fargateService.getService()
                .getConnections()
                .getSecurityGroups()
                .get(0)
                .addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(443), "allow https inbound from vpc");

        fargateService.getService().applyRemovalPolicy(RemovalPolicy.DESTROY);
    }

    private CognitoOutput setupCognito(CdkParameters parameters) {

        // FIXME: remove
        System.out.println(parameters);

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

    record CognitoOutput(String userPoolId, String userPoolClientId, SecretValue userPoolClientSecret, String logoutUrl, String providerUrl) {

    }
}
