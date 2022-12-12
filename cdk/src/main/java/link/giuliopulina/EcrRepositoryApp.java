package link.giuliopulina;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class EcrRepositoryApp {
    public static void main(final String[] args) {
        App app = new App();

        /* TODO: clean up parameters that are not needed by this app */
        String accountId = (String) app.getNode().tryGetContext("accountId");
        Validations.requireNonEmpty(accountId, "context variable 'accountId' must not be null");

        String region = (String) app.getNode().tryGetContext("region");
        Validations.requireNonEmpty(region, "context variable 'region' must not be null");

        String dockerRepository = (String) app.getNode().tryGetContext("dockerRepositoryName");
        Validations.requireNonEmpty(dockerRepository, "context variable 'dockerRepositoryName' must not be null");

        Environment awsEnvironment = makeEnv(accountId, region);

        EcrRepositoryParameters parameters = new EcrRepositoryParameters(accountId, region, dockerRepository);

        new EcrRepositoryStack(app, "EcrRepositoryStack", StackProps
                .builder()
                .stackName("EcrRepositoryStack")
                .env(awsEnvironment)
                .build(), parameters);

        app.synth();
    }

    static Environment makeEnv(String account, String region) {
        return Environment.builder()
                .account(account)
                .region(region)
                .build();
    }
}

