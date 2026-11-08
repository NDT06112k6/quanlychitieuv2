package com.example.expensetracker.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class StaticWebSecurity {

    public static final String ROOT = "/";
    public static final String INDEX = "/index.html";
    public static final String CSS_ASSETS = "/css/**";
    public static final String JS_ASSETS = "/js/**";
    public static final String FAVICON = "/favicon.ico";
    public static final List<String> PUBLIC_PATHS = List.of(ROOT, INDEX, CSS_ASSETS, JS_ASSETS, FAVICON);
    public static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "connect-src 'self'; img-src 'self'; font-src 'self'; object-src 'none'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'none'";

    private StaticWebSecurity() {
    }

    public static HeaderWriter contentSecurityPolicyWriter() {
        List<RequestMatcher> matchers = new ArrayList<>();
        for (String path : PUBLIC_PATHS) {
            matchers.add(PathPatternRequestMatcher.withDefaults().matcher(path));
        }
        RequestMatcher staticAssets = new OrRequestMatcher(matchers);
        HeaderWriter policy = new ContentSecurityPolicyHeaderWriter(CONTENT_SECURITY_POLICY);
        HeaderWriter writer = new DelegatingRequestMatcherHeaderWriter(staticAssets, policy);
        return writer;
    }
}
