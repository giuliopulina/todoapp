package link.giuliopulina;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ecr.LifecycleRule;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.iam.AccountPrincipal;
import software.constructs.Construct;

import java.util.Collections;


public class EcrRepositoryStack extends Stack {

    public EcrRepositoryStack(final Construct scope, final String id, final StackProps props, final EcrRepositoryParameters parameters) {
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
