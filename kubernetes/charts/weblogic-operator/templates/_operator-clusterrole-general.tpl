# Copyright (c) 2018, 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorClusterRoleGeneral" }}
---
{{- $useClusterRole := and (or .enableClusterRoleBinding (not (hasKey . "enableClusterRoleBinding"))) (ne .domainNamespaceSelectionStrategy "Dedicated") }}
{{- if $useClusterRole }}
kind: "ClusterRole"
{{- else }}
kind: "Role"
{{- end }}
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  {{- if $useClusterRole }}
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-general" | join "-" | quote }}
  {{- else }}
  name: "weblogic-operator-role-general"
  namespace: {{ .Release.Namespace | quote }}
  {{- end }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
rules:
{{- if $useClusterRole }}
- apiGroups: [""]
  resources: ["namespaces"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["apiextensions.k8s.io"]
  resources: ["customresourcedefinitions"]
  verbs: ["get", "list", "watch", "create", "update", "patch"]
{{- end }}
- apiGroups: [""]
  resources: ["persistentvolumes"]
  verbs: ["get", "list", "create"]
- apiGroups: ["weblogic.oracle"]
  resources: ["domains", "clusters", "domains/status", "clusters/status"]
  verbs: ["get", "create", "list", "watch", "update", "patch"]
- apiGroups: ["authentication.k8s.io"]
  resources: ["tokenreviews"]
  verbs: ["create"]
- apiGroups: ["authorization.k8s.io"]
  resources: ["selfsubjectrulesreviews"]
  verbs: ["create"]
- apiGroups: ["admissionregistration.k8s.io"]
  resources: ["validatingwebhookconfigurations"]
  verbs: ["get", "create", "update", "patch", "delete"]
{{- end }}
