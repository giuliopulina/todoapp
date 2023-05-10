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

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getInvitationCode() {
        return invitationCode;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setInvitationCode(String invitationCode) {
        this.invitationCode = invitationCode;
    }
}
