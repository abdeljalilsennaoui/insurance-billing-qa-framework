package com.insurancebilling.api;

import com.insurancebilling.api.dto.PolicyRequest;
import com.insurancebilling.api.dto.PolicyResponse;
import com.insurancebilling.domain.PolicyStatus;
import com.insurancebilling.service.PolicyService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

  private final PolicyService policies;

  public PolicyController(PolicyService policies) {
    this.policies = policies;
  }

  @PostMapping
  public ResponseEntity<PolicyResponse> create(@Valid @RequestBody PolicyRequest request) {
    PolicyResponse created = PolicyResponse.from(policies.create(request));
    return ResponseEntity.created(URI.create("/api/policies/" + created.id())).body(created);
  }

  @GetMapping("/{id}")
  public PolicyResponse get(@PathVariable Long id) {
    return PolicyResponse.from(policies.findById(id));
  }

  /**
   * Changes a policy's lifecycle state.
   *
   * <p>Exposed because the automated suites need a lapsed or cancelled policy in order to test that
   * payments against one are refused. Building that state through the API keeps the test data owned by
   * the test rather than depending on a particular seeded row.
   */
  @PatchMapping("/{id}/status")
  public PolicyResponse updateStatus(@PathVariable Long id, @RequestBody PolicyStatusUpdate update) {
    return PolicyResponse.from(policies.updateStatus(id, update.status()));
  }

  /** Request body for a policy status change. */
  public record PolicyStatusUpdate(PolicyStatus status) {}
}
