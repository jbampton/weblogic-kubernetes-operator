// Copyright (c) 2017, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
//
def kind_k8s_map = [
    '0.20.0': [
        '1.28.0':  'kindest/node:v1.28.0@sha256:b7a4cad12c197af3ba43202d3efe03246b3f0793f162afb40a33c923952d5b31',
        '1.28':    'kindest/node:v1.28.0@sha256:b7a4cad12c197af3ba43202d3efe03246b3f0793f162afb40a33c923952d5b31',
        '1.27.3':  'kindest/node:v1.27.3@sha256:3966ac761ae0136263ffdb6cfd4db23ef8a83cba8a463690e98317add2c9ba72',
        '1.27':    'kindest/node:v1.27.3@sha256:3966ac761ae0136263ffdb6cfd4db23ef8a83cba8a463690e98317add2c9ba72',
        '1.26.6':  'kindest/node:v1.26.6@sha256:6e2d8b28a5b601defe327b98bd1c2d1930b49e5d8c512e1895099e4504007adb',
        '1.26':    'kindest/node:v1.26.6@sha256:6e2d8b28a5b601defe327b98bd1c2d1930b49e5d8c512e1895099e4504007adb',
        '1.25.11': 'kindest/node:v1.25.11@sha256:227fa11ce74ea76a0474eeefb84cb75d8dad1b08638371ecf0e86259b35be0c8',
        '1.25':    'kindest/node:v1.25.11@sha256:227fa11ce74ea76a0474eeefb84cb75d8dad1b08638371ecf0e86259b35be0c8',
        '1.24.15': 'kindest/node:v1.24.15@sha256:7db4f8bea3e14b82d12e044e25e34bd53754b7f2b0e9d56df21774e6f66a70ab',
        '1.24':    'kindest/node:v1.24.15@sha256:7db4f8bea3e14b82d12e044e25e34bd53754b7f2b0e9d56df21774e6f66a70ab',
        '1.23.17': 'kindest/node:v1.23.17@sha256:59c989ff8a517a93127d4a536e7014d28e235fb3529d9fba91b3951d461edfdb',
        '1.23':    'kindest/node:v1.23.17@sha256:59c989ff8a517a93127d4a536e7014d28e235fb3529d9fba91b3951d461edfdb',
        '1.22.17': 'kindest/node:v1.22.17@sha256:f5b2e5698c6c9d6d0adc419c0deae21a425c07d81bbf3b6a6834042f25d4fba2',
        '1.22':    'kindest/node:v1.22.17@sha256:f5b2e5698c6c9d6d0adc419c0deae21a425c07d81bbf3b6a6834042f25d4fba2',
        '1.21.14': 'kindest/node:v1.21.14@sha256:8a4e9bb3f415d2bb81629ce33ef9c76ba514c14d707f9797a01e3216376ba093',
        '1.21':    'kindest/node:v1.21.14@sha256:8a4e9bb3f415d2bb81629ce33ef9c76ba514c14d707f9797a01e3216376ba093'
    ]
]
def _kind_image = null

