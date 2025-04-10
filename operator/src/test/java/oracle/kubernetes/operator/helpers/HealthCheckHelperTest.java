// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ResourceRule;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.Collections.singletonList;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_UID_UNIQUENESS_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.PV_ACCESS_MODE_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.PV_NOT_FOUND_FOR_DOMAIN_UID;
import static oracle.kubernetes.common.logging.MessageKeys.VERIFY_ACCESS_DENIED;
import static oracle.kubernetes.common.logging.MessageKeys.VERIFY_ACCESS_DENIED_WITH_NS;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.CREATE;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.DELETE;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.DELETECOLLECTION;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.GET;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.LIST;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.PATCH;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.UPDATE;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.WATCH;
import static org.hamcrest.MatcherAssert.assertThat;

class HealthCheckHelperTest {

  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
      DOMAIN_UID_UNIQUENESS_FAILED,
      PV_ACCESS_MODE_FAILED,
      PV_NOT_FOUND_FOR_DOMAIN_UID,
      VERIFY_ACCESS_DENIED,
      VERIFY_ACCESS_DENIED_WITH_NS
  };

  private static final String NS1 = "ns1";
  private static final String NS2 = "ns2";
  private static final String OPERATOR_NAMESPACE = "op1";
  private static final List<String> TARGET_NAMESPACES = Arrays.asList(NS1, NS2);
  private static final List<String> CRUD_RESOURCES =
      Arrays.asList(
          "configmaps",
          "events",
          "jobs//batch",
          "pods",
          "services");

  private static final List<String> CLUSTER_CRUD_RESOURCES =
        singletonList("customresourcedefinitions//apiextensions.k8s.io");

  private static final List<String> CLUSTER_READ_WATCH_RESOURCES =
        singletonList("namespaces");

  private static final List<String> CLUSTER_READ_UPDATE_RESOURCES =
      Arrays.asList("domains//weblogic.oracle", "domains/status/weblogic.oracle");

  private static final List<String> CREATE_AND_GET_RESOURCES =
      Arrays.asList("pods/exec", "tokenreviews//authentication.k8s.io",
          "selfsubjectrulesreviews//authorization.k8s.io");

  private static final List<String> READ_WATCH_RESOURCES =
        singletonList("secrets");

  private static final List<Operation> CRUD_OPERATIONS =
      Arrays.asList(GET, LIST, WATCH, CREATE, UPDATE, PATCH, DELETE, DELETECOLLECTION);

  private static final List<Operation> CRD_OPERATIONS =
      Arrays.asList(GET, LIST, WATCH, CREATE, UPDATE, PATCH);

  private static final List<Operation> READ_ONLY_OPERATIONS = Arrays.asList(GET, LIST);

  private static final List<Operation> READ_WATCH_OPERATIONS = Arrays.asList(GET, LIST, WATCH);

  private static final List<Operation> READ_UPDATE_OPERATIONS =
      Arrays.asList(GET, LIST, WATCH, UPDATE, PATCH);

  private static final List<Operation> CREATE_GET_OPERATIONS =
      Arrays.asList(CREATE, GET);

  private static final String POD_LOGS = "pods/log";

  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final AccessChecks accessChecks = new AccessChecks();

  @BeforeEach
  void setUp() throws Exception {
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
    mementos.add(TestUtils.silenceOperatorLogger().collectLogMessages(logRecords, LOG_KEYS));
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenRulesReviewSupported_accessGrantedForEverything() {
    expectSelfSubjectRulesReview();

    for (String ns : TARGET_NAMESPACES) {
      V1SubjectRulesReviewStatus status = HealthCheckHelper.getSelfSubjectRulesReviewStatus(ns);
      HealthCheckHelper.verifyAccess(status, ns, true);
    }
  }

  @Test
  void whenRulesReviewSupportedAndNoDomainNamespaceAccess_logWarning() {
    accessChecks.setMayAccessNamespace(false);
    expectSelfSubjectRulesReview();

    for (String ns : TARGET_NAMESPACES) {
      V1SubjectRulesReviewStatus status = HealthCheckHelper.getSelfSubjectRulesReviewStatus(ns);
      HealthCheckHelper.verifyAccess(status, ns, true);
    }

    assertThat(logRecords, containsWarning(VERIFY_ACCESS_DENIED_WITH_NS));
  }

  @Test
  void whenRulesReviewSupportedAndNoOperatorNamespaceAccess_logWarning() {
    accessChecks.setMayAccessNamespace(false);
    expectSelfSubjectRulesReview();

    V1SubjectRulesReviewStatus status = HealthCheckHelper.getSelfSubjectRulesReviewStatus(OPERATOR_NAMESPACE);
    HealthCheckHelper.verifyAccess(status, OPERATOR_NAMESPACE, false);

    assertThat(logRecords, containsWarning(VERIFY_ACCESS_DENIED_WITH_NS));
  }

  private void expectSelfSubjectRulesReview() {
    testSupport.defineResources(new V1SelfSubjectRulesReview().status(accessChecks.createRulesStatus()));
  }

  @SuppressWarnings("SameParameterValue")
  static class AccessChecks {
    private static final boolean MAY_ACCESS_CLUSTER = true;

    private boolean mayAccessNamespace = true;

    private static String getResource(String resourceString) {
      return resourceString.split("/")[0];
    }

    private static String getSubresource(String resourceString) {
      String[] split = resourceString.split("/");
      return split.length <= 1 ? "" : split[1];
    }

    private static String getApiGroup(String resourceString) {
      String[] split = resourceString.split("/");
      return split.length <= 2 ? "" : split[2];
    }

    void setMayAccessNamespace(boolean mayAccessNamespace) {
      this.mayAccessNamespace = mayAccessNamespace;
    }

    private V1SubjectRulesReviewStatus createRulesStatus() {
      return new V1SubjectRulesReviewStatus().resourceRules(createRules());
    }

    private List<V1ResourceRule> createRules() {
      List<V1ResourceRule> rules = new ArrayList<>();
      if (mayAccessNamespace) {
        addNamespaceRules(rules);
      }
      if (MAY_ACCESS_CLUSTER) {
        addClusterRules(rules);
      }
      return rules;
    }

    private void addNamespaceRules(List<V1ResourceRule> rules) {
      rules.add(createRule(CRUD_RESOURCES, CRUD_OPERATIONS));
      rules.add(createRule(READ_WATCH_RESOURCES, READ_WATCH_OPERATIONS));
      rules.add(createRule(singletonList(POD_LOGS), READ_ONLY_OPERATIONS));
      rules.add(createRule(CREATE_AND_GET_RESOURCES, CREATE_GET_OPERATIONS));
    }

    private void addClusterRules(List<V1ResourceRule> rules) {
      rules.add(createRule(CLUSTER_CRUD_RESOURCES, CRD_OPERATIONS));
      rules.add(createRule(CLUSTER_READ_UPDATE_RESOURCES, READ_UPDATE_OPERATIONS));
      rules.add(createRule(CLUSTER_READ_WATCH_RESOURCES, READ_WATCH_OPERATIONS));
    }

    private V1ResourceRule createRule(List<String> resourceStrings, List<Operation> operations) {
      return new V1ResourceRule()
          .apiGroups(getApiGroups(resourceStrings))
          .resources(getResources(resourceStrings))
          .verbs(toVerbs(operations));
    }

    private List<String> getApiGroups(List<String> resourceStrings) {
      return resourceStrings.stream()
          .map(AccessChecks::getApiGroup)
          .distinct()
          .toList();
    }

    private List<String> getResources(List<String> resourceStrings) {
      return resourceStrings.stream().map(this::getFullResource).toList();
    }

    private String getFullResource(String resourceString) {
      String resource = getResource(resourceString);
      String subresource = getSubresource(resourceString);
      return subresource.isEmpty() ? resource : resource + "/" + subresource;
    }

    private List<String> toVerbs(List<Operation> operations) {
      return operations.stream().map(Operation::toString).toList();
    }
  }
}
