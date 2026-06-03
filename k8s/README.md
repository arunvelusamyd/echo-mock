# Deploying echo-mock to Kubernetes

Plain YAML manifests for an on-prem cluster. The service runs internal-only
(`ClusterIP`) so the API under test reaches it at `http://echo-mock:8080`, with an
optional `Ingress` for external testing.

## Manifests

| File             | Purpose                                                          |
|------------------|------------------------------------------------------------------|
| `namespace.yaml` | Namespace `echo-mock`                                            |
| `configmap.yaml` | `mocks.yml` scenarios — **edit this to change mock behaviour**   |
| `deployment.yaml`| Deployment (2 replicas), probes, ConfigMap mount, hardened pod   |
| `service.yaml`   | `ClusterIP echo-mock:8080` — the in-cluster address the API uses |
| `ingress.yaml`   | External host → service (for manual testing)                    |

## 1. Build & push the image

The on-prem registry path is a placeholder — replace `your-registry.example.com`.

```bash
docker build -t your-registry.example.com/echo-mock:1.0.0 .
docker push your-registry.example.com/echo-mock:1.0.0
```

Then set that same reference in `deployment.yaml` (`spec.template.spec.containers[0].image`).

## 2. Deploy

```bash
kubectl apply -f k8s/
kubectl -n echo-mock rollout status deployment/echo-mock
```

## 3. Verify

```bash
# Health (port-forward)
kubectl -n echo-mock port-forward svc/echo-mock 8080:8080 &
curl localhost:8080/__admin/health          # {"status":"UP","mocks":3}

# Core echo test
curl -X POST localhost:8080/api/transactions \
  -H 'Content-Type: application/json' -H 'X-Tracking-Id: TRK-001' \
  -d '{"transactionId":"TXN-555"}'           # echoes TXN-555 / TRK-001

# In-cluster reachability (proves the API can call it by service name)
kubectl -n echo-mock run curl --rm -it --image=curlimages/curl --restart=Never -- \
  curl -s http://echo-mock:8080/__admin/health

# External via Ingress (set host to match ingress.yaml; <ingress-ip> from your controller)
curl -H 'Host: echo-mock.local' http://<ingress-ip>/__admin/health
```

## 4. Change mock scenarios (no image rebuild)

Edit `configmap.yaml`, then **restart** so every replica picks it up:

```bash
kubectl apply -f k8s/configmap.yaml
kubectl -n echo-mock rollout restart deployment/echo-mock
```

> Why restart instead of `POST /__admin/reload`? The reload endpoint only reloads the
> single pod that receives the request. With 2 replicas behind the Service that's
> non-deterministic, so `rollout restart` is the reliable way to reload all pods.
> (If you prefer the reload endpoint, set `replicas: 1` in `deployment.yaml`.)

## Notes / how the calling API connects

- **Same namespace:** `http://echo-mock:8080`
- **Other namespace:** `http://echo-mock.echo-mock.svc.cluster.local:8080`
- Override the listen port with the `MOCK_PORT` env var; the external config path is
  `MOCK_CONFIG_PATH=/etc/echo-mock/mocks.yml` (the mounted ConfigMap).
- The pod runs non-root (uid 10001), read-only root filesystem with a writable
  `/tmp` emptyDir, and no service-account token mounted.

## Adjust before production

- `ingressClassName` in `ingress.yaml` (`nginx` placeholder) → your controller's class.
- `host` in `ingress.yaml` (`echo-mock.local`) → a real DNS name; add TLS if needed.
- Resource requests/limits in `deployment.yaml` if your scenarios are heavier.
