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
