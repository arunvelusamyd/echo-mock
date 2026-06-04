import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

// Custom metric: counts responses that came back CORRECT in shape but WRONG in value.
// This is the signal for your concurrency bug — the request "succeeds" (200 OK)
// but the data is corrupted because threads stepped on each other.
const functionalErrors = new Counter('functional_errors');

export const options = {
  // Ramp concurrency UP in stages so you can see the load level at which
  // the bug starts appearing. Watch the functional_errors metric per stage.
  stages: [
    { duration: '30s', target: 20 },   // warm-up, baseline
    { duration: '1m',  target: 100 },  // moderate concurrency
    { duration: '2m',  target: 300 },  // peak / stress — bug most likely here
    { duration: '30s', target: 0 },    // ramp down
  ],
  thresholds: {
    http_req_failed:   ['rate<0.01'],   // <1% transport/5xx errors
    http_req_duration: ['p(95)<800'],   // 95th percentile latency budget (ms)
    functional_errors: ['count<1'],     // ANY functional corruption fails the run
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // ---------------------------------------------------------------------------
  // 1. Hit the endpoint you suspect.
  //    TIP for thread bugs: have many VUs hit the SAME resource/record
  //    (e.g. same id) so they contend over shared state. That's what surfaces
  //    races, lost updates, and non-thread-safe shared objects.
  // ---------------------------------------------------------------------------
  const payload = JSON.stringify({
    // ...your request body...
    id: 42, // same id across VUs = maximum contention
  });

  const res = http.post(`${BASE_URL}/api/your-endpoint`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  // ---------------------------------------------------------------------------
  // 2. Transport-level check — did the request even complete?
  // ---------------------------------------------------------------------------
  check(res, {
    'status is 200': (r) => r.status === 200,
  });

  // ---------------------------------------------------------------------------
  // 3. FUNCTIONAL correctness — this is the part that catches the thread bug.
  //    Assert the SPECIFIC invariant that you've seen break in production.
  //    Examples: a computed total matches its parts, a returned id matches the
  //    requested id, a status is never in an impossible state, no field is null
  //    that should always be set, etc.
  // ---------------------------------------------------------------------------
  let body;
  try {
    body = res.json();
  } catch (e) {
    functionalErrors.add(1);
    console.error(`Unparseable body @ VU${__VU} iter${__ITER}: ${res.body}`);
    return;
  }

  const correct = check(body, {
    'returned id matches requested': (b) => b && b.id === 42,
    'invariant holds':               (b) => b && b.total === b.partA + b.partB,
    // ^^^ replace with the real invariant that gets violated under load
  });

  if (!correct) {
    functionalErrors.add(1);
    // Logging VU + iteration + raw body makes interleaving visible afterward.
    console.error(`FUNCTIONAL FAIL @ VU${__VU} iter${__ITER}: ${res.body}`);
  }

  sleep(0.1); // small think-time; lower it to crank up real concurrency
}
