package com.financehub.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class SecurityProblems {
    private final ObjectMapper mapper;
    public SecurityProblems(ObjectMapper mapper) { this.mapper=mapper; }
    public void write(HttpServletRequest request,HttpServletResponse response,int status,String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        var problem=ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.valueOf(status),detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        mapper.writeValue(response.getOutputStream(),problem);
    }
}
