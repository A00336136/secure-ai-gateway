package com.secureai.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelCoverageTest {
    @Test
    void testErrorResponseGetters() {
        ErrorResponse er = new ErrorResponse(404, "Not Found", "Missing", "/api/test");
        assertEquals(404, er.getStatus());
        assertEquals("Not Found", er.getError());
        assertEquals("Missing", er.getMessage());
        assertEquals("/api/test", er.getPath());
        assertNotNull(er.getTimestamp());
    }

    @Test
    void testLoginResponseGettersSetters() {
        LoginResponse lr = new LoginResponse();
        lr.setToken("abc");
        lr.setExpiresIn(123L);
        lr.setUsername("bob");
        lr.setRole("ADMIN");
        assertEquals("abc", lr.getToken());
        assertEquals(123L, lr.getExpiresIn());
        assertEquals("bob", lr.getUsername());
        assertEquals("ADMIN", lr.getRole());
        assertEquals("Bearer", lr.getTokenType());
    }

    @Test
    void testAskResponseGettersSetters() {
        AskResponse ar = new AskResponse();
        ar.setResponse("resp");
        ar.setPiiDetected(true);
        ar.setPiiRedacted(true);
        ar.setReactSteps(2);
        ar.setDurationMs(10L);
        ar.setModel("m");
        assertEquals("resp", ar.getResponse());
        assertTrue(ar.isPiiDetected());
        assertTrue(ar.isPiiRedacted());
        assertEquals(2, ar.getReactSteps());
        assertEquals(10L, ar.getDurationMs());
        assertEquals("m", ar.getModel());
    }

    @Test
    void testLoginRequestGettersSetters() {
        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("pw");
        assertEquals("alice", req.getUsername());
        assertEquals("pw", req.getPassword());
    }

    @Test
    void testLoginRequestParameterizedConstructor() {
        LoginRequest req = new LoginRequest("user1", "pass1");
        assertEquals("user1", req.getUsername());
        assertEquals("pass1", req.getPassword());
    }

    @Test
    void testLoginResponseParameterizedConstructor() {
        LoginResponse lr = new LoginResponse("token123", 3600L, "admin", "ADMIN");
        assertEquals("token123", lr.getToken());
        assertEquals(3600L, lr.getExpiresIn());
        assertEquals("admin", lr.getUsername());
        assertEquals("ADMIN", lr.getRole());
        assertEquals("Bearer", lr.getTokenType());
    }

    @Test
    void testAskResponseParameterizedConstructor() {
        AskResponse ar = new AskResponse("answer", true, true, 3, 500L, "llama3");
        assertEquals("answer", ar.getResponse());
        assertTrue(ar.isPiiDetected());
        assertTrue(ar.isPiiRedacted());
        assertEquals(3, ar.getReactSteps());
        assertEquals(500L, ar.getDurationMs());
        assertEquals("llama3", ar.getModel());
    }

    @Test
    void testRegisterRequestGettersSetters() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setPassword("pw");
        req.setEmail("a@b.com");
        assertEquals("alice", req.getUsername());
        assertEquals("pw", req.getPassword());
        assertEquals("a@b.com", req.getEmail());
    }

    @Test
    void testAskRequestGettersSetters() {
        AskRequest req = new AskRequest();
        req.setPrompt("p");
        req.setUseReActAgent(true);
        assertEquals("p", req.getPrompt());
        assertTrue(req.isUseReActAgent());
    }
}