pipeline {
    agent { label 'large-ol9' }
    options {
        timeout(time: 800, unit: 'MINUTES')
    }

    tools {
        maven 'maven-3.8.7'
        jdk 'jdk17'
    }

    environment {
        ocir_host = "${env.WKT_OCIR_HOST}"
        wko_tenancy = "${env.WKT_TENANCY}"
        ocir_creds = 'wkt-ocir-creds'

        outdir = "${WORKSPACE}/staging"
        result_root = "${outdir}/wl_k8s_test_results"
        pv_root = "${outdir}/k8s-pvroot"
        kubeconfig_file = "${result_root}/kubeconfig"

        kind_name = "kind"
        kind_network = "kind"
        registry_name = "kind-registry"
        registry_host = "${registry_name}"
        registry_port = "5000"

        start_time = sh(script: 'date +"%Y-%m-%d %H:%M:%S"', returnStdout: true).trim()
        wle_download_url="https://github.com/oracle/weblogic-logging-exporter/releases/latest"
    }

    parameters {
        choice(name: 'MAVEN_PROFILE_NAME',
                description: 'Profile to use in mvn command to run the tests. Possible values are wls-srg (the default), integration-tests, toolkits-srg, kind-sequential and kind-upgrade. Refer to weblogic-kubernetes-operator/integration-tests/pom.xml on the branch.',
                choices: [
                        'wls-srg',
                        'integration-tests',
                        'kind-sequential',
                        'kind-upgrade',
                        'toolkits-srg'
                ]
        )
        string(name: 'IT_TEST',
               description: 'Comma separated list of individual It test classes to be run e.g., ItParameterizedDomain, ItMiiUpdateDomainConfig, ItMiiDynamicUpdate*, ItMiiMultiMode',
               defaultValue: ''
        )
        choice(name: 'KIND_VERSION',
               description: 'Kind version.',
               choices: [
                   '0.20.0'
               ]
        )
        choice(name: 'KUBE_VERSION',
               description: 'Kubernetes version. Supported values depend on the Kind version. Kind 0.20.0: 1.28, 1.28.0, 1.27, 1.27.3, 1.26, 1.26.6, 1.25, 1.25.11, 1.24, 1.24.15, 1.23, 1.23.17, 1.22, 1.22.17, 1.21, 1.21.14 ',
               choices: [
                    // The first item in the list is the default value...
                    '1.26.6',
                    '1.28.0',
                    '1.28',
                    '1.27.3',
                    '1.27',
                    '1.26',
                    '1.25.11',
                    '1.25',
                    '1.24.15',
                    '1.24',
                    '1.23.17',
                    '1.23',
                    '1.22.17',
                    '1.22',
                    '1.21.14',
                    '1.21'
               ]
        )
        string(name: 'HELM_VERSION',
               description: 'Helm version',
               defaultValue: '3.11.2'
        )
        choice(name: 'ISTIO_VERSION',
               description: 'Istio version',
               choices: [
                   '1.17.2',
                   '1.16.1',
                   '1.13.2',
                   '1.12.6',
                   '1.11.1',
                   '1.10.4',
                   '1.9.9'
               ]
        )
        booleanParam(name: 'PARALLEL_RUN',
                     description: 'Runs tests in parallel. Default is true, test classes run in parallel.',
                     defaultValue: true
        )
        string(name: 'NUMBER_OF_THREADS',
               description: 'Number of threads to run the classes in parallel, default is 3.',
               defaultValue: "3"
        )
        string(name: 'WDT_DOWNLOAD_URL',
               description: 'URL to download WDT.',
               defaultValue: 'https://github.com/oracle/weblogic-deploy-tooling/releases/latest'
        )
        string(name: 'WIT_DOWNLOAD_URL',
               description: 'URL to download WIT.',
               defaultValue: 'https://github.com/oracle/weblogic-image-tool/releases/latest'
        )
        string(name: 'TEST_IMAGES_REPO',
               description: '',
               defaultValue: "${env.WKT_OCIR_HOST}"
        )
        choice(name: 'BASE_IMAGES_REPO',
               choices: ["${env.WKT_OCIR_HOST}", 'container-registry.oracle.com'],
               description: 'Repository to pull the base images. Make sure to modify the image names if you are modifying this parameter value.'
        )
        string(name: 'WEBLOGIC_IMAGE_NAME',
               description: 'WebLogic base image name. Default is the image name in BASE_IMAGES_REPO. Use middleware/weblogic for OCR.',
               defaultValue: "test-images/weblogic"
        )
        string(name: 'WEBLOGIC_IMAGE_TAG',
               description: '12.2.1.4,  12.2.1.4-dev(12.2.1.4-dev-ol7) , 12.2.1.4-slim(12.2.1.4-slim-ol7), 12.2.1.4-ol8, 12.2.1.4-dev-ol8, 12.2.1.4-slim-ol8, 14.1.1.0-11-ol7, 14.1.1.0-dev-11-ol7, 14.1.1.0-slim-11-ol7, 14.1.1.0-8-ol7, 14.1.1.0-dev-8-ol7, 14.1.1.0-slim-8-ol7, 14.1.1.0-11-ol8, 14.1.1.0-dev-11-ol8, 14.1.1.0-slim-11-ol8, 14.1.1.0-8-ol8, 14.1.1.0-dev-8-ol8, 14.1.1.0-slim-8-ol8',
               defaultValue: '12.2.1.4'
        )
        string(name: 'FMWINFRA_IMAGE_NAME',
               description: 'FWM Infra image name. Default is the image name in BASE_IMAGES_REPO. Use middleware/fmw-infrastructure for OCR.',
               defaultValue: "test-images/fmw-infrastructure"
        )
        string(name: 'FMWINFRA_IMAGE_TAG',
               description: 'FWM Infra image tag',
               defaultValue: '12.2.1.4'
        )
        string(name: 'DB_IMAGE_NAME',
               description: 'Oracle DB image name. Default is the image name in BASE_IMAGES_REPO, use database/enterprise for OCR.',
               defaultValue: "test-images/database/enterprise"
        )
        string(name: 'DB_IMAGE_TAG',
               description: 'Oracle DB image tag',
               defaultValue: '12.2.0.1-slim'
        )
        string(name: 'MONITORING_EXPORTER_BRANCH',
               description: '',
               defaultValue: 'main'
        )
        string(name: 'MONITORING_EXPORTER_WEBAPP_VERSION',
               description: '',
               defaultValue: '2.0.7'
        )
        string(name: 'PROMETHEUS_CHART_VERSION',
               description: '',
               defaultValue: '15.2.0'
        )
        string(name: 'GRAFANA_CHART_VERSION',
               description: '',
               defaultValue: '6.38.6'
        )
        booleanParam(name: 'COLLECT_LOGS_ON_SUCCESS',
                     description: 'Collect logs for successful runs. Default is false.',
                     defaultValue: false
        )
    }

    stages {
        stage('Filter unwanted branches') {
            when {
                anyOf {
                    changeRequest()
                    branch 'main'
                    branch 'release/4.0'
                    branch 'release/3.4'
                }
            }
            stages {
                stage('Workaround JENKINS-41929 Parameters bug') {
                    steps {
                        echo 'Initialize parameters as environment variables due to https://issues.jenkins-ci.org/browse/JENKINS-41929'
                        evaluate """${def script = ""; params.each { k, v -> script += "env.${k} = '''${v}'''\n" }; return script}"""
                    }
                }
                stage ('Echo environment') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            env|sort
                            java -version
                            mvn --version
                            python --version
                            podman version
                            ulimit -a
                            ulimit -aH
                        '''
                        script {
                            def knd = params.KIND_VERSION
                            def k8s = params.KUBE_VERSION
                            if (knd != null && k8s != null) {
                                def k8s_map = kind_k8s_map.get(knd)
                                if (k8s_map != null) {
                                    _kind_image = k8s_map.get(k8s)
                                }
                                if (_kind_image == null) {
                                    currentBuild.result = 'ABORTED'
                                    error('Unable to compute _kind_image for Kind version ' +
                                            knd + ' and Kubernetes version ' + k8s)
                                }
                            } else {
                                currentBuild.result = 'ABORTED'
                                error('KIND_VERSION or KUBE_VERSION were null')
                            }
                            echo "Kind Image = ${_kind_image}"
                        }
                    }
                }

                stage('Build WebLogic Kubernetes Operator') {
                    steps {
                        withMaven(globalMavenSettingsConfig: 'wkt-maven-settings-xml', publisherStrategy: 'EXPLICIT') {
                            sh "mvn -DtrimStackTrace=false clean install"
                        }
                    }
                }

                stage('Make Workspace bin directory') {
                    steps {
                        sh "mkdir -m777 -p ${WORKSPACE}/bin"
                    }
                }

                stage('Install Helm') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            oci os object get --namespace=${wko_tenancy} --bucket-name=wko-system-test-files \
                                --name=helm/helm-v${HELM_VERSION}.tar.gz --file=helm.tar.gz \
                                --auth=instance_principal
                            tar zxf helm.tar.gz
                            mv linux-amd64/helm ${WORKSPACE}/bin/helm
                            rm -rf linux-amd64
                            helm version
                        '''
                    }
                }

                stage('Run Helm installation tests') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        withMaven(globalMavenSettingsConfig: 'wkt-maven-settings-xml', publisherStrategy: 'EXPLICIT') {
                            sh 'export PATH=${runtime_path} && mvn -pl kubernetes -P helm-installation-test verify'
                        }
                    }
                }

                stage ('Install kubectl') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                        KUBE_VERSION = "${params.KUBE_VERSION}"
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            oci os object get --namespace=${wko_tenancy} --bucket-name=wko-system-test-files \
                                --name=kubectl/kubectl-v${KUBE_VERSION} --file=${WORKSPACE}/bin/kubectl \
                                --auth=instance_principal
                            chmod +x ${WORKSPACE}/bin/kubectl
                            kubectl version --client=true
                        '''
                    }
                }

                stage('Install kind') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            oci os object get --namespace=${wko_tenancy} --bucket-name=wko-system-test-files \
                                --name=kind/kind-v${KIND_VERSION} --file=${WORKSPACE}/bin/kind \
                                --auth=instance_principal
                            chmod +x "${WORKSPACE}/bin/kind"
                            kind version
                        '''
                    }
                }

                stage('Preparing Integration Test Environment') {
                    steps {
                        sh 'mkdir -m777 -p ${result_root}'
                        echo "Results will be in ${result_root}"
                        sh 'mkdir -m777 -p ${pv_root}'
                        echo "Persistent volume files, if any, will be in ${pv_root}"
                    }
                }

                stage('Start registry container') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}

                            running="$(podman container inspect -f '{{.State.Running}}' "${registry_name}" 2>/dev/null || true)"
                            if [ "${running}" = 'true' ]; then
                              echo "Stopping the registry container ${registry_name}"
                              podman stop "${registry_name}"
                              podman rm --force "${registry_name}"
                            fi
        
                            podman run -d --restart=always -p "127.0.0.1:${registry_port}:5000" --name "${registry_name}" \
                                ${ocir_host}/${wko_tenancy}/test-images/docker/registry:2.8.2
                            echo "Registry Host: ${registry_host}"
                        '''
                    }
                }

                stage('Create kind cluster') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                        kind_image = sh(script: "echo -n ${_kind_image}", returnStdout: true).trim()
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            export KIND_EXPERIMENTAL_PROVIDER=podman

                            podman version
                            cat /etc/systemd/system/user@.service.d/delegate.conf
                            cat /etc/modules-load.d/iptables.conf
                            lsmod|grep -E "^ip_tables|^iptable_filter|^iptable_nat|^ip6"

                            if kind delete cluster --name ${kind_name} --kubeconfig "${kubeconfig_file}"; then
                                echo "Deleted orphaned kind cluster ${kind_name}"
                            fi
                            cat <<EOF | kind create cluster --name "${kind_name}" --kubeconfig "${kubeconfig_file}" --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${registry_port}"]
    endpoint = ["http://${registry_host}:${registry_port}"]
nodes:
  - role: control-plane
    image: ${kind_image}
  - role: worker
    image: ${kind_image}
    extraMounts:
      - hostPath: ${pv_root}
        containerPath: ${pv_root}
EOF

                            export KUBECONFIG=${kubeconfig_file}
                            kubectl cluster-info --context "kind-${kind_name}"

                            for node in $(kind get nodes --name "${kind_name}"); do
                                kubectl annotate node ${node} tilt.dev/registry=localhost:${registry_port};
                            done

                            # Document the local registry
                            # https://github.com/kubernetes/enhancements/tree/master/keps/sig-cluster-lifecycle/generic/1755-communicating-a-local-registry
                            cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:${registry_port}"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF
                        '''
                    }
                }

                stage('Run integration tests') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        script {
                            def res = 0
                            res = sh(script: '''
                                    if [ -z "${IT_TEST}" ] && [ "${MAVEN_PROFILE_NAME}" = "integration-tests" ]; then
                                       echo 'ERROR: All tests cannot be run with integration-tests profile'
                                       exit 1
                                    fi
                                ''', returnStatus: true)
                            if (res != 0 ) {
                                currentBuild.result = 'ABORTED'
                                error('Profile/ItTests Validation Failed')
                            }
                        }

                        sh '''
                            export PATH=${runtime_path}
                            export KUBECONFIG=${kubeconfig_file}
                            mkdir -m777 -p "${WORKSPACE}/.mvn"
                            touch ${WORKSPACE}/.mvn/maven.config
                            K8S_NODEPORT_HOST=$(kubectl get node kind-worker -o jsonpath='{.status.addresses[?(@.type == "InternalIP")].address}')
                            if [ "${MAVEN_PROFILE_NAME}" == "kind-sequential" ]; then
                                PARALLEL_RUN='false'
                            elif [ "${MAVEN_PROFILE_NAME}" == "kind-upgrade" ]; then
                                PARALLEL_RUN='false'
                            elif [ -n "${IT_TEST}" ]; then
                                echo 'Overriding MAVEN_PROFILE_NAME to integration-test when running individual test(s)'
                                MAVEN_PROFILE_NAME="integration-tests"
                                echo "-Dit.test=\"${IT_TEST}\"" >> ${WORKSPACE}/.mvn/maven.config
                            fi
                            echo "-Dwko.it.wle.download.url=\"${wle_download_url}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.result.root=\"${result_root}\""                                               >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.pv.root=\"${pv_root}\""                                                       >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.k8s.nodeport.host=\"${K8S_NODEPORT_HOST}\""                                   >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.kind.repo=\"localhost:${registry_port}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.istio.version=\"${ISTIO_VERSION}\""                                           >> ${WORKSPACE}/.mvn/maven.config
                            echo "-DPARALLEL_CLASSES=\"${PARALLEL_RUN}\""                                                >> ${WORKSPACE}/.mvn/maven.config
                            echo "-DNUMBER_OF_THREADS=\"${NUMBER_OF_THREADS}\""                                          >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.wdt.download.url=\"${WDT_DOWNLOAD_URL}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.wit.download.url=\"${WIT_DOWNLOAD_URL}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.base.images.repo=\"${BASE_IMAGES_REPO}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.base.images.tenancy=\"${wko_tenancy}\""                                       >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.test.images.repo=\"${TEST_IMAGES_REPO}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.test.images.tenancy=\"${wko_tenancy}\""                                       >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.weblogic.image.name=\"${WEBLOGIC_IMAGE_NAME}\""                               >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.weblogic.image.tag=\"${WEBLOGIC_IMAGE_TAG}\""                                 >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.fmwinfra.image.name=\"${FMWINFRA_IMAGE_NAME}\""                               >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.fmwinfra.image.tag=\"${FMWINFRA_IMAGE_TAG}\""                                 >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.db.image.name=\"${DB_IMAGE_NAME}\""                                           >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.db.image.tag=\"${DB_IMAGE_TAG}\""                                             >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.monitoring.exporter.branch=\"${MONITORING_EXPORTER_BRANCH}\""                 >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.monitoring.exporter.webapp.version=\"${MONITORING_EXPORTER_WEBAPP_VERSION}\"" >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.prometheus.chart.version=\"${PROMETHEUS_CHART_VERSION}\""                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.grafana.chart.version=\"${GRAFANA_CHART_VERSION}\""                           >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.collect.logs.on.success=\"${COLLECT_LOGS_ON_SUCCESS}\""                       >> ${WORKSPACE}/.mvn/maven.config

                            echo "${WORKSPACE}/.mvn/maven.config contents:"
                            cat "${WORKSPACE}/.mvn/maven.config"
                            cp "${WORKSPACE}/.mvn/maven.config" "${result_root}"
                        '''
                        withMaven(globalMavenSettingsConfig: 'wkt-maven-settings-xml', publisherStrategy: 'EXPLICIT') {
                            withCredentials([
                                usernamePassword(credentialsId: "${ocir_creds}", usernameVariable: 'OCIR_USER', passwordVariable: 'OCIR_PASS')
                            ]) {
                                sh '''
                                    export PATH=${runtime_path}
                                    export KUBECONFIG=${kubeconfig_file}
                                    export BASE_IMAGES_REPO_USERNAME="${OCIR_USER}"
                                    export BASE_IMAGES_REPO_PASSWORD="${OCIR_PASS}"
                                    export BASE_IMAGES_REPO_EMAIL="noreply@oracle.com"
                                    export TEST_IMAGES_REPO_USERNAME="${OCIR_USER}"
                                    export TEST_IMAGES_REPO_PASSWORD="${OCIR_PASS}"
                                    export TEST_IMAGES_REPO_EMAIL="noreply@oracle.com"
                                    if ! time mvn -pl integration-tests -P ${MAVEN_PROFILE_NAME} verify 2>&1 | tee "${result_root}/kindtest.log"; then
                                        echo "integration-tests failed"
                                        exit 1
                                    fi
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            sh '''
                                export PATH="${WORKSPACE}/bin:${PATH}"
                                export KUBECONFIG=${kubeconfig_file}
                                mkdir -m777 -p ${result_root}/kubelogs
                                if ! kind export logs "${result_root}/kubelogs" --name "${kind_name}" --verbosity 99; then
                                    echo "Failed to export kind logs for kind cluster ${kind_name}"
                                fi
                                if ! podman exec kind-worker journalctl --utc --dmesg --system > "${result_root}/journalctl-kind-worker.out"; then
                                    echo "Failed to run journalctl for kind worker"
                                fi
                                if ! podman exec kind-control-plane journalctl --utc --dmesg --system > "${result_root}/journalctl-kind-control-plane.out"; then
                                    echo "Failed to run journalctl for kind control plane"
                                fi
                                if ! journalctl --utc --dmesg --system --since "$start_time" > "${result_root}/journalctl-compute.out"; then
                                    echo "Failed to run journalctl for compute node"
                                fi

                                mkdir -m777 -p "${WORKSPACE}/logdir/${BUILD_TAG}/wl_k8s_test_results"
                                sudo mv -f ${result_root}/* "${WORKSPACE}/logdir/${BUILD_TAG}/wl_k8s_test_results"
                            '''
                            archiveArtifacts(artifacts:
                            "logdir/${BUILD_TAG}/wl_k8s_test_results/diagnostics/**/*,logdir/${BUILD_TAG}/wl_k8s_test_results/workdir/liftandshiftworkdir/**/*")
                            junit(testResults: 'integration-tests/target/failsafe-reports/*.xml', allowEmptyResults: true)
                        }
                    }
                }
            }
            post {
                always {
                    sh '''
                        export PATH="${WORKSPACE}/bin:${PATH}"
                        running="$(podman container inspect -f '{{.State.Running}}' "${registry_name}" 2>/dev/null || true)"
                        if [ "${running}" = 'true' ]; then
                            echo "Stopping the registry container ${registry_name}"
                            podman stop "${registry_name}"
                            podman rm --force "${registry_name}"
                        fi
                        echo 'Remove old Kind cluster (if any)...'
                        if ! kind delete cluster --name ${kind_name} --kubeconfig "${kubeconfig_file}"; then
                            echo "Failed to delete kind cluster ${kind_name}"
                        fi
                    '''
                }
            }
        }
        stage ('Sync') {
            when {
                anyOf {
                    branch 'main'
                    branch 'release/4.0'
                    branch 'release/3.4'
                }
                anyOf {
                    not { triggeredBy 'TimerTrigger' }
                    tag 'v*'
                }
            }
            steps {
                build job: "wkt-sync", parameters: [ string(name: 'REPOSITORY', value: 'weblogic-kubernetes-operator') ]
            }
        }
    }
}
