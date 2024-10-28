# Copyright (c) 2018, 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorRoleBindingNamespace" }}
---
{{- $useClusterRole := and (or .enableClusterRoleBinding (not (hasKey . "enableClusterRoleBinding"))) (ne .domainNamespaceSelectionStrategy "Dedicated") }}
{{- if $useClusterRole }}
kind: "ClusterRoleBinding"
{{- else }}
kind: "RoleBinding"
{{- end }}
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  {{- if $useClusterRole }}
  name: {{ list .Release.Namespace "weblogic-operator-clusterrolebinding-namespace" | join "-" | quote }}
  {{- else }}
  name: "weblogic-operator-rolebinding-namespace"
  namespace: {{ .domainNamespace | quote }}
  {{- end }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
subjects:
- kind: "ServiceAccount"
  name: {{ .serviceAccount | quote }}
  namespace: {{ .Release.Namespace | quote }}
  apiGroup: ""
roleRef:
  {{- if $useClusterRole }}
  kind: "ClusterRole"
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-namespace" | join "-" | quote }}
  {{- else }}
  kind: "Role"
  name: "weblogic-operator-role-namespace"
  {{- end }}
  apiGroup: "rbac.authorization.k8s.io"
{{- end }}
