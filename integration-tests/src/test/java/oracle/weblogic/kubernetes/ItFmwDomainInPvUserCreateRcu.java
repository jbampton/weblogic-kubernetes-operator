// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import oracle.weblogic.domain.DomainCreationImage;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.DbUtils.setupDBandRCUschema;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.FmwUtils.createDomainResourceSimplifyJrfPv;
import static oracle.weblogic.kubernetes.utils.FmwUtils.saveAndRestoreOpssWalletfileSecret;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyDomainReady;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResourceServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVHostPathDir;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test to create a FMW domain on PV with DomainOnPvSimplification feature when user pre-creates RCU.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test for initializeDomainOnPV when user per-creates RCU")
@IntegrationTest
@Tag("kind-sequential")
public class ItFmwDomainInPvUserCreateRcu {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String dbNamespace = null;

  private static final String RCUSCHEMAPREFIX = "jrfdomainpv";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static String ORACLEDBSUFFIX = null;
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;
  private static String DOMAINHOMEPREFIX = null;
  private static final String domainUid = "domainonpv-userrcu";
  private static final String clusterName = "cluster-1";
  private static final String adminServerName = "admin-server";
  private static final String managedServerNameBase = "managed-server";
  private static final String adminServerPodName = domainUid + "-" + adminServerName;
  private static final String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
  private static final int managedServerPort = 8001;
  private static final String miiAuxiliaryImage1Tag = "jrf1" + MII_BASIC_IMAGE_TAG;
  private final String adminSecretName = domainUid + "-weblogic-credentials";
  private final String rcuaccessSecretName = domainUid + "-rcu-credentials";
  private final String encryptionSecretName = domainUid + "-encryptionsecret";
  private final String opsswalletpassSecretName = domainUid + "-opss-wallet-password-secret";
  private final String opsswalletfileSecretName = domainUid + "-opss-wallet-file-secret";
  private static final int replicaCount = 1;

  private final String fmwModelFilePrefix = "model-fmwdomainonpv-rcu-wdt";
  private final String fmwModelFile = fmwModelFilePrefix + ".yaml";
  private String pvHostPath = null;
  private Path hostPVPath = null;
  private static String pvName = null;
  private static String pvcName = null;
  private static DomainCreationImage domainCreationImage = null;

