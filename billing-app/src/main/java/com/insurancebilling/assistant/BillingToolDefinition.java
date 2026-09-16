package com.insurancebilling.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One tool a machine consumer may call, and the schema its argument must satisfy.
 *
 * <p>Every tool in {@link BillingReadTools} takes exactly one string argument — an account or term
 * reference. That is a deliberate constraint rather than a simplification that will need undoing: a
 * caller that can only name one aggregate cannot ask for a join the application does not already
 * publish, and a single argument is small enough that the schema below can be written by hand and
 * read by anybody.
 *
 * <p>The schema is emitted as a nested {@link Map} rather than through a JSON Schema library. It is
 * four keys deep and Jackson serialises it directly, so a dependency would buy nothing but a version
 * to keep current.
 */
public record BillingToolDefinition(
    String name, String description, String argument, String argumentDescription) {

  /**
   * The JSON Schema for this tool's input.
   *
   * <p>{@code additionalProperties} is false and the single argument is required, because both are
   * preconditions for asking a model provider to validate arguments against the schema before the
   * call reaches us. A schema that permits unknown properties is a schema that cannot be enforced.
   */
  public Map<String, Object> inputSchema() {
    Map<String, Object> property = new LinkedHashMap<>();
    property.put("type", "string");
    property.put("description", argumentDescription);

    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put(argument, property);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", List.of(argument));
    schema.put("additionalProperties", false);
    return schema;
  }
}
