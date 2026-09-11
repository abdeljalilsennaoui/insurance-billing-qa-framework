package com.insurancebilling.service;

/** Thrown when a request references an entity that does not exist. Mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String resource, Object identifier) {
    super(resource + " " + identifier + " was not found");
  }
}
