package link.giuliopulina;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcProps;
import software.amazon.awscdk.services.ecr.LifecycleRule;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ClusterProps;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.RepositoryImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateServiceProps;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.iam.AccountPrincipal;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.constructs.Construct;

import java.util.Collections;
import java.util.List;
import java.util.Map;


public class EcrRepositoryStack extends Stack {

    public EcrRepositoryStack(final Construct scope, final String id, final StackProps props, final Parameters parameters) {
        super(scope, id, props);

        final int maxImagesForEcrRepository = 10;
        var ecrRepository = Repository.Builder.create(this, "ecrRepository")
                .repositoryName(parameters.dockerRepositoryName())
                .removalPolicy(RemovalPolicy.DESTROY)
                .lifecycleRules(Collections.singletonList(LifecycleRule.builder()
                        .rulePriority(1)
                        .description("limit to " + maxImagesForEcrRepository + " images")
                        .maxImageCount(maxImagesForEcrRepository)
                        .build()))

                .build();
        ecrRepository.grantPullPush(new AccountPrincipal(parameters.accountId()));
        ecrRepository.applyRemovalPolicy(RemovalPolicy.DESTROY);

    }
}
