// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ConfigMapKeySelector;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.FluentbitSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.CM_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.CM_EXISTS;
import static oracle.kubernetes.common.logging.MessageKeys.CM_REPLACED;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class FluentbitSpecificationTest {

    private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
    private final List<Memento> mementos = new ArrayList<>();
    private final List<LogRecord> logRecords = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        mementos.add(
                TestUtils.silenceOperatorLogger()
                        .collectLogMessages(logRecords, CM_CREATED, CM_EXISTS, CM_REPLACED)
                        .withLogLevel(Level.FINE));
        mementos.add(testSupport.install());
    }

    @AfterEach
    public void tearDown() throws Exception {
        mementos.forEach(Memento::revert);

        testSupport.throwOnCompletionFailure();
    }


    @Test
    void testDefaultImage() {
        FluentbitSpecification spec = new FluentbitSpecification();
        assertThat(spec.getImage(), equalTo(KubernetesConstants.DEFAULT_FLUENTD_IMAGE));
    }

    @Test
    void testDefaultImagePullPolicy() {
        FluentbitSpecification spec = new FluentbitSpecification();
        assertThat(spec.getImagePullPolicy(), equalTo("IfNotPresent"));
    }

    @Test
    void testUserProvidedConfig() {
        FluentbitSpecification spec = new FluentbitSpecification();
        spec.setFluentbitConfiguration("[OUTPUT]");
        spec.setParserConfiguration("[PARSER]");
        assertThat(spec.getFluentbitConfiguration(), equalTo("[OUTPUT]"));
        assertThat(spec.getParserConfiguration(), equalTo("[PARSER]"));
    }

    @Test
    void testEqual() {
        FluentbitSpecification spec1 = new FluentbitSpecification();
        FluentbitSpecification spec2 = new FluentbitSpecification();
        spec1.setImage("myimage:v1");
        spec2.setImage("myimage:v1");
        spec1.setElasticSearchCredentials("mycred");
        spec2.setElasticSearchCredentials("mycred");
        spec1.setWatchIntrospectorLogs(true);
        spec2.setWatchIntrospectorLogs(true);
        assertThat(spec1, equalTo(spec2));
    }

    @Test
    void testSettingOthers() {
        FluentbitSpecification spec = new FluentbitSpecification();
        spec.setWatchIntrospectorLogs(false);
        List<V1EnvVar> envList = new ArrayList<>();
        envList.add(new V1EnvVar().name("HELLO").value("WORLD"));
        envList.add(new V1EnvVar().name("test1").valueFrom(new V1EnvVarSource()
                .configMapKeyRef(new V1ConfigMapKeySelector().key("key1").name("newconfigmp"))));
        spec.setFluentbitConfiguration("[OUTPUT]");
        spec.setParserConfiguration("[PARSER]");
        spec.setEnv(envList);
        List<V1VolumeMount> volumeMounts = new ArrayList<>();
        volumeMounts.add(new V1VolumeMount().name("v1").mountPath("/xyznew"));
        volumeMounts.add(new V1VolumeMount().name("v2").mountPath("/anotherone"));
        spec.setVolumeMounts(volumeMounts);

        assertThat(spec.getEnv(), equalTo(envList));
        assertThat(spec.getVolumeMounts(), equalTo(volumeMounts));
        assertThat(spec.getWatchIntrospectorLogs(), equalTo(false));

    }


}
