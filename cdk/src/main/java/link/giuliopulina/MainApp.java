package link.giuliopulina;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class MainApp {
    public static void main(final String[] args) {
        App app = new App();

        String accountId = (String) app.getNode().tryGetContext("accountId");
        Validations.requireNonEmpty(accountId, "context variable 'accountId' must not be null");

        String region = (String) app.getNode().tryGetContext("region");
        Validations.requireNonEmpty(region, "context variable 'region' must not be null");

        String applicationName = (String) app.getNode().tryGetContext("applicationName");
        Validations.requireNonEmpty(applicationName, "context variable 'applicationName' must not be null");

        String hostedZoneDomain = (String) app.getNode().tryGetContext("hostedZoneDomain");
        Validations.requireNonEmpty(hostedZoneDomain, "context variable 'hostedZoneDomain' must not be null");

        String applicationDomain = (String) app.getNode().tryGetContext("applicationDomain");
        Validations.requireNonEmpty(applicationDomain, "context variable 'applicationDomain' must not be null");

        String dockerRepository = (String) app.getNode().tryGetContext("dockerRepositoryName");
        Validations.requireNonEmpty(dockerRepository, "context variable 'dockerRepositoryName' must not be null");

        String dockerImageTag = (String) app.getNode().tryGetContext("dockerImageTag");
        Validations.requireNonEmpty(dockerImageTag, "context variable 'dockerImageTag' must not be null");

        String loginPageDomainPrefix = (String) app.getNode().tryGetContext("loginPageDomainPrefix");
        Validations.requireNonEmpty(loginPageDomainPrefix, "context variable 'loginPageDomainPrefix' must not be null");

        String springProfile = (String) app.getNode().tryGetContext("springProfile");
        Validations.requireNonEmpty(springProfile, "context variable 'springProfile' must not be null");

        Environment awsEnvironment = makeEnv(accountId, region);

        MainAppParameters parameters = new MainAppParameters(accountId, region, applicationName, hostedZoneDomain,
                applicationDomain, loginPageDomainPrefix, dockerRepository, dockerImageTag, springProfile);

        new MainAppStack(app, "ApplicationStack", StackProps
                .builder()
                .stackName("ApplicationStack")
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

