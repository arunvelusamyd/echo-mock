package com.example.echomock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MockControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void echoesTransactionIdFromBodyAndTrackingIdFromHeader() throws Exception {
        mvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tracking-Id", "TRK-123")
                        .content("{\"transactionId\":\"TXN-999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.transactionId").value("TXN-999"))
                .andExpect(jsonPath("$.trackingId").value("TRK-123"));
    }

    @Test
    void trackingIdFallsBackToBodyWhenHeaderAbsent() throws Exception {
        mvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"TXN-1\",\"trackingId\":\"TRK-FROM-BODY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("TRK-FROM-BODY"));
    }

    @Test
    void rejectsPaymentOverLimitButStillEchoesId() throws Exception {
        mvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"TXN-BIG\",\"amount\":50000,\"currency\":\"SGD\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("AMOUNT_EXCEEDS_LIMIT"))
                .andExpect(jsonPath("$.transactionId").value("TXN-BIG"));
    }

    @Test
    void acceptsValidPayment() throws Exception {
        mvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tracking-Id", "TRK-OK")
                        .content("{\"transactionId\":\"TXN-OK\",\"amount\":100,\"currency\":\"SGD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.transactionId").value("TXN-OK"))
                .andExpect(jsonPath("$.trackingId").value("TRK-OK"));
    }

    @Test
    void echoesPathVariableAsTransactionId() throws Exception {
        mvc.perform(get("/api/transactions/TXN-PATH/status")
                        .header("X-Tracking-Id", "TRK-PATH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-PATH"))
                .andExpect(jsonPath("$.trackingId").value("TRK-PATH"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void returns404WhenNoMockMatches() throws Exception {
        mvc.perform(get("/no/such/endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NO_MOCK_MATCHED"))
                .andExpect(jsonPath("$.method").value("GET"));
    }

    @Test
    void rejectsUnsupportedCurrency() throws Exception {
        mvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"TXN-CUR\",\"amount\":100,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("UNSUPPORTED_CURRENCY"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void statusEndpointUsesQueryDefaultsWhenHeaderAndParamsAbsent() throws Exception {
        mvc.perform(get("/api/transactions/TXN-Q/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-Q"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void statusEndpointReflectsQueryTrackingIdAndStatus() throws Exception {
        mvc.perform(get("/api/transactions/TXN-Q2/status")
                        .param("trackingId", "TRK-FROM-QUERY")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("TRK-FROM-QUERY"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void methodMismatchOnKnownPathReturns404() throws Exception {
        // /api/payments is configured for POST only; a GET should not match.
        mvc.perform(get("/api/payments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NO_MOCK_MATCHED"));
    }

    @Test
    void echoesTrackingIdHeaderBackInResponseHeaders() throws Exception {
        mvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tracking-Id", "TRK-HDR")
                        .content("{\"transactionId\":\"TXN-H\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Tracking-Id", "TRK-HDR"));
    }
}
