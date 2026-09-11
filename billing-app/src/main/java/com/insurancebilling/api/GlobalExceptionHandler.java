package com.insurancebilling.api;

import com.insurancebilling.api.dto.ErrorResponse;
import com.insurancebilling.domain.PaymentRejectedException;
import com.insurancebilling.service.DuplicateEmailException;
import com.insurancebilling.service.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps exceptions to the single {@link ErrorResponse} shape.
 *
 * <p>The status codes are chosen so an automated test can tell failure kinds apart:
 *
 * <ul>
 *   <li><b>400</b> — the request could not be understood: missing field, wrong type, unparseable body.
 *   <li><b>404</b> — the request referenced something that does not exist.
 *   <li><b>409</b> — the request is valid but conflicts with existing state.
 *   <li><b>422</b> — the request was understood and a billing rule refused it. The {@code code} field
 *       carries the specific {@link com.insurancebilling.domain.PaymentRejectionReason}.
 * </ul>
 *
 * <p>Without the 400/422 split, a test could not distinguish "the client sent a malformed amount" from
 * "the platform rejected an overpayment", and a regression that turned one into the other would go
 * unnoticed.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError error : exception.getBindingResult().getFieldErrors()) {
      fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    return ResponseEntity.badRequest()
        .body(
            ErrorResponse.validation(
                HttpStatus.BAD_REQUEST.value(),
                "Request validation failed",
                request.getRequestURI(),
                fieldErrors));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "MALFORMED_REQUEST",
                "Request body could not be parsed",
                request.getRequestURI()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "MALFORMED_REQUEST",
                "Parameter '" + exception.getName() + "' has an invalid value",
                request.getRequestURI()));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      ResourceNotFoundException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                exception.getMessage(),
                request.getRequestURI()));
  }

  @ExceptionHandler(DuplicateEmailException.class)
  public ResponseEntity<ErrorResponse> handleDuplicateEmail(
      DuplicateEmailException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "DUPLICATE_EMAIL",
                exception.getMessage(),
                request.getRequestURI()));
  }

  @ExceptionHandler(PaymentRejectedException.class)
  public ResponseEntity<ErrorResponse> handlePaymentRejected(
      PaymentRejectedException exception, HttpServletRequest request) {
    return ResponseEntity.unprocessableEntity()
        .body(
            ErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                exception.getReason().name(),
                exception.getMessage(),
                request.getRequestURI()));
  }
}
