#!/usr/bin/env bash
# Deploy SwiftDrop to the current kubectl context (a local kind or Docker Desktop cluster).
#
#   infra/k8s/deploy.sh                 build images, deploy to the current context
#   infra/k8s/deploy.sh --create-kind   first create the "swiftdrop" kind cluster
#   infra/k8s/deploy.sh --skip-build    reuse the swiftdrop-*:local images already built
#
# Needs: docker, kubectl, helm (and kind for --create-kind). Runs in Git Bash on Windows.
# Secrets come from the repo-root .env (the same file docker compose uses); they go into
# the swiftdrop-secrets Secret and never into the chart or the shell history.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NAMESPACE="${NAMESPACE:-swiftdrop}"
RELEASE="${RELEASE:-swiftdrop}"
TAG="local"
TRAEFIK_CHART_VERSION="42.3.0"
ENV_FILE="${ENV_FILE:-$ROOT/.env}"

create_kind=false
skip_build=false
for arg in "$@"; do
  case "$arg" in
    --create-kind) create_kind=true ;;
    --skip-build) skip_build=true ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

step() { printf '\n==> %s\n' "$*"; }
die() { echo "ERROR: $*" >&2; exit 1; }

for tool in docker kubectl helm; do
  command -v "$tool" >/dev/null || die "$tool is not installed"
done
[[ -f "$ENV_FILE" ]] || die "$ENV_FILE not found (cp .env.example .env and fill it in)"

# Last KEY=value line in .env, without CR, export prefix or surrounding quotes.
env_get() {
  local line value
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?$1=" "$ENV_FILE" | tail -n 1 || true)"
  value="${line#*=}"
  value="${value%$'\r'}"
  if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]; then value="${BASH_REMATCH[1]}"; fi
  printf '%s' "$value"
}

# ---------- Cluster ----------
if $create_kind; then
  command -v kind >/dev/null || die "kind is not installed"
  if kind get clusters 2>/dev/null | grep -qx swiftdrop; then
    step "kind cluster 'swiftdrop' already exists"
  else
    step "Creating kind cluster 'swiftdrop'"
    kind create cluster --config "$ROOT/infra/k8s/kind-config.yaml"
  fi
  kubectl config use-context kind-swiftdrop >/dev/null
fi

CONTEXT="$(kubectl config current-context 2>/dev/null)" || die "no kubectl context (enable Docker Desktop Kubernetes or use --create-kind)"
step "Deploying to context '$CONTEXT', namespace '$NAMESPACE'"

# ---------- Images ----------
if ! $skip_build; then
  step "Building images"
  docker build -t "swiftdrop-backend:$TAG" "$ROOT/backend"
  docker build -t "swiftdrop-frontend:$TAG" \
    --build-arg VITE_API_BASE_URL=/api/v1 --build-arg VITE_USE_MOCK_API=false "$ROOT/frontend"
fi
if [[ "$CONTEXT" == kind-* ]]; then
  command -v kind >/dev/null || die "kind is not installed (needed to load images into $CONTEXT)"
  step "Loading images into kind"
  kind load docker-image "swiftdrop-backend:$TAG" "swiftdrop-frontend:$TAG" --name "${CONTEXT#kind-}"
fi

# ---------- Ingress controller ----------
step "Installing Traefik $TRAEFIK_CHART_VERSION"
helm repo add traefik https://traefik.github.io/charts >/dev/null 2>&1 || true
helm repo update traefik >/dev/null
traefik_args=(-f "$ROOT/infra/k8s/traefik-values.yaml")
if [[ "$CONTEXT" == kind-* ]]; then
  # kind-config.yaml maps these node ports to localhost:80/443.
  traefik_args+=(--set service.type=NodePort --set ports.web.nodePort=30080 --set ports.websecure.nodePort=30443)
fi
helm upgrade --install traefik traefik/traefik --version "$TRAEFIK_CHART_VERSION" \
  -n traefik --create-namespace "${traefik_args[@]}" --wait

