package com.gkcontas.multitenancy.api;

import com.gkcontas.multitenancy.tenant.TenantNotResolvedException;
import com.gkcontas.multitenancy.tenant.UnknownTenantException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorHandler {

    /**
     * A request with no tenant is a bad request and not a server error: the
     * application cannot guess, and guessing is the bug scenario
     * {@code tenant-resolution} measures.
     */
    @ExceptionHandler(TenantNotResolvedException.class)
    public ProblemDetail noTenant(TenantNotResolvedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                e.getMessage());
        problem.setTitle("Missing tenant");
        problem.setProperty("header", "X-Tenant-Id");
        return problem;
    }

    @ExceptionHandler(UnknownTenantException.class)
    public ProblemDetail unknownTenant(UnknownTenantException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                e.getMessage());
        problem.setTitle("Unknown tenant");
        return problem;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail notFound(NoSuchElementException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                e.getMessage());
        problem.setTitle("Not found");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request body is not valid");
        problem.setTitle("Validation failed");
        problem.setProperty("fields", fields);
        return problem;
    }
}
