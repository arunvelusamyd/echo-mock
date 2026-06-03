package com.example.echomock.engine;

import com.example.echomock.model.ResponseSpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseRendererTest {

    private final ResponseRenderer renderer = new ResponseRenderer(new TemplateResolver());

    private RequestContext ctx(String body) {
        return new RequestContext("POST", Map.of("x-tracking-id", "TRK-1"),
                Map.of("forceStatus", "418"), Map.of(), body);
    }

    @Test
    void rendersStringBodyWithTemplatesAndDefaultsJsonContentType() {
        ResponseSpec spec = new ResponseSpec();
        spec.setStatus("200");
        spec.setBody("{\"transactionId\":\"${body.txn}\"}");

        ResponseEntity<String> resp = renderer.render(spec, ctx("{\"txn\":\"T1\"}"));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo("{\"transactionId\":\"T1\"}");
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void serializesStructuredMapBodyToJsonAndTemplatesIt() {
        ResponseSpec spec = new ResponseSpec();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionId", "${body.txn}");
        body.put("status", "OK");
        spec.setBody(body);

        ResponseEntity<String> resp = renderer.render(spec, ctx("{\"txn\":\"T2\"}"));

        assertThat(resp.getBody()).contains("\"transactionId\":\"T2\"").contains("\"status\":\"OK\"");
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void resolvesStatusFromTemplate() {
        ResponseSpec spec = new ResponseSpec();
        spec.setStatus("${query.forceStatus}");
        spec.setBody("hi");

        ResponseEntity<String> resp = renderer.render(spec, ctx("{}"));
        assertThat(resp.getStatusCode().value()).isEqualTo(418);
    }

    @Test
    void invalidStatusFallsBackTo200() {
        ResponseSpec spec = new ResponseSpec();
        spec.setStatus("not-a-number");
        spec.setBody("hi");

        ResponseEntity<String> resp = renderer.render(spec, ctx("{}"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void nonJsonBodyGetsTextPlainContentType() {
        ResponseSpec spec = new ResponseSpec();
        spec.setBody("just text");

        ResponseEntity<String> resp = renderer.render(spec, ctx("{}"));
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
    }

    @Test
    void nullBodyRendersEmptyString() {
        ResponseSpec spec = new ResponseSpec();
        spec.setBody(null);

        ResponseEntity<String> resp = renderer.render(spec, ctx("{}"));
        assertThat(resp.getBody()).isEmpty();
    }

    @Test
    void customHeadersAreTemplatedAndRespectExplicitContentType() {
        ResponseSpec spec = new ResponseSpec();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Tracking-Id", "${header.X-Tracking-Id}");
        headers.put(HttpHeaders.CONTENT_TYPE, "application/xml");
        spec.setHeaders(headers);
        spec.setBody("<x/>");

        ResponseEntity<String> resp = renderer.render(spec, ctx("{}"));
        assertThat(resp.getHeaders().getFirst("X-Tracking-Id")).isEqualTo("TRK-1");
        assertThat(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/xml");
    }

    @Test
    void appliesConfiguredDelay() {
        ResponseSpec spec = new ResponseSpec();
        spec.setBody("ok");
        spec.setDelayMs(20);

        long start = System.nanoTime();
        renderer.render(spec, ctx("{}"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isGreaterThanOrEqualTo(15);
    }
}
