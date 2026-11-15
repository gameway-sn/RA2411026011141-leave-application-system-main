package com.example.leave.service;

import com.example.leave.model.LeaveRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Notification hook called when a request reaches its final status.
 * This version only writes to the log; replace the body with e-mail,
 * SMS or a messaging call when needed.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void leaveFinalised(LeaveRequest request) {
        log.info("[notification] To {}: leave request #{} ({} to {}) is {} - {}",
                request.getEmployeeEmail(),
                request.getId(),
                request.getStartDate(),
                request.getEndDate(),
                request.getStatus(),
                request.getStatusMessage());
    }
}
