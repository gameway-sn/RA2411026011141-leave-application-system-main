package com.example.leave.service;

import com.example.leave.model.DecisionRequest;
import com.example.leave.model.LeaveBalance;
import com.example.leave.model.LeaveRequest;
import com.example.leave.model.LeaveStatus;
import com.example.leave.model.SubmitLeaveRequest;
import com.example.leave.repository.LeaveBalanceRepository;
import com.example.leave.repository.LeaveRequestRepository;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Core integration layer between the REST API, the database and Camunda.
 */
@Service
public class LeaveService {

    public static final String PROCESS_ID = "leave-request";
    public static final int DEFAULT_ANNUAL_DAYS = 20;

    private static final Logger log = LoggerFactory.getLogger(LeaveService.class);

    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final CamundaClient camunda;
    private final NotificationService notifications;

    public LeaveService(LeaveRequestRepository requests,
                        LeaveBalanceRepository balances,
                        CamundaClient camunda,
                        NotificationService notifications) {
        this.requests = requests;
        this.balances = balances;
        this.camunda = camunda;
        this.notifications = notifications;
    }

    /**
     * Saves the request, then starts the leave-request process.
     * Not @Transactional on purpose: the row must be committed before the
     * validate-leave worker (running on another thread) tries to read it.
     */
    public LeaveRequest submit(SubmitLeaveRequest body) {
        LeaveRequest request = new LeaveRequest();
        request.setEmployeeName(body.employeeName().trim());
        request.setEmployeeEmail(normalise(body.employeeEmail()));
        request.setLeaveType(body.leaveType().trim().toUpperCase());
        request.setStartDate(body.startDate());
        request.setEndDate(body.endDate());
        request.setDays(countWorkingDays(body.startDate(), body.endDate()));
        request.setReason(body.reason());
        request.setStatus(LeaveStatus.SUBMITTED);
        request.setStatusMessage("Submitted, waiting for validation");
        request = requests.save(request);

        balance(request.getEmployeeEmail());

        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", request.getId());
        variables.put("employeeEmail", request.getEmployeeEmail());
        variables.put("leaveType", request.getLeaveType());
        variables.put("days", request.getDays());
        variables.put("startDate", request.getStartDate().toString());
        variables.put("endDate", request.getEndDate().toString());

        try {
            ProcessInstanceEvent instance = camunda.newCreateInstanceCommand()
                    .bpmnProcessId(PROCESS_ID)
                    .latestVersion()
                    .variables(variables)
                    .send()
                    .join();
            requests.updateProcessInstanceKey(request.getId(), instance.getProcessInstanceKey());
            log.info("Leave request {} started process instance {}",
                    request.getId(), instance.getProcessInstanceKey());
        } catch (ClientException e) {
            log.error("Could not start process for leave request {}", request.getId(), e);
            request.setStatus(LeaveStatus.REJECTED);
            request.setStatusMessage("Workflow engine unavailable, please submit again later");
            requests.save(request);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Camunda is not reachable: " + e.getMessage());
        }

        return requests.findById(request.getId()).orElse(request);
    }

    /**
     * Completes the Manager approval user task of the request's process instance.
     * Returns HTTP 409 when the task is not visible yet (search index lag) or the
     * request is no longer waiting; the frontend retries briefly on 409.
     */
    public void decide(Long id, DecisionRequest body) {
        LeaveRequest request = find(id);
        if (request.getStatus() != LeaveStatus.PENDING_APPROVAL || request.getProcessInstanceKey() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request " + id + " is not awaiting approval");
        }

        List<UserTask> tasks = camunda.newUserTaskSearchRequest()
                .filter(f -> f.processInstanceKey(request.getProcessInstanceKey())
                        .state(UserTaskState.CREATED))
                .send()
                .join()
                .items();

        if (tasks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Approval task for request " + id + " is not available yet, retry shortly");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", body.approved());
        variables.put("managerComment", body.comment() == null ? "" : body.comment().trim());

        try {
            camunda.newCompleteUserTaskCommand(tasks.get(0).getUserTaskKey())
                    .variables(variables)
                    .send()
                    .join();
            log.info("Manager {} leave request {}", body.approved() ? "approved" : "rejected", id);
        } catch (ClientException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Could not complete the approval task: " + e.getMessage());
        }
    }

    /** Called by the validate-leave job worker. Returns the process variables valid and validationMessage. */
    @Transactional
    public Map<String, Object> validate(Long requestId) {
        LeaveRequest request = find(requestId);
        LeaveBalance balance = balance(request.getEmployeeEmail());

        boolean valid;
        String message;
        if (request.getEndDate().isBefore(request.getStartDate())) {
            valid = false;
            message = "End date is before start date";
        } else if (request.getDays() == 0) {
            valid = false;
            message = "The selected period contains no working days";
        } else if ("ANNUAL".equals(request.getLeaveType()) && request.getDays() > balance.getRemainingDays()) {
            valid = false;
            message = "Insufficient annual leave: requested " + request.getDays()
                    + " day(s), " + balance.getRemainingDays() + " remaining";
        } else {
            valid = true;
            message = "OK";
        }

        if (valid) {
            request.setStatus(LeaveStatus.PENDING_APPROVAL);
            request.setStatusMessage("Waiting for manager approval");
        } else {
            request.setStatusMessage(message);
        }
        requests.save(request);
        log.info("Validated leave request {}: valid={} ({})", requestId, valid, message);

        Map<String, Object> result = new HashMap<>();
        result.put("valid", valid);
        result.put("validationMessage", message);
        return result;
    }

    /**
     * Called by the finalize-leave job worker. Idempotent: if the job is retried
     * after the request already has a final status, nothing changes, so the
     * balance is never deducted twice.
     */
    @Transactional
    public void finalizeRequest(Long requestId, boolean approved, String managerComment, String validationMessage) {
        LeaveRequest request = find(requestId);
        if (request.getStatus() == LeaveStatus.APPROVED || request.getStatus() == LeaveStatus.REJECTED) {
            log.info("Leave request {} already finalised as {}", requestId, request.getStatus());
            return;
        }

        String comment = managerComment == null ? "" : managerComment.trim();

        if (approved) {
            if ("ANNUAL".equals(request.getLeaveType())) {
                LeaveBalance balance = balance(request.getEmployeeEmail());
                balance.setRemainingDays(balance.getRemainingDays() - request.getDays());
                balances.save(balance);
            }
            request.setStatus(LeaveStatus.APPROVED);
            request.setStatusMessage(comment.isEmpty() ? "Approved by manager" : "Approved: " + comment);
        } else {
            request.setStatus(LeaveStatus.REJECTED);
            boolean failedValidation = validationMessage != null && !"OK".equals(validationMessage);
            if (failedValidation) {
                request.setStatusMessage(validationMessage);
            } else {
                request.setStatusMessage(comment.isEmpty() ? "Rejected by manager" : "Rejected: " + comment);
            }
        }
        requests.save(request);
        notifications.leaveFinalised(request);
    }

    /** Returns the employee's balance, creating the default 20-day entitlement on first use. */
    @Transactional
    public LeaveBalance balance(String email) {
        String key = normalise(email);
        return balances.findById(key)
                .orElseGet(() -> balances.save(new LeaveBalance(key, DEFAULT_ANNUAL_DAYS)));
    }

    /** Counts Monday-to-Friday days between start and end, both inclusive. */
    static int countWorkingDays(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        int days = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                days++;
            }
        }
        return days;
    }

    private LeaveRequest find(Long id) {
        return requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request " + id + " not found"));
    }

    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
