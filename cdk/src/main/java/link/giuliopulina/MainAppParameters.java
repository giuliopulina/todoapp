package link.giuliopulina;

public record MainAppParameters(
        String accountId,
        String region,
        String applicationName,
        String hostedZoneDomain,
        String applicationDomain,
        String loginPageDomainPrefix,
        String dockerRepositoryName,
        String dockerImageTag) { }
