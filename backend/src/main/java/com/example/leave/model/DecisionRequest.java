package com.example.leave.model;

/** Body of POST /api/leaves/{id}/decision sent by the Manager Inbox. */
public record DecisionRequest(boolean approved, String comment) {}
