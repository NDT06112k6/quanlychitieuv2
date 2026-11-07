package com.example.expensetracker.support;

import com.example.expensetracker.auth.AuthConstants;
import com.example.expensetracker.auth.JwtService;
import com.example.expensetracker.auth.JwtTestFixtures;
import lombok.RequiredArgsConstructor;
import org.springframework.test.web.servlet.MockMvc;

@RequiredArgsConstructor
public class ControllerTestSupport {

    protected final MockMvc mockMvc;
    private final JwtService jwtService;

    protected String authorization() {
        String token = jwtService.createToken(JwtTestFixtures.PRINCIPAL);
        String header = AuthConstants.BEARER_PREFIX + token;
        return header;
    }
}
