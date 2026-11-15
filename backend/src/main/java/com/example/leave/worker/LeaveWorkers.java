package com.example.leave.worker;

import com.example.leave.service.LeaveService;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Job workers for the service tasks in leave-request.bpmn.
 * The type values must match the task definition types in the BPMN file.
 */
@Component
public class LeaveWorkers {

    private final LeaveService service;

    public LeaveWorkers(LeaveService service) {
        this.service = service;
    }

    /** Service task "Validate leave request". Returns valid + validationMessage as process variables. */
    @JobWorker(type = "validate-leave")
    public Map<String, Object> validate(@Variable(name = "requestId") Long requestId) {
        return service.validate(requestId);
    }

    /**
     * Service tasks "Finalize as approved" and "Finalize as rejected".
     * Reads the full variable map because managerComment does not exist on the
     * invalid-request path.
     */
    @JobWorker(type = "finalize-leave")
    public void finalizeLeave(ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        Long id = ((Number) variables.get("requestId")).longValue();
        boolean approved = "APPROVED".equals(variables.get("outcome"));

        service.finalizeRequest(
                id,
                approved,
                asString(variables.get("managerComment"), ""),
                asString(variables.get("validationMessage"), "OK"));
    }

    private static String asString(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }
}
