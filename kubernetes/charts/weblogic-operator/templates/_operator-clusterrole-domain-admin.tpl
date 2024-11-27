# Copyright (c) 2018, 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorClusterRoleDomainAdmin" }}
---
{{- $useClusterRole := and (or .enableClusterRoleBinding (not (hasKey . "enableClusterRoleBinding"))) (not (eq .domainNamespaceSelectionStrategy "Dedicated")) }}
{{- if $useClusterRole }}
kind: "ClusterRole"
{{- else }}
kind: "Role"
{{- end }}
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  {{- if $useClusterRole }}
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-domain-admin" | join "-" | quote }}
  {{- else }}
  name: "weblogic-operator-role-domain-admin"
  namespace: {{ .Release.Namespace | quote }}
  {{- end }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
rules:
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: [""]
  resources: ["secrets", "pods", "events"]
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources: ["pods/log"]
  verbs: ["get", "list"]
- apiGroups: [""]
  resources: ["pods/exec"]
  verbs: ["get", "create"]
- apiGroups: ["weblogic.oracle"]
  resources: ["domains", "clusters"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["weblogic.oracle"]
  resources: ["domains/status", "clusters/status"]
  verbs: ["get", "watch"]
{{- end }}
