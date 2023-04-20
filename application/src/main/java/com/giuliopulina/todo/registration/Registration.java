package com.giuliopulina.todo.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class Registration {

    @NotBlank
    private String username;
    @Email
    private String email;
    @ValidInvitationCode
    @NotBlank
    private String invitationCode;
}
