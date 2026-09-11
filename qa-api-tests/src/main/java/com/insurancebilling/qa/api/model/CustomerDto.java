package com.insurancebilling.qa.api.model;

/** A customer as the API publishes it. */
public record CustomerDto(Long id, String firstName, String lastName, String email) {}
