#!/bin/bash
# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

prop() {
    grep "${1}" ${propsFile}| grep -v "#" | cut -d'=' -f2
}

generateTFVarFile() {
    tfVarsFile=${terraformVarDir}/terraform.tfvars
    rm -f ${tfVarsFile}
    cp ${terraformVarDir}/terraform.tfvars.template $tfVarsFile
    chmod 777 $tfVarsFile

    sed -i -e "s:@OCI_TENANCY_ID@:${tenancy_id}:g" ${tfVarsFile}
    sed -i -e "s:@OCI_COMPARTMENT_ID@:${compartment_id}:g" ${tfVarsFile}
    sed -i -e "s:@OCI_USER_ID@:${user_id}:g" ${tfVarsFile}
    sed -i -e "s/@OCI_FINGERPRINT@/"${fingerprint}"/g" ${tfVarsFile}
    sed -i -e "s:@OCI_API_PRIVATE_KEY_PATH@:${api_private_key_path}:g" ${tfVarsFile}

    sed -i -e "s:@OCI_REGION@:${region}:g" ${tfVarsFile}
    sed -i -e "s/@OCI_AVAILABILITY_DOMAIN_ID@/"${availability_domain_id}"/g" ${tfVarsFile}
    sed -i -e "s:@OCI_INSTANCE_PREFIX@:${prefix}:g" ${tfVarsFile}

    sed -i -e "s:@OCI_DEPLOY_NETWORKING@:${deploy_networking}:g" ${tfVarsFile}
    sed -i -e "s:@OCI_SUBNET_ID@:${subnet_id}:g" ${tfVarsFile}
    sed -i -e "s:@OCI_VCN_ID@:${vcn_id}:g" ${tfVarsFile}

    sed -i -e "s:@OCI_SSH_PUBLIC_KEY_PATH@:${ssh_public_key_path}:g" ${tfVarsFile}
    sed -i -e "s:@OCI_SSH_PRIVATE_KEY_PATH@:${ssh_private_key_path}:g" ${tfVarsFile}

    sed -i -e "s:@OCI_ENABLE_BASTION@:${enable_bastion}:g" ${tfVarsFile}

    sed -i -e "s:@OCI_VIRTUAL_IP@:${virtual_ip}:g" ${tfVarsFile}

    sed -i -e "s:@OCNE_CONTROL_PLANE_NODE_COUNT@:${control_plane_node_count}:g" ${tfVarsFile}
    sed -i -e "s:@OCNE_WORKER_NODE_COUNT@:${worker_node_count}:g" ${tfVarsFile}
    sed -i -e "s:@OCNE_ENVIRONMENT_NAME@:${environment_name}:g" ${tfVarsFile}
    sed -i -e "s:@OCNE_K8S_CLUSTER_NAME@:${kubernetes_name}:g" ${tfVarsFile}

    sed -i -e "s:@OCNE_VERSION@:${ocne_version}:g" ${tfVarsFile}

    sed -i -e "s:@NO_PROXY@:${no_proxy}:g" ${tfVarsFile}

    echo "Generated TFVars file [${tfVarsFile}]"
}

setupTerraform() {
    mkdir ${terraformDir}
    cd ${terraformDir}
    if [[ "${OSTYPE}" == "darwin"* ]]; then
      os_type="darwin"
    elif [[ "${OSTYPE}" == "linux"* ]]; then
       os_type="linux"
    else
       echo "Unsupported OS"
    fi
    curl -O https://releases.hashicorp.com/terraform/1.8.1/terraform_1.8.1_${os_type}_${platform}64.zip
    unzip terraform_1.8.1_${os_type}_${platform}64.zip
    chmod +x ${terraformDir}/terraform
    export PATH=${terraformDir}:${PATH}
}

deleteOlderVersionTerraformOCIProvider() {
    if [ -d ~/.terraform.d/plugins ]; then
        echo "Deleting older version of terraform plugins dir"
        rm -rf ~/.terraform.d/plugins
    fi
    if [ -d ${terraformVarDir}/.terraform ]; then
        rm -rf ${terraformVarDir}/.terraform
    fi
    if [ -e ~/.terraformrc ]; then
      rm ~/.terraformrc
    fi
}

