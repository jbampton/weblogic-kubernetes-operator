#!/bin/bash
# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

env
echo $JAVA_HOME
echo $result_root
MW_HOME="$result_root/mwhome"
SILENT_RESPONSE_FILE=$result_root/silent.response
ORAINVENTORYPOINTER_LOC=$result_root/oraInv.loc
ORAINVENTORY_LOC=$result_root/oraInventory
WLS_SHIPHOME=$result_root/fmw_wls_generic.jar

echo "creating $SILENT_RESPONSE_FILE file with contents"

cat <<EOF > $SILENT_RESPONSE_FILE
[ENGINE]
Response File Version=1.0.0.0.0
[GENERIC]
ORACLE_HOME=$MW_HOME
INSTALL_TYPE=WebLogic Server
EOF

cat $SILENT_RESPONSE_FILE

echo "creating $ORAINVENTORYPOINTER_LOC file with contents"

cat <<EOF > $ORAINVENTORYPOINTER_LOC
inventory_loc=$ORAINVENTORY_LOC
inst_group=opc
EOF

cat $ORAINVENTORYPOINTER_LOC

mkdir -p $MW_HOME

# Check if the variable WEBLOGIC_IMAGE_TAG is set
if [[ -z "$WEBLOGIC_IMAGE_TAG" ]]; then
  echo "WEBLOGIC_IMAGE_TAG is not set."
  echo "Not proceeding with installation of WebLogic shiphome"
  exit 0
fi

#download WebLogic shiphome installer
if [[ "$WEBLOGIC_IMAGE_TAG" == *"12.2.1.4"* ]]; then
  DOWNLOAD_URL = http://$SHIPHOME_DOWNLOAD_SERVER/results/release/src122140psu/fmw_12.2.1.4.0_wls_generic.jar  
elif [[ "$WEBLOGIC_IMAGE_TAG" == *"14.1.2"* ]]; then
  DOWNLOAD_URL = http://$SHIPHOME_DOWNLOAD_SERVER/results/release/src141200/fmw_14.1.2.0.0_wls_generic.jar
else
  echo "WEBLOGIC_IMAGE_TAG does not contain the required version."
  echo "Not proceeding with installation of WebLogic shiphome"
  exit 0
fi
curl -Lo $WLS_SHIPHOME $DOWNLOAD_URL

#install WebLogic
mkdir -p $MW_HOME
java -jar $WLS_SHIPHOME -silent -responseFile $SILENT_RESPONSE_FILE -invPtrLoc $ORAINVENTORYPOINTER_LOC
