# Kubernetes deployment

SwiftDrop ships a Helm chart in [`infra/helm/swiftdrop`](../infra/helm/swiftdrop). It runs the same
six services as `docker-compose.yml`. [`infra/k8s/deploy.sh`](../infra/k8s/deploy.sh) deploys it to a local
cluster in one command.

## What gets deployed

```
                       Ingress "swiftdrop" (Traefik)
              /api ─────────────┬──────────── /  (and /mailpit locally)
                                │
   ┌────────────────────┐   ┌───┴────────────────┐
   │ backend Deployment │   │ frontend Deployment│  nginx serves the React build
   │ Spring Boot, 1 pod │   │ 2 pods (1 locally) │
   └──┬───────┬──────┬──┘   └────────────────────┘
      │       │      │
 postgres  rabbitmq  redis       StatefulSets, each with a PersistentVolumeClaim
                                  mailpit Deployment catches every email
```

| Kind | Name | Purpose |
|------|------|---------|
| ConfigMap | `swiftdrop-config` | Non-secret backend settings (`config:` in values). Hosts, ports and the public URL are filled in for you |
| Secret | `swiftdrop-secrets` | Passwords, JWT secret, API key pepper, webhook key, admin login, optional mail and AI keys |
| Deployment + Service | `backend` | Spring Boot on port 8080, with startup, readiness and liveness probes on `/actuator/health/*` |
| PersistentVolumeClaim | `backend-proofs` | Courier proof-of-delivery photos at `/data/proofs`. `helm uninstall` keeps it |
| Deployment + Service | `frontend` | nginx on port 80, probe on `/healthz` |
| StatefulSet + headless Service + PVC | `postgres`, `rabbitmq`, `redis` | Data survives pod restarts |
| Deployment + Service | `mailpit` | SMTP on 1025, UI on 8025 under `/mailpit/` |
| Ingress | `swiftdrop` | `/api` to the backend (the SSE live streams too), `/` to the frontend, optional TLS |

Service names match docker-compose (`backend`, `postgres`, ...), because the frontend's nginx proxies to
`http://backend:8080` and the backend connects to the others by name. Install one release per namespace.

The backend runs without the `dev` profile here: Swagger is off, webhooks may not target private
addresses, and health details stay hidden.

## Run it locally

You need Docker Desktop, `kubectl`, [Helm](https://helm.sh/docs/intro/install/) 3.x or 4.x, and either
[kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) or Docker Desktop's built-in Kubernetes
(Settings > Kubernetes > Enable, kubeadm mode). Ports 80 and 443 must be free.

```bash
cp .env.example .env              # if you haven't already; the script reads secrets from it
infra/k8s/deploy.sh --create-kind # kind: creates the "swiftdrop" cluster first
infra/k8s/deploy.sh               # Docker Desktop, or any cluster that is already the current context
```

On Windows, run the script in Git Bash. It:

1. builds `swiftdrop-backend:local` and `swiftdrop-frontend:local` (and loads them into kind),
2. installs the Traefik ingress controller (chart 42.3.0) with [`traefik-values.yaml`](../infra/k8s/traefik-values.yaml),
3. creates the `swiftdrop-secrets` Secret from `.env`,
4. installs the chart with [`values-local.yaml`](../infra/helm/swiftdrop/values-local.yaml) and waits until every pod is ready.

Then open:

| What | URL |
|------|-----|
| Web app | http://localhost/ |
| API health | http://localhost/api/v1/health |
| Mailpit | http://localhost/mailpit/ |
| Admin sign-in | http://localhost/admin/login (`ADMIN_EMAIL` / `ADMIN_PASSWORD` from `.env`) |
| RabbitMQ UI | `kubectl port-forward -n swiftdrop svc/rabbitmq 15672:15672`, then http://localhost:15672 |

Run the script again after a code or `.env` change. A changed Secret restarts the backend pod by itself.

For Google sign-in, add `http://localhost` to the OAuth client's Authorized JavaScript origins.

Remove everything with `helm uninstall swiftdrop -n swiftdrop` and `kubectl delete namespace swiftdrop`
(deleting the namespace also deletes the data volumes). For kind, use `kind delete cluster --name swiftdrop`.

## Useful commands

```bash
kubectl get all,ingress,pvc -n swiftdrop
kubectl logs -n swiftdrop deploy/backend -f
kubectl describe pod -n swiftdrop -l app.kubernetes.io/component=backend
helm get values swiftdrop -n swiftdrop
helm template swiftdrop infra/helm/swiftdrop -f infra/helm/swiftdrop/values-local.yaml \
  --set secrets.existingSecret=swiftdrop-secrets      # print the plain manifests
```

## A real cluster

CI publishes the images to GHCR on every push to `main` (`latest`, `sha-<commit>`). A `v1.0.0` tag
publishes `1.0.0`, which is the chart's default image tag. If the packages are private, make them public
(Package settings > Change visibility) or add an `imagePullSecret`.

1. Point a DNS name at the cluster's ingress controller and create a TLS Secret
   (`kubectl create secret tls swiftdrop-tls --cert=... --key=... -n swiftdrop`, or use cert-manager).
2. Create `swiftdrop-secrets` yourself with the keys listed under `secrets.values` in
   [`values.yaml`](../infra/helm/swiftdrop/values.yaml). Use new random values (`openssl rand -base64 48`), not the `.env.example` ones.
3. Write a values file that you don't commit, for example:

   ```yaml
   ingress:
     host: swiftdrop.example.com
     tls:
       enabled: true
   secrets:
     existingSecret: swiftdrop-secrets
   config:
     GOOGLE_CLIENT_ID: "<client id>"
     MAIL_HOST: smtp.gmail.com
     MAIL_PORT: "587"
     MAIL_SMTP_AUTH: "true"
     MAIL_STARTTLS: "true"
     MAIL_FROM: you@gmail.com
   mailpit:
     enabled: false
   ```

   Put `MAIL_USERNAME` and `MAIL_PASSWORD` in the Secret.
4. `helm upgrade --install swiftdrop infra/helm/swiftdrop -n swiftdrop --create-namespace -f my-values.yaml --wait`

`FRONTEND_URL` (CORS) and `FRONTEND_BASE_URL` (email links) come from `ingress.host` unless you set them.

## Limits

- **One backend pod.** `WebhookRetryJob` runs on every instance with no lock, so two pods would send each
  due retry twice. The Deployment uses the `Recreate` strategy for the same reason. To scale out, add
  ShedLock (or `SELECT ... FOR UPDATE SKIP LOCKED`) first. The live streams already scale, because each pod
  has its own RabbitMQ queue.
- **Single-node data services.** PostgreSQL, RabbitMQ and Redis run one pod each. In production, use
  managed services or operators such as CloudNativePG and the RabbitMQ Cluster Operator.
- **Ingress timeouts.** The SSE streams need the ingress read timeout turned off. `traefik-values.yaml`
  does this; set the same on any other ingress controller.
