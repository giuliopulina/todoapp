package link.giuliopulina;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class CdkApp {
    public static void main(final String[] args) {
        App app = new App();

        String accountId = (String) app.getNode().tryGetContext("accountId");
        Validations.requireNonEmpty(accountId, "context variable 'accountId' must not be null");

        String region = (String) app.getNode().tryGetContext("region");
        Validations.requireNonEmpty(region, "context variable 'region' must not be null");

        String applicationName = (String) app.getNode().tryGetContext("applicationName");
        Validations.requireNonEmpty(applicationName, "context variable 'applicationName' must not be null");

        String hostedZoneDomain = (String) app.getNode().tryGetContext("hostedZoneDomain");
        Validations.requireNonEmpty(applicationName, "context variable 'hostedZoneDomain' must not be null");

        String applicationDomain = (String) app.getNode().tryGetContext("applicationDomain");
        Validations.requireNonEmpty(applicationName, "context variable 'applicationDomain' must not be null");

        String dockerRepository = (String) app.getNode().tryGetContext("dockerRepositoryName");
        Validations.requireNonEmpty(applicationName, "context variable 'dockerRepositoryName' must not be null");

        String dockerImageTag = (String) app.getNode().tryGetContext("dockerImageTag");
        Validations.requireNonEmpty(applicationName, "context variable 'dockerImageTag' must not be null");

        Environment awsEnvironment = makeEnv(accountId, region);

        Parameters parameters = new Parameters(accountId, region, applicationName, hostedZoneDomain,
                applicationDomain, dockerRepository, dockerImageTag);

        new CdkStack(app, "CdkStack", StackProps
                .builder()
                .stackName("CdkStack")
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