createCluster () {
    cd ${terraformVarDir}
    echo "terraform init -var-file=${terraformVarDir}/terraform.tfvars"
    terraform init -var-file=${terraformVarDir}/terraform.tfvars
    terraform plan -var-file=${terraformVarDir}/terraform.tfvars
    terraform apply -auto-approve -var-file=${terraformVarDir}/terraform.tfvars
}

createRoleBindings () {
    ${KUBERNETES_CLI:-kubectl} -n kube-system create serviceaccount $okeclustername-sa
    ${KUBERNETES_CLI:-kubectl} create clusterrolebinding add-on-cluster-admin --clusterrole=cluster-admin --serviceaccount=kube-system:$okeclustername-sa
    TOKENNAME=`${KUBERNETES_CLI:-kubectl} -n kube-system get serviceaccount/$okeclustername-sa -o jsonpath='{.secrets[0].name}'`
    TOKEN=`${KUBERNETES_CLI:-kubectl} -n kube-system get secret $TOKENNAME -o jsonpath='{.data.token}'| base64 --decode`
    ${KUBERNETES_CLI:-kubectl} config set-credentials $okeclustername-sa --token=$TOKEN
    ${KUBERNETES_CLI:-kubectl} config set-context --current --user=$okeclustername-sa
}

checkKubernetesCliConnection() {
    echo "Confirming ${KUBERNETES_CLI:-kubectl} can connect to the server..."

    # Get the cluster public IP
    clusterPublicIP=$(oci ce cluster list --compartment-id="${compartment_ocid}" | jq -r '.data[] | select(."name" == "'"${okeclustername}"'" and (."lifecycle-state" == "ACTIVE")) | ."endpoints" | ."public-endpoint" | split(":")[0]')

    # Check if clusterPublicIP is empty or not
    if [ -z "$clusterPublicIP" ]; then
        echo "[ERROR] No active cluster found with name ${okeclustername}."
        exit 1
    fi
    echo "clusterPublicIP: ###$clusterPublicIP###"
    unset NO_PROXY
    export NO_PROXY=localhost,127.0.0.1,10.244.0.0/16,10.101.0.0/16,10.196.0.0/16,$clusterPublicIP
    echo "export NO_PROXY=:$NO_PROXY"

    local myline_output=$(${KUBERNETES_CLI:-kubectl} get nodes -o wide 2>&1)

    if echo "$myline_output" | grep -q "Unable to connect to the server: net/http: TLS handshake timeout"; then
        echo "[ERROR] Unable to connect to the server: net/http: TLS handshake timeout"
        echo '- could not talk to OKE cluster, aborting'
        unset http_proxy
        unset https_proxy
        myline_output=$(${KUBERNETES_CLI:-kubectl} get nodes -o wide 2>&1)
        if echo "$myline_output" | grep -q "Unable to connect to the server: net/http: TLS handshake timeout"; then
          cd "${terraformVarDir}"
          terraform destroy -auto-approve -var-file="${terraformVarDir}/${clusterTFVarsFile}.tfvars"
          exit 1
        fi
    fi
    if echo "$myline_output" | grep -q "couldn't get current server API group"; then
            echo "[ERROR] Unable to connect to the server: couldn't get current server API group, connection refused"
            echo '- check errors during OKE cluster creation'
            echo '- could not talk to OKE cluster, aborting'

            cd "${terraformVarDir}"
            terraform destroy -auto-approve -var-file="${terraformVarDir}/${clusterTFVarsFile}.tfvars"
            exit 1
    fi

}

