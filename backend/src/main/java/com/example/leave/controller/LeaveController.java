package com.example.leave.controller;

import com.example.leave.model.DecisionRequest;
import com.example.leave.model.LeaveRequest;
import com.example.leave.model.LeaveStatus;
import com.example.leave.model.SubmitLeaveRequest;
import com.example.leave.repository.LeaveRequestRepository;
import com.example.leave.service.LeaveService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API used by the React frontend. Camunda is never called from the browser. */
@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService service;
    private final LeaveRequestRepository requests;

    public LeaveController(LeaveService service, LeaveRequestRepository requests) {
        this.service = service;
        this.requests = requests;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequest submit(@Valid @RequestBody SubmitLeaveRequest body) {
        return service.submit(body);
    }

    @GetMapping
    public List<LeaveRequest> mine(@RequestParam String employeeEmail) {
        return requests.findByEmployeeEmailOrderByCreatedAtDesc(employeeEmail.trim().toLowerCase());
    }

    @GetMapping("/pending")
    public List<LeaveRequest> pending() {
        return requests.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING_APPROVAL);
    }

    @PostMapping("/{id}/decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(@PathVariable Long id, @RequestBody DecisionRequest body) {
        service.decide(id, body);
    }

    @GetMapping("/balance")
    public Map<String, Integer> balance(@RequestParam String employeeEmail) {
        return Map.of("remainingDays", service.balance(employeeEmail).getRemainingDays());
    }
}
