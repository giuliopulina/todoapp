package net.giuliopulina.stratospheric.collaboration;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

@Component
public class TodoSharingListener {

    //  private final MailSender mailSender;
    private final TodoCollaborationService todoCollaborationService;
    private final boolean autoConfirmCollaborations;

    private static final Logger LOG = LoggerFactory.getLogger(TodoSharingListener.class.getName());

    public TodoSharingListener(
//    MailSender mailSender,
            TodoCollaborationService todoCollaborationService,
            @Value("${custom.auto-confirm-collaborations}") boolean autoConfirmCollaborations) {
//    this.mailSender = mailSender;
        this.todoCollaborationService = todoCollaborationService;
        this.autoConfirmCollaborations = autoConfirmCollaborations;
    }

    @SqsListener(value = "${custom.sharing-queue}")
    public void listenToSharingMessages(TodoCollaborationNotification payload) throws InterruptedException {
        LOG.info("Incoming todo sharing payload: {}", payload);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("dummy@example.com");
        message.setTo(payload.getCollaboratorEmail());
        message.setSubject("A todo was shared with you");
        message.setText(
                String.format(
                        """
                                Hi %s,\s

                                someone shared a Todo from %s with you.

                                Information about the shared Todo item:\s

                                Title: %s\s
                                Description: %s\s
                                Priority: %s\s

                                You can accept the collaboration by clicking this link: %s/todo/%s/collaborations/%s/confirm?token=%s\s

                                Kind regards,\s
                                Stratospheric""",
                        payload.getCollaboratorEmail(),
                        "example.com",
                        payload.getTodoTitle(),
                        payload.getTodoDescription(),
                        payload.getTodoPriority(),
                        "example.com",
                        payload.getTodoId(),
                        payload.getCollaboratorId(),
                        payload.getToken()
                )
        );

        LOG.info(message.toString());
//    mailSender.send(message);

        LOG.warn("NOT REALLY SENDING THE EMAIL!");

        LOG.info("Successfully informed collaborator about shared todo.");

        if (autoConfirmCollaborations) {
            LOG.info("Auto-confirmed collaboration request for todo: {}", payload.getTodoId());
            Thread.sleep(2_500);
            todoCollaborationService.confirmCollaboration(payload.getCollaboratorEmail(), payload.getTodoId(), payload.getCollaboratorId(), payload.getToken());
        }
    }
}