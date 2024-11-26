# Copyright (c) 2018, 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.clusterRoleBindingGeneral" }}
---
apiVersion: "rbac.authorization.k8s.io/v1"
{{- $useClusterRole := and (default true .enableClusterRoleBinding) (not (eq .domainNamespaceSelectionStrategy "Dedicated")) }}
{{- if $useClusterRole }}
kind: "ClusterRoleBinding"
{{- else }}
kind: "RoleBinding"
{{- end }}
metadata:
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
  {{- if $useClusterRole }}
  name: {{ list .Release.Namespace "weblogic-operator-clusterrolebinding-general" | join "-" | quote }}
  {{- else }}
  name: "weblogic-operator-rolebinding-general"
  namespace: {{ .Release.Namespace | quote }}
  {{- end }}
roleRef:
  apiGroup: "rbac.authorization.k8s.io"
  {{- if $useClusterRole }}
  kind: "ClusterRole"
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-general" | join "-" | quote }}
  {{- else }}
  kind: "Role"
  name: "weblogic-operator-role-general"
  {{- end }}
subjects:
- kind: "ServiceAccount"
  apiGroup: ""
  name: {{ .serviceAccount | quote }}
  namespace: {{ .Release.Namespace | quote }}
{{- end }}
