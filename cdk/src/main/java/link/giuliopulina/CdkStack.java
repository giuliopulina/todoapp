package link.giuliopulina;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcProps;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.LifecycleRule;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ClusterProps;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.RepositoryImage;
import software.amazon.awscdk.services.ecs.patterns.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.iam.AccountPrincipal;
import software.amazon.awscdk.services.route53.*;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.util.Collections;
import java.util.List;
import java.util.Map;


public class CdkStack extends Stack {

    public CdkStack(final Construct scope, final String id, final StackProps props, final Parameters parameters) {
        super(scope, id, props);

        // Create VPC with a AZ limit of two.
        Vpc vpc = new Vpc(this, "MyVpc", VpcProps.builder().maxAzs(2).build());
        vpc.applyRemovalPolicy(RemovalPolicy.DESTROY);

        // Create the ECS Service
        Cluster cluster = new Cluster(this, "Ec2Cluster", ClusterProps.builder().vpc(vpc).build());
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

        IRepository ecrRepository = Repository.fromRepositoryName(this, "ecrRepository", parameters.dockerRepositoryName());
        ContainerImage image = RepositoryImage.fromEcrRepository(ecrRepository, parameters.dockerImageTag());

        // Use the ECS Network Load Balanced Fargate Service construct to create a ECS service
        ApplicationLoadBalancedFargateService fargateService = new ApplicationLoadBalancedFargateService(
                this,
                "FargateService",
                ApplicationLoadBalancedFargateServiceProps.builder()
                        .cluster(cluster)
                        .protocol(ApplicationProtocol.HTTPS)
                        .listenerPort(443)
                        .publicLoadBalancer(true)
                        .certificate(appCertificate)
                        .domainName(parameters.applicationDomain())
                        .domainZone(appHostedZone)
                        .desiredCount(1)
                        .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                                .image(image)
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
}
