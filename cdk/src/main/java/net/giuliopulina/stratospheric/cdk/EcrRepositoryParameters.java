package net.giuliopulina.stratospheric.cdk;

public record EcrRepositoryParameters(
        String accountId,
        String region,
        String dockerRepositoryName

) {}
