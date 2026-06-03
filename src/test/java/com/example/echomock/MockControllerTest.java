package com.example.echomock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.error").value("NO_MOCK_MATCHED"));
    }
}
