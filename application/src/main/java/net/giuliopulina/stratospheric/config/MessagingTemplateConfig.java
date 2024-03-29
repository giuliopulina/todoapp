package net.giuliopulina.stratospheric.config;


import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Configuration
public class MessagingTemplateConfig {
    @Bean
    public SqsTemplate sqsTemplate(
            SqsAsyncClient sqsAsyncClient) {
        return SqsTemplate.newTemplate(sqsAsyncClient);
    }
}
