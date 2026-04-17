{{/* ── 공통 헬퍼 (이름, 라벨, 셀렉터) ────────────────────────────────────── */}}

{{- define "claude-toolkit.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "claude-toolkit.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "claude-toolkit.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "claude-toolkit.labels" -}}
helm.sh/chart: {{ include "claude-toolkit.chart" . }}
{{ include "claude-toolkit.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "claude-toolkit.selectorLabels" -}}
app.kubernetes.io/name: {{ include "claude-toolkit.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "claude-toolkit.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "claude-toolkit.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "claude-toolkit.dbPort" -}}
{{- if eq (int .Values.db.port) 0 -}}
{{- if eq .Values.db.type "mysql" -}}3306{{- end -}}
{{- if eq .Values.db.type "postgresql" -}}5432{{- end -}}
{{- if eq .Values.db.type "h2" -}}0{{- end -}}
{{- else -}}
{{- .Values.db.port -}}
{{- end -}}
{{- end -}}
