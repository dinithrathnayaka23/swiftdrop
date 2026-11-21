{{/* Common labels. Call with (dict "root" $ "component" "backend"). */}}
{{- define "swiftdrop.labels" -}}
{{ include "swiftdrop.selectorLabels" . }}
app.kubernetes.io/part-of: swiftdrop
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version }}
{{- end }}

{{- define "swiftdrop.selectorLabels" -}}
app.kubernetes.io/name: swiftdrop
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end }}

{{/* Image reference. Call with (dict "root" $ "image" .Values.backend.image). */}}
{{- define "swiftdrop.image" -}}
{{ printf "%s:%s" .image.repository (default .root.Chart.AppVersion .image.tag) }}
{{- end }}

{{- define "swiftdrop.secretName" -}}
{{ .Values.secrets.existingSecret | default "swiftdrop-secrets" }}
{{- end }}

{{/* Public URL of the web app, from the ingress settings. */}}
{{- define "swiftdrop.publicUrl" -}}
{{- $scheme := ternary "https" "http" .Values.ingress.tls.enabled -}}
{{ printf "%s://%s" $scheme (default "localhost" .Values.ingress.host) }}
{{- end }}

{{/* Volume claim template for the stateful services. Call with (dict "root" $ "size" "1Gi"). */}}
{{- define "swiftdrop.volumeClaim" -}}
- metadata:
    name: data
  spec:
    accessModes: ["ReadWriteOnce"]
    {{- with .root.Values.storageClass }}
    storageClassName: {{ . }}
    {{- end }}
    resources:
      requests:
        storage: {{ .size }}
{{- end }}