checkClusterRunning() {
    checkKubernetesCliConnection

    local privateIP=${vcn_cidr_prefix}
    declare -a myline=($(${KUBERNETES_CLI:-kubectl} get nodes -o wide | grep "${privateIP}" | awk '{print $2}'))
    local NODE_IP=$(${KUBERNETES_CLI:-kubectl} get nodes -o wide | grep "${privateIP}" | awk '{print $7}')

    local status=${myline[0]}
    local max=100
    local count=1

    for i in {0..1}; do
        while [ "${myline[i]}" != "Ready" ] && [ $count -le $max ]; do
            echo "[ERROR] Some Nodes in the Cluster are not in the Ready Status, sleeping for 10s..."
            sleep 10
            myline=($(${KUBERNETES_CLI:-kubectl} get nodes -o wide | grep "${privateIP}" | awk '{print $2}'))
            NODE_IP=$(${KUBERNETES_CLI:-kubectl} get nodes -o wide | grep "${privateIP}" | awk '{print $7}')
            echo "myline[i]: ${myline[i]}"
            echo "Status is ${myline[i]} Iter [$count/$max]"
            count=$((count + 1))
        done
    done

    local NODES=$(${KUBERNETES_CLI:-kubectl} get nodes -o wide | grep "${privateIP}" | wc -l)

    if [ "$NODES" -eq 2 ]; then
        echo '- looks good'
    else
        echo '- could not talk to OKE cluster, aborting'
        cd "${terraformVarDir}"
        terraform destroy -auto-approve -var-file="${terraformVarDir}/${clusterTFVarsFile}.tfvars"
        exit 1
    fi

    if [ $count -gt $max ]; then
        echo "[ERROR] Unable to start the nodes in the OKE cluster after 200s"
        cd "${terraformVarDir}"
        terraform destroy -auto-approve -var-file="${terraformVarDir}/${clusterTFVarsFile}.tfvars"
        exit 1
    fi
}


#MAIN
propsFile=${1:-$PWD/oci.props}
terraformVarDir=${2:-$PWD}
platform=${3:-amd}

#grep props's values from oci.props file

tenancy_id=$(prop 'tenancy_id')
compartment_id=$(prop 'compartment_id')
user_id=$(prop 'user_id')
fingerprint=$(prop 'fingerprint')
api_private_key_path=$(prop 'api_private_key_path')

region=$(prop 'region')
availability_domain_id=$(prop 'availability_domain_id')
prefix=$(prop 'prefix')

deploy_networking=$(prop 'deploy_networking')
subnet_id=$(prop 'subnet_id')
vcn_id=$(prop 'vcn_id')

ssh_public_key_path=$(prop 'ssh_public_key_path')
ssh_private_key_path=$(prop 'ssh_private_key_path')

enable_bastion=$(prop 'enable_bastion')

virtual_ip=$(prop 'virtual_ip')

control_plane_node_count=$(prop 'control_plane_node_count')
worker_node_count=$(prop 'worker_node_count')
environment_name=$(prop 'environment_name')
kubernetes_name=$(prop 'kubernetes_name')

ocne_version=$(prop 'ocne_version')

proxy=$(prop 'proxy')
no_proxy=$(prop 'no_proxy')

terraformDir=$(prop 'terraform.installdir')

# generate terraform configuration file with name $(clusterTFVarsFile).tfvar
generateTFVarFile

# cleanup previously installed terraform binaries
rm -rf ${terraformDir}

# download terraform binaries into ${terraformDir}
setupTerraform

# clean previous versions of terraform oci provider
deleteOlderVersionTerraformOCIProvider

chmod 600 ${ocipk_path}

# run terraform init,plan,apply to create OKE cluster based on the provided tfvar file ${clusterTFVarsFile).tfvar
createCluster
#check status of OKE cluster nodes, destroy if can not access them
#export KUBECONFIG=${terraformVarDir}/${okeclustername}_kubeconfig


#export okeclustername=\"${okeclustername}\"


#echo " oci ce cluster list --compartment-id=${compartment_ocid} | jq '.data[]  | select(."name" == '"${okeclustername}"' and (."lifecycle-state" == "ACTIVE"))' | jq ' ."endpoints" | ."public-endpoint"'"

#clusterIP=$(oci ce cluster list --compartment-id=${compartment_ocid} | jq '.data[]  | select(."name" == '"${okeclustername}"' and (."lifecycle-state" == "ACTIVE"))' | jq ' ."endpoints" | ."public-endpoint"')
#echo "clusterIp : $clusterIP"
#clusterPublicIP=${clusterIP:1:-6}
#echo " clusterPublicIP : ${clusterPublicIP}"
#echo "NO_PROXY before : ${NO_PROXY}"
#export NO_PROXY=${clusterPublicIP}
#echo "NO_PROXY:" $NO_PROXY

#checkClusterRunning
#echo "${okeclustername} is up and running"