# ---------- Secret ----------
step "Writing Secret swiftdrop-secrets from $(basename "$ENV_FILE")"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f - >/dev/null

secret_args=()
add_secret() { # add_secret <secret key> <.env key> <required|optional>
  local value; value="$(env_get "$2")"
  if [[ -z "$value" ]]; then
    [[ "$3" == required ]] && die "$2 is empty in $ENV_FILE"
    return 0
  fi
  secret_args+=(--from-literal="$1=$value")
}
for key in POSTGRES_USER POSTGRES_PASSWORD RABBITMQ_USER RABBITMQ_PASSWORD REDIS_PASSWORD \
           JWT_SECRET API_KEY_PEPPER WEBHOOK_SECRET_KEY ADMIN_EMAIL ADMIN_PASSWORD; do
  add_secret "$key" "$key" required
done
for key in JWT_SECRET API_KEY_PEPPER WEBHOOK_SECRET_KEY; do
  value="$(env_get "$key")"
  (( ${#value} >= 32 )) || die "$key must be at least 32 characters"
done
# The cluster runs without the dev profile, so the assistant keys use Spring's relaxed names.
add_secret SWIFTDROP_ASSISTANT_APIKEY SWIFTDROP_ASSISTANT_API_KEY optional
add_secret SWIFTDROP_ASSISTANT_OPENROUTER_APIKEY SWIFTDROP_ASSISTANT_OPENROUTER_API_KEY optional
add_secret SWIFTDROP_ASSISTANT_GROK_APIKEY SWIFTDROP_ASSISTANT_GROK_API_KEY optional

kubectl create secret generic swiftdrop-secrets -n "$NAMESPACE" "${secret_args[@]}" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

# ---------- SwiftDrop ----------
# A hash of the Secret on the backend pod, so a changed .env restarts it (and nothing else does).
secret_hash="$(printf '%s\n' "${secret_args[@]}" | sha256sum | cut -c1-16)"
chart_args=(-f "$ROOT/infra/helm/swiftdrop/values-local.yaml" --set secrets.existingSecret=swiftdrop-secrets
            --set-string "backend.podAnnotations.checksum/secret=$secret_hash")
set_config() { # set_config <config key> <.env key>
  local value; value="$(env_get "$2")"
  [[ -n "$value" ]] && chart_args+=(--set-string "config.$1=$value")
  return 0
}
set_config GOOGLE_CLIENT_ID GOOGLE_CLIENT_ID
set_config ADMIN_FULL_NAME ADMIN_FULL_NAME
set_config SWIFTDROP_ASSISTANT_PROVIDER SWIFTDROP_ASSISTANT_PROVIDER
set_config SWIFTDROP_ASSISTANT_MODEL SWIFTDROP_ASSISTANT_MODEL
set_config SWIFTDROP_ASSISTANT_BASEURL SWIFTDROP_ASSISTANT_BASE_URL
set_config SWIFTDROP_ASSISTANT_OPENROUTER_MODEL SWIFTDROP_ASSISTANT_OPENROUTER_MODEL
set_config SWIFTDROP_ASSISTANT_GROK_MODEL SWIFTDROP_ASSISTANT_GROK_MODEL

step "Installing the SwiftDrop chart (waits until every pod is ready)"
helm upgrade --install "$RELEASE" "$ROOT/infra/helm/swiftdrop" -n "$NAMESPACE" \
  "${chart_args[@]}" --wait --timeout 10m

step "Done"
kubectl get pods,svc,ingress -n "$NAMESPACE"
cat <<EOF

Web app:  http://localhost/
API:      http://localhost/api/v1/health
Mailpit:  http://localhost/mailpit/
Admin:    http://localhost/admin/login  (ADMIN_EMAIL / ADMIN_PASSWORD from .env)
Remove:   helm uninstall $RELEASE -n $NAMESPACE   (kind: kind delete cluster --name swiftdrop)
EOF
