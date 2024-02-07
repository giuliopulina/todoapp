package net.giuliopulina.stratospheric.cdk.construct;

import net.giuliopulina.stratospheric.cdk.MainAppParameters;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.dynamodb.*;
import software.constructs.Construct;

public class BreadcrumbsDynamoDbTable extends Construct {
    public BreadcrumbsDynamoDbTable(
            final Construct scope,
            final String id,
            final InputParameter inputParameters
    ) {
        super(scope, id);
        new Table(
                this,
                "BreadcrumbsDynamoDbTable",
                TableProps.builder()
                        .tableName(inputParameters.tableName)
                        .partitionKey(
                                Attribute.builder().type(AttributeType.STRING).name("id").build())
                        .encryption(TableEncryption.AWS_MANAGED)
                        .billingMode(BillingMode.PROVISIONED)
                        .readCapacity(10)
                        .writeCapacity(10)
                        .removalPolicy(RemovalPolicy.DESTROY)
                        .build());
    }
    public record InputParameter(String tableName) {
    }
}