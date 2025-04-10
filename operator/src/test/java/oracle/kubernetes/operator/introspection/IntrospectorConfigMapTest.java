// Copyright (c) 2020, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.introspection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.EventTestUtils;
import oracle.kubernetes.operator.IntrospectorConfigMapConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.ConfigMapSplitter;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainTopology;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.http.rest.ScanCacheStub;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.lang.System.lineSeparator;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INVALID_EVENT_ERROR;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.EventTestUtils.getLocalizedString;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.DOMAINZIP_HASH;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.DOMAIN_INPUTS_HASH;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.DOMAIN_RESTART_VERSION;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.NUM_CONFIG_MAPS;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.SECRETS_MD_5;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.TOPOLOGY_YAML;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.getIntrospectorConfigMapNamePrefix;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILED;
import static oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory.forDomain;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.DOMAIN_INVALID;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class IntrospectorConfigMapTest {

  private static final int TEST_DATA_LIMIT = 1000;
  private static final int DATA_ALLOWANCE = 500;  // assumed size of data that will not be split
  private static final int NUM_MAPS_TO_CREATE = 3;
  private static final String NUM_MAPS_STRING = Integer.toString(NUM_MAPS_TO_CREATE);
  private static final int SPLITTABLE_DATA_SIZE = NUM_MAPS_TO_CREATE * TEST_DATA_LIMIT - DATA_ALLOWANCE;
  private static final String UNIT_DATA = "123456789";
  private static final String LARGE_DATA_VALUE = UNIT_DATA.repeat(SPLITTABLE_DATA_SIZE / UNIT_DATA.length());
  private static final String LARGE_DATA_KEY = "domainzip.encoded";
  private static final String TOPOLOGY_VALUE = "domainValid: true\ndomain:\n  name: sample";
  private static final String DOMAIN_HASH_VALUE = "MII_domain_hash";
  private static final String INPUTS_HASH_VALUE = "MII_inputs_hash";
  private static final String MD5_SECRETS = "md5-secrets";
  private static final String RESTART_VERSION = "123";
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final TerminalStep terminalStep = new TerminalStep();
  private final IntrospectResult introspectResult = new IntrospectResult();
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);

  @BeforeEach
  void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(ScanCacheStub.install());
    mementos.add(StaticStubSupport.install(ConfigMapSplitter.class, "dataLimit", TEST_DATA_LIMIT));

    testSupport.defineResources(domain);
    testSupport.addDomainPresenceInfo(info);
  }

  @AfterEach
  void tearDown() throws Exception {
    testSupport.throwOnCompletionFailure();
    mementos.forEach(Memento::revert);
  }

  class IntrospectResult {
    private final StringBuilder builder = new StringBuilder();

    IntrospectResult defineFile(String fileName, String... contents) {
      addLine(">>> /" + fileName);
      Arrays.stream(contents).forEach(this::addLine);
      addLine(">>> EOF");
      return this;
    }

    private void addLine(String line) {
      builder.append(line).append(System.lineSeparator());
    }

    void addToPacket() {
      testSupport.addToPacket(ProcessingConstants.DOMAIN_INTROSPECTOR_LOG_RESULT, builder.toString());
    }

  }

  @Test
  void whenNoTopologySpecified_continueProcessing() {
    testSupport.defineResources(
          createIntrospectorConfigMap(0, Map.of(TOPOLOGY_YAML, TOPOLOGY_VALUE, SECRETS_MD_5, MD5_SECRETS)));
    introspectResult.defineFile(SECRETS_MD_5, "not telling").addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenNoTopologySpecified_dontUpdateConfigMap() {
    testSupport.defineResources(
          createIntrospectorConfigMap(0, Map.of(TOPOLOGY_YAML, TOPOLOGY_VALUE, SECRETS_MD_5, MD5_SECRETS)));
    introspectResult.defineFile(SECRETS_MD_5, "not telling").addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(getIntrospectorConfigMapValue(SECRETS_MD_5), equalTo(MD5_SECRETS));
  }

  @Test
  void whenNoTopologySpecified_addIntrospectionVersionLabel() {
    forDomain(domain).withIntrospectVersion("4");
    testSupport.defineResources(
          createIntrospectorConfigMap(0, Map.of(TOPOLOGY_YAML, TOPOLOGY_VALUE, SECRETS_MD_5, MD5_SECRETS)));
    introspectResult.defineFile(SECRETS_MD_5, "not telling").addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(getIntrospectionVersion(), equalTo(domain.getIntrospectVersion()));
  }

  @SuppressWarnings("SameParameterValue")
  private String getIntrospectorConfigMapValue(String key) {
    return getIntrospectionConfigMap()
          .map(V1ConfigMap::getData)
          .map(m -> m.get(key))
          .orElse(null);
  }

  private String getIntrospectionVersion() {
    return getIntrospectionConfigMap()
          .map(V1ConfigMap::getMetadata)
          .map(V1ObjectMeta::getLabels)
          .map(m -> m.get(INTROSPECTION_STATE_LABEL))
          .orElse(null);
  }

  @Test
  void whenTopologyNotValid_reportInDomainStatus() {
    introspectResult.defineFile(TOPOLOGY_YAML,
          "domainValid: false", "validationErrors: [first problem, second problem]").addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(getDomain(), hasCondition(FAILED).withReason(DOMAIN_INVALID)
          .withMessageContaining(perLine("first problem", "second problem")));
  }

  @Test
  void whenTopologyNotValid_generateFailedEvent() {
    introspectResult.defineFile(TOPOLOGY_YAML,
        "domainValid: false", "validationErrors: [first problem, second problem]").addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(
        "Expected Event " + DOMAIN_FAILED + " expected with message not found",
        EventTestUtils.getExpectedEventMessage(testSupport, DOMAIN_FAILED),
        stringContainsInOrder("Domain", UID, "failed due to",
            getLocalizedString(DOMAIN_INVALID_EVENT_ERROR)));
  }

  @Nonnull
  private String perLine(String... errors) {
    return String.join(lineSeparator(), errors);
  }

  @Nonnull
  private DomainResource getDomain() {
    return testSupport.<DomainResource>getResources(KubernetesTestSupport.DOMAIN)
          .stream()
          .findFirst()
          .orElse(new DomainResource());
  }

  @Test
  void whenTopologyNotValid_abortProcessing() {
    introspectResult.defineFile(TOPOLOGY_YAML, "domainValid: false", "validationErrors: []").addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  void whenTopologyPresent_continueProcessing() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"").addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenTopologyPresent_addToPacket() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"").addToPacket();

    Packet packet = testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(packet.get(DOMAIN_TOPOLOGY), instanceOf(WlsDomainConfig.class));
  }

  @Test
  void whenTopologyAndDomainZipHashPresent_addToPacket() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .defineFile(DOMAINZIP_HASH, DOMAIN_HASH_VALUE)
          .addToPacket();

    Packet packet = testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(packet.get(DOMAINZIP_HASH), equalTo(DOMAIN_HASH_VALUE));
  }

  @Test
  void whenTopologyAndDomainZipHashPresent_addToConfigMap() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .defineFile(DOMAINZIP_HASH, DOMAIN_HASH_VALUE)
          .addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(getIntrospectorConfigMapData(), hasEntry(DOMAINZIP_HASH, DOMAIN_HASH_VALUE));
  }

  private Map<String, String> getIntrospectorConfigMapData() {
    return getIntrospectionConfigMap()
          .map(V1ConfigMap::getData)
          .orElseGet(Collections::emptyMap);
  }

  @Nonnull
  private Optional<V1ConfigMap> getIntrospectionConfigMap() {
    return testSupport.<V1ConfigMap>getResources(KubernetesTestSupport.CONFIG_MAP)
          .stream()
          .filter(this::isOperatorResource)
          .filter(IntrospectorConfigMapTest::isIntrospectorConfigMap)
          .findFirst();
  }

  private boolean isOperatorResource(V1ConfigMap configMap) {
    return Optional.ofNullable(configMap.getMetadata())
          .map(V1ObjectMeta::getLabels)
          .map(this::hasCreatedByOperatorLabel)
          .orElse(false);
  }

  private boolean hasCreatedByOperatorLabel(Map<String, String> m) {
    return "true".equals(m.get(CREATEDBYOPERATOR_LABEL));
  }

  private static boolean isIntrospectorConfigMap(V1ConfigMap configMap) {
    return getConfigMapName(configMap).startsWith(getIntrospectorConfigMapNamePrefix(UID));
  }

  private static String getConfigMapName(V1ConfigMap configMap) {
    return Optional.ofNullable(configMap.getMetadata()).map(V1ObjectMeta::getName).orElse("");
  }

  @Test
  void whenTopologyAndMIISecretsHashPresent_addToPacket() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .defineFile(SECRETS_MD_5, MD5_SECRETS)
          .addToPacket();

    Packet packet = testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(packet.get(SECRETS_MD_5), equalTo(MD5_SECRETS));
  }

  @Test
  void whenTopologyAndMIISecretsHashPresent_addToConfigMap() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .defineFile(SECRETS_MD_5, MD5_SECRETS)
          .addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(getIntrospectorConfigMapData(), hasEntry(SECRETS_MD_5, MD5_SECRETS));
  }

  @Test
  void whenDataTooLargeForSingleConfigMap_recordCountInMap() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .defineFile(LARGE_DATA_KEY, LARGE_DATA_VALUE)
          .addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(getIntrospectorConfigMapData(), hasEntry(NUM_CONFIG_MAPS, NUM_MAPS_STRING));
  }

  @Test
  void whenDataTooLargeForSingleConfigMap_recordCountInPacket() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .defineFile(LARGE_DATA_KEY, LARGE_DATA_VALUE)
          .addToPacket();

    final Packet packet = testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(packet.getValue(NUM_CONFIG_MAPS), equalTo(NUM_MAPS_STRING));
  }

  @Test
  void whenDataTooLargeForSingleConfigMap_createMultipleMaps() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .defineFile(LARGE_DATA_KEY, LARGE_DATA_VALUE)
          .addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(getIntrospectionConfigMaps(), hasSize(NUM_MAPS_TO_CREATE));
  }

  @Test
  void whenDomainHasRestartVersion_addToPacket() {
    configureDomain().withRestartVersion(RESTART_VERSION);
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .addToPacket();

    Packet packet = testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(packet.get(IntrospectorConfigMapConstants.DOMAIN_RESTART_VERSION), equalTo(RESTART_VERSION));
  }

  private DomainConfigurator configureDomain() {
    return forDomain(domain);
  }

  @Test
  void whenDomainIsModelInImage_addImageSpecHashToPacket() {
    configureDomain().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .addToPacket();

    Packet packet = testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(packet.get(DOMAIN_INPUTS_HASH), notNullValue());
  }

  @Test
  void whenDomainIsModelInImage_dontAddRangesForZipsThatFitInMainConfigMap() {
    configureDomain().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .defineFile("domainzip.secure", "abcdefg")
          .defineFile("primordial_domainzip.secure", "hijklmno")
          .addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(getIntrospectorConfigMapValue("domainzip.secure.range"), nullValue());
    assertThat(getIntrospectorConfigMapValue("primordial_domainzip.secure.range"), nullValue());
  }

  private V1ConfigMap createIntrospectorConfigMap(int mapIndex, Map<String, String> entries) {
    return new V1ConfigMap()
          .metadata(createOperatorMetadata().name(getIntrospectorConfigMapName(mapIndex)).namespace(NS))
          .data(new HashMap<>(entries));
  }

  @Nonnull
  private V1ObjectMeta createOperatorMetadata() {
    return new V1ObjectMeta().putLabelsItem(CREATEDBYOPERATOR_LABEL, "true");
  }

  private static String getIntrospectorConfigMapName(int mapIndex) {
    return IntrospectorConfigMapConstants.getIntrospectorConfigMapName(UID, mapIndex);
  }

  @Test
  void loadExistingEntriesFromIntrospectorConfigMap() {
    testSupport.defineResources(createIntrospectorConfigMap(0, Map.of(
          TOPOLOGY_YAML, TOPOLOGY_VALUE,
          SECRETS_MD_5, MD5_SECRETS,
          DOMAINZIP_HASH, DOMAIN_HASH_VALUE,
          DOMAIN_RESTART_VERSION, RESTART_VERSION,
          DOMAIN_INPUTS_HASH, INPUTS_HASH_VALUE,
          NUM_CONFIG_MAPS, NUM_MAPS_STRING)));

    Packet packet = testSupport.runSteps(ConfigMapHelper.readExistingIntrospectorConfigMap());

    assertThat(packet.get(SECRETS_MD_5), equalTo(MD5_SECRETS));
    assertThat(packet.get(DOMAINZIP_HASH), equalTo(DOMAIN_HASH_VALUE));
    assertThat(packet.get(DOMAIN_RESTART_VERSION), equalTo(RESTART_VERSION));
    assertThat(packet.get(DOMAIN_INPUTS_HASH), equalTo(INPUTS_HASH_VALUE));
    assertThat(packet.get(DOMAIN_TOPOLOGY), equalTo(getParsedDomain(TOPOLOGY_VALUE)));
    assertThat(packet.get(NUM_CONFIG_MAPS), equalTo(NUM_MAPS_STRING));
  }

  @SuppressWarnings("SameParameterValue")
  private WlsDomainConfig getParsedDomain(String topologyYaml) {
    return Optional.ofNullable(topologyYaml)
          .map(DomainTopology::parseDomainTopologyYaml)
          .map(DomainTopology::getDomain)
          .orElse(null);
  }

  @Test
  void whenSitConfigEntriesMissingFromIntrospectionResult_removeFromConfigMap() {
    testSupport.defineResources(createIntrospectorConfigMap(0, Map.of(
          TOPOLOGY_YAML, TOPOLOGY_VALUE,
          "Sit-Cfg-1", "value1",
          "Sit-Cfg-2", "value2")));
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(getIntrospectorConfigMapData(), allOf(not(hasKey("Sit-Cfg-1")), not(hasKey("Sit-Cfg-2"))));
  }

  @Test
  void whenNoTopologySpecified_dontRemoveSitConfigEntries() {
    testSupport.defineResources(
          createIntrospectorConfigMap(0, Map.of(TOPOLOGY_YAML, TOPOLOGY_VALUE, "Sit-Cfg-1", "value1")));
    introspectResult.defineFile(SECRETS_MD_5, "not telling").addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(getIntrospectorConfigMapValue("Sit-Cfg-1"), equalTo("value1"));
  }

  @Test
  void whenRequested_deleteAllIntrospectorConfigMaps() {
    testSupport.defineResources(
          createIntrospectorConfigMap(0, Map.of()),
          createIntrospectorConfigMap(1, Map.of()),
          createIntrospectorConfigMap(2, Map.of())
    );

    testSupport.runSteps(ConfigMapHelper.deleteIntrospectorConfigMapStep(UID, NS, null));

    assertThat(getIntrospectionConfigMaps(), empty());
  }

  @Nonnull
  private List<V1ConfigMap> getIntrospectionConfigMaps() {
    return testSupport.<V1ConfigMap>getResources(KubernetesTestSupport.CONFIG_MAP)
          .stream()
          .filter(this::isOperatorResource)
          .filter(IntrospectorConfigMapTest::isIntrospectorConfigMap)
          .toList();
  }

}
