package com.olp.notification.dto;

import java.util.ArrayList;
import java.util.List;

public class TargetedNotificationResponse {

    private Integer recipients;
    private List<String> roles = new ArrayList<>();

    public Integer getRecipients() {
        return recipients;
    }

    public void setRecipients(Integer recipients) {
        this.recipients = recipients;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