  /**
   * Assigns unique namespaces for DB, operator and domain.
   * Start DB service and create RCU schema.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster.
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a new unique dbNamespace
    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);
    final int dbListenerPort = getNextFreePort();
    ORACLEDBSUFFIX = ".svc.cluster.local:" + dbListenerPort + "/devpdb.k8s";
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ORACLEDBSUFFIX;

    // get a new unique opNamespace
    logger.info("Assign a unique namespace for operator1");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace = namespaces.get(1);

    // get a new unique domainNamespace
    logger.info("Assign a unique namespace for FMW domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    domainNamespace = namespaces.get(2);

    pvName = getUniqueName(domainUid + "-pv-");
    pvcName = getUniqueName(domainUid + "-pvc-");

    DOMAINHOMEPREFIX = "/shared/" + domainNamespace + "/domains/";
    // start DB and create RCU schema
    logger.info("Start DB and create RCU schema for namespace: {0}, dbListenerPort: {1}, RCU prefix: {2}, "
            + "dbUrl: {3}, dbImage: {4},  fmwImage: {5} ", dbNamespace, dbListenerPort, RCUSCHEMAPREFIX, dbUrl,
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);
    assertDoesNotThrow(() -> setupDBandRCUschema(DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC,
        RCUSCHEMAPREFIX, dbNamespace, getNextFreePort(), dbUrl, dbListenerPort),
        String.format("Failed to create RCU schema for prefix %s in the namespace %s with "
            + "dbUrl %s, dbListenerPost %s", RCUSCHEMAPREFIX, dbNamespace, dbUrl, dbListenerPort));

    // install operator with DomainOnPvSimplification=true"
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    installAndVerifyOperator(opNamespace, opNamespace + "-sa", false,
        0, opHelmParams, ELASTICSEARCH_HOST, false, true, null,
        null, false, "INFO", "DomainOnPvSimplification=true", false, domainNamespace);

    // create pull secrets for domainNamespace when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);
  }

  /**
   * User creates RCU, Operate creates PV/PVC and FMW domain
   * Verify Pod is ready and service exists for both admin server and managed servers.
   * Verify EM console is accessible.
   */
  @Test
  @Order(1)
  @DisplayName("Create a FMW domain on PV when user per-creates RCU")
  void testFmwDomainOnPvUserCreatesRCU() {

    final int t3ChannelPort = getNextFreePort();
    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile();

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName, RCUSCHEMAPREFIX, RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName,
        domainNamespace,
        RCUSCHEMAPREFIX,
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + fmwModelFile);
    List<String> modelProList = new ArrayList<>();
    modelProList.add(fmwModelPropFile.toPath().toString());
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage1Tag)
            .modelFiles(modelList)
            .modelVariableFiles(modelProList);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage1Tag, witParams);
    domainCreationImage =
        new DomainCreationImage().image(MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage1Tag);

    String clusterName = "cluster-1";
    pvHostPath = getHostPath(pvName, this.getClass().getSimpleName());

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource with pvName: {0}, hostPath: {1}", pvName, pvHostPath);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid, domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        pvHostPath, rcuaccessSecretName,
        opsswalletpassSecretName, null,
        pvName, pvcName, Collections.singletonList(domainCreationImage));

    // create a domain custom resource and verify domain is created
    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready and EM console is accessible
    verifyDomainReady(domainNamespace, domainUid, replicaCount, "nosuffix");
  }

  /**
   * Export the OPSS wallet file secret of Fmw domain from the previous run
   * Use this OPSS wallet file secret to create Fmw domain on PV to connect to the same database
   * Verify Pod is ready and service exists for both admin server and managed servers.
   * Verify EM console is accessible.
   */
  @Test
  @Order(2)
  @DisplayName("Create a FMW domain on PV when user provide OPSS wallet file secret")
  void testFmwDomainOnPvUserProvideOpss() {
    saveAndRestoreOpssWalletfileSecret(domainNamespace, domainUid, opsswalletfileSecretName);
    logger.info("Deleting domain custom resource with namespace: {0}, domainUid {1}", domainNamespace, domainUid);
    deleteDomainResource(domainNamespace, domainUid);
    try {
      deleteDirectory(hostPVPath.toFile());
    } catch (IOException ioe) {
      logger.severe("Failed to cleanup the pv directory " + pvHostPath, ioe);
    }
    logger.info("Creating domain custom resource with pvName: {0}, hostPath: {1}", pvName, pvHostPath);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid, domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        pvHostPath, rcuaccessSecretName,
        opsswalletpassSecretName, opsswalletfileSecretName,
        pvName, pvcName, Collections.singletonList(domainCreationImage));
    // create a domain custom resource and verify domain is created
    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready and EM console is accessible
    verifyDomainReady(domainNamespace, domainUid, replicaCount, "nosuffix");

  }

  private File createWdtPropertyFile() {

    Properties p = new Properties();
    p.setProperty("rcuDb", dbUrl);
    p.setProperty("rcuSchemaPrefix", RCUSCHEMAPREFIX);
    p.setProperty("rcuSchemaPassword", RCUSCHEMAPASSWORD);
    p.setProperty("adminUsername", ADMIN_USERNAME_DEFAULT);
    p.setProperty("adminPassword", ADMIN_PASSWORD_DEFAULT);

    // create a model property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
        File.createTempFile(fmwModelFilePrefix, ".properties"),
        "Failed to create FMW model properties file");

    // create the property file
    assertDoesNotThrow(() ->
        p.store(new FileOutputStream(domainPropertiesFile), "FMW properties file"),
        "Failed to write FMW properties file");

    return domainPropertiesFile;
  }

  private String getHostPath(String pvName, String className) {
    hostPVPath = createPVHostPathDir(pvName, className);
    return hostPVPath.toString();
  }

  /**
   * Shutdown the domain by setting serverStartPolicy as "Never".
   */
  private void shutdownDomain() {
    patchDomainResourceServerStartPolicy("/spec/serverStartPolicy", "Never", domainNamespace, domainUid);
    logger.info("Domain is patched to stop entire WebLogic domain");

    // make sure all the server pods are removed after patch
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    logger.info("Domain shutdown success");

  }

}