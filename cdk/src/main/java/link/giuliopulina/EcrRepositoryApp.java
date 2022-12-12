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

        String applicationName = (String) app.getNode().tryGetContext("applicationName");
        Validations.requireNonEmpty(applicationName, "context variable 'applicationName' must not be null");

        String hostedZoneDomain = (String) app.getNode().tryGetContext("hostedZoneDomain");
        Validations.requireNonEmpty(applicationName, "context variable 'hostedZoneDomain' must not be null");

        String applicationDomain = (String) app.getNode().tryGetContext("applicationDomain");
        Validations.requireNonEmpty(applicationName, "context variable 'applicationDomain' must not be null");

        String dockerRepository = (String) app.getNode().tryGetContext("dockerRepositoryName");
        Validations.requireNonEmpty(applicationName, "context variable 'dockerRepositoryName' must not be null");

        String dockerImageTag = (String) app.getNode().tryGetContext("dockerImageTag");

        String loginPageDomainPrefix = (String) app.getNode().tryGetContext("loginPageDomainPrefix");
        Validations.requireNonEmpty(applicationName, "context variable 'loginPageDomainPrefix' must not be null");

        Environment awsEnvironment = makeEnv(accountId, region);

        Parameters parameters = new Parameters(accountId, region, applicationName, hostedZoneDomain,
                applicationDomain, loginPageDomainPrefix, dockerRepository, dockerImageTag);

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

