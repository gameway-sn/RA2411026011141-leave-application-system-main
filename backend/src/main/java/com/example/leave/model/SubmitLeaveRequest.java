package com.example.leave.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Body of POST /api/leaves sent by the React Apply form. */
public record SubmitLeaveRequest(
        @NotBlank String employeeName,
        @NotBlank @Email String employeeEmail,
        @NotBlank String leaveType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String reason
) {}
