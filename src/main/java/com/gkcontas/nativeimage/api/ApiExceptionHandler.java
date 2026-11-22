package com.gkcontas.nativeimage.api;

import com.gkcontas.nativeimage.pricing.UnknownRegionException;
import com.gkcontas.nativeimage.product.DuplicateSkuException;
import com.gkcontas.nativeimage.product.ProductNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(ProductNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, List.of(e.getMessage()));
    }

    @ExceptionHandler(DuplicateSkuException.class)
    ResponseEntity<ErrorResponse> duplicate(DuplicateSkuException e) {
        return error(HttpStatus.CONFLICT, List.of(e.getMessage()));
    }

    @ExceptionHandler(UnknownRegionException.class)
    ResponseEntity<ErrorResponse> region(UnknownRegionException e) {
        return error(HttpStatus.BAD_REQUEST, List.of(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
        return error(HttpStatus.BAD_REQUEST, e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).sorted().toList());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> invalidParameter(HandlerMethodValidationException e) {
        return error(HttpStatus.BAD_REQUEST, e.getAllErrors().stream()
                .map(err -> err.getDefaultMessage()).sorted().toList());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> constraint(ConstraintViolationException e) {
        return error(HttpStatus.BAD_REQUEST, e.getConstraintViolations().stream()
                .map(v -> v.getMessage()).sorted().toList());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> badParameter(Exception e) {
        return error(HttpStatus.BAD_REQUEST, List.of("invalid or missing request parameter"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, List.of("malformed JSON body"));
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, List<String> details) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), status.getReasonPhrase(), details));
    }
}
