+++
title = "Upgrade WLS and FMW domains to v14.1.2.0"
date = 2023-10-05T16:45:16-05:00
weight = 1
pre = "<b> </b>"
description = "Guidelines for upgrading WLS and FMW infrastructure domains to v14.1.2.0."
+++

{{< table_of_contents >}}


### Important considerations

By default, version 14.1.2.0 WLS and FMW infrastructure domains _in production mode_ are set to secured production mode, in which their default security configuration
is more secure, insecure configurations are logged as warnings, and default authorization and
role mapping policies are more restrictive.

Some important Secure Production Mode changes are:

*  Plain HTTP listen ports are disabled.  Any application code, utilities, or ingresses that use plain HTTP listen ports must be changed.

*  SSL listen ports must be enabled for every server in the domain.  Each server must have at least one SSL listen port set up, either in the default channel or in one of the custom network channels.  If none is explicitly enabled, WebLogic Server, by default, will enable the default SSL listen port and use the demo SSL certificate.   
Note that demo SSL certificates should **not** be used in a production environment; you should set up SSL listen ports with valid SSL certificates in all server instances.

For more information, see the [secured production mode](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/lockd/secure.html#GUID-ADF914EF-0FB6-446E-B6BF-D230D8B0A5B0) documentation.

**NOTE**: If the domain is _not_ in production mode, then none of the security changes apply.

### General upgrade procedures

In general, the process for upgrading WLS and FMW infrastructure domains in Kubernetes is similar to upgrading domains on premises. For a thorough understanding, we suggest that you read the [Fusion Middleware Upgrade Guide](https://docs.oracle.com/en/middleware/fusion-middleware/12.2.1.4/asmas/planning-upgrade-oracle-fusion-middleware-12c.html#GUID-D9CEE7E2-5062-4086-81C7-79A33A200080).

Before the upgrade, you must do the following:

- If your [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) is Domain on Persistent Volume (DoPV), then back up the domain home.
- If your domain type is JRF, then:
   - Back up the JRF database.
   - Back up the OPSS wallet file. See [Save the OPSS wallet secret](#back-up-the-opss-wallet-and-save-it-in-a-secret).
   - Make sure nothing else is accessing the database.
- Shut down the domain by setting `serverStartPolicy: Never` in the domain and cluster resource YAML file.
- **Do not delete** the domain resource.

WebLogic provides two utilities for performing major version upgrades of WebLogic domains: the Upgrade Assistant and the Reconfiguration Wizard.
Because a typical Kubernetes environment lacks a graphical interface, you must run these utilities with the command-line options.

#### Back up the OPSS wallet and save it in a secret

The operator provides a helper script, the [OPSS wallet utility](https://orahub.oci.oraclecorp.com/weblogic-cloud/weblogic-kubernetes-operator/-/blob/main/kubernetes/samples/scripts/domain-lifecycle/opss-wallet.sh), for extracting the wallet file and storing it in a Kubernetes `walletFileSecret`. In addition, you should save the wallet file in a safely backed-up location, outside of Kubernetes. For example, the following command saves the OPSS wallet for the `sample-domain1` domain in the `sample-ns` namespace to a file named `ewallet.p12` in the `/tmp` directory and also stores it in the wallet secret named `sample-domain1-opss-walletfile-secret`.

```
$ opss-wallet.sh -n sample-ns -d sample-domain1 -s -r -wf /tmp/ewallet.p12 -ws sample-domain1-opss-walletfile-secret
```

#### Deploy a WebLogic Server pod attaching a persistent volume

For DoPV domains, you will need to access the domain home on the shared volume with a new WebLogic Server version pod.
You can launch a running pod with the [PV and PVC helper script](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/scripts/domain-lifecycle/pv-pvc-helper.sh).

For example,

```shell
$ ./pv-pvc-helper.sh -n sample-domain1-ns -c sample-domain1-pvc-rwm1 -m /share -i wls14120:fmw
```

After the pod is deployed, you can follow the instructions to `kubectl exec` into the pod's terminal session.

#### Upgrade the JRF database

The Upgrade Assistant is for upgrading schemas in a JRF database.  It will detect if any schema needs to be upgraded, then upgrade the schemas, and also upgrade the system-owned schema version table.

If you have not yet deployed a WebLogic Server pod, see [Deploy the server pod](#deploy-a-weblogic-server-pod-attaching-a-persistent-volume).

From the `pvhelper` pod:

To discover all the command-line options.

```shell
$ cd $ORACLE_HOME/oracle_common/upgrade/bin
$ ./ua -help
```

Create a file named `response.txt` with this content and modify any `<TODO:` to match your environment.

```
# This is a response file for the Fusion Middleware Upgrade Assistant.
# Individual component upgrades are performed in the order they are described here.
# Each upgrade is introduced by a section header containing the name of the
# component and name of the upgrade plugin. The form of the section header is
# [ComponentName.PluginName]
# These names can be found in the Upgrade Descriptor files for the components.

# Individual input lines consist of a name, an equal sign, and a value.
# The name is in two parts separated by a period.  The first part is the "name"
# attribute from the Descriptor File XML tag by which the plugin refers to the value.
# The second part of the name identifies a field within that value.  Some input
# types have only one field, while other types can have half a dozen.   Do not
# intermix input lines that apply to different XML tags.

[GENERAL]
# This is the file format version number.  Do not change the next line.
fileFormatVersion = 3

# The next section contains information for accessing a WebLogic Server domain.

[UAWLSINTERNAL.UAWLS]
# The following number uniquely identifies this instance of an
# upgrade plugin.  Do not change it.
pluginInstance = 1

# Specifies the WebLogic Server domain directory:

#UASVR.path = /share/domains/sample-domain1
UASVR.path = <TODO: provides the complete domain home path>

# The next section contains the information for performing a schema
# upgrade on Oracle Platform Security Services, as described in the Upgrade
# Descriptor file located at
#   /u01/oracle/oracle_common/plugins/upgrade/Opss.xml

# Do not change the next line.
[OPSS.OPSS_SCHEMA_PLUGIN]

# The following number uniquely identifies this instance of an
# upgrade plugin.  Do not change it.
pluginInstance = 10

# The next few lines describe a database connection.
#  "Specify the database containing the OPSS schema."
# Specifies the type of database.  Supported types for this product are
#   Oracle Database, Oracle Database enabled for edition-based redefinition, Microsoft SQL Server, IBM DB2

OPSS.databaseType = Oracle Database

# Specifies the database connection string for the DBA user.
# The format depends upon the database type.

#OPSS.databaseConnectionString = //nuc:1521/orclpdb1
OPSS.databaseConnectionString = <TODO: provides the connection string>

# Specifies the database connection string for the user schema.
# The format depends upon the database type.

#OPSS.schemaConnectionString = //nuc:1521/orclpdb1
OPSS.schemaConnectionString = <TODO: provides the connection string>

# Specifies the name of the schema or database user

#OPSS.schemaUserName = FMWTEST_OPSS
OPSS.schemaUserName = <TODO: provides the schema name rcuprefix_OPSS >

# Specifies the password for the schema, in encrypted form.
# To specify a different password in cleartext, use the "cleartextSchemaPassword" keyword instead:

OPSS.cleartextSchemaPassword = <TODO: provides the clear text password>

# encrypted password can be generated with command line option -createResponse
#OPSS.encryptedSchemaPassword = 0551CF2EACFC4FE7BCB1F860FCF68E13AA6E61A724E7CFC09E
# Specifies the name of the database administrator account.

OPSS.dbaUserName = <TODO: provide dba user name, e.g. sys as sysdba>

# Specifies the password for the database administrator account, in encrypted form.
# To specify a different password in cleartext, use the "cleartextDbaPassword" keyword
# instead:

OPSS.cleartextDbaPassword = <TODO: provides clear text dba password>

#OPSS.encryptedDbaPassword = 057B3698F71FB2EE583D32EF36234174DCC2C7276FC11F77E7

# The next section contains the information for performing a schema
# upgrade on Oracle Metadata Services, as described in the Upgrade
# Descriptor file located at
#   /u01/oracle/oracle_common/plugins/upgrade/mds.xml
# Do not change the next line.

[MDS.SCHEMA_UPGRADE]
pluginInstance = 11

MDS.databaseConnectionString = <TODO: provides the connection string>
MDS.schemaConnectionString = <TODO: provides the connection string>
MDS.schemaUserName = <TODO: provides the schema name rcuprefix_MDS >
MDS.cleartextSchemaPassword = <TODO: provides the clear text password>
MDS.dbaUserName = <TODO: provide dba user name, e.g. sys as sysdba>
MDS.cleartextDbaPassword = <TODO: provides clear text dba password>

# The next section contains the information for performing a schema
# upgrade on Oracle Audit Services, as described in the Upgrade
# Descriptor file located at
#   /u01/oracle/oracle_common/plugins/upgrade/audit.xml
# Do not change the next line.

[IAU.AUDIT_SCHEMA_PLUGIN]
pluginInstance = 6

IAU.databaseType = Oracle Database
IAU.databaseConnectionString = <TODO: provides the connection string>
IAU.schemaConnectionString = <TODO: provides the connection string>
IAU.schemaUserName = <TODO: provides the schema name rcuprefix_IAU >
IAU.cleartextSchemaPassword = <TODO: provides the clear text password>
IAU.dbaUserName = <TODO: provide dba user name, e.g. sys as sysdba>
IAU.cleartextDbaPassword = <TODO: provides clear text dba password>


# The next section contains the information for performing a schema
# upgrade on Common Infrastructure Services, as described in the Upgrade
# Descriptor file located at
#   /u01/oracle/oracle_common/plugins/upgrade/cie.xml
# Do not change the next line.

[FMWCONFIG.CIE_SCHEMA_PLUGIN]
pluginInstance = 4

STB.databaseType = Oracle Database
STB.databaseConnectionString = <TODO: provides the connection string>
STB.schemaConnectionString = <TODO: provides the connection string>
STB.schemaUserName = <TODO: provides the schema name rcuprefix_STB >
STB.cleartextSchemaPassword = <TODO: provides the clear text password>
STB.dbaUserName = <TODO: provide dba user name, e.g. sys as sysdba>
STB.cleartextDbaPassword = <TODO: provides clear text dba password>

# This secion is not needed for pure JRF domain.

# The next section contains the information for performing a schema
# upgrade on Oracle WebLogicServer, as described in the Upgrade
# Descriptor file located at
#   /u01/oracle/oracle_common/plugins/upgrade/wlsservices.xml
# Do not change the next line.

#[WLS.WLS]
#pluginInstance = 7

#WLS.databaseType = Oracle Database
#WLS.databaseConnectionString =
#WLS.schemaConnectionString =
#WLS.schemaUserName =
#WLS.encryptedSchemaPassword = 05FEC474FC653B49B15ED79A53565A8B00F49ADADA72D30816
#WLS.dbaUserName =
# WLS.cleartextDbaPassword =
#WLS.encryptedDbaPassword = 0543C93F9A28FBAFBF3FCC49E78EB2C6B3AA02F53098BB322C

```

Copy the response file to the pod.

```shell
$ kubectl -n sample-domain1-ns cp response.txt pvhelper:/tmp
```

Run the Upgrade Assistant readiness check to verify the input values and whether the schema needs to be upgraded.

```shell
$ ./ua -readiness -response /tmp/response.txt -logDir /tmp
```

Check the output to see if there are any errors.

```
Oracle Fusion Middleware Upgrade Assistant 14.1.2.0.0
Log file is located at: /tmp/ua2023-10-04-17-23-32PM.log
Reading installer inventory, this will take a few moments...
...completed reading installer inventory.
Using response file /tmp/response.txt for input
 Oracle Metadata Services schema readiness check is in progress
 Oracle Audit Services schema readiness check is in progress
 Oracle Platform Security Services schema readiness check is in progress
 Common Infrastructure Services schema readiness check is in progress
 Common Infrastructure Services schema readiness check finished with status: ready for upgrade
 Oracle Metadata Services schema readiness check finished with status: ready for upgrade
 Oracle Audit Services schema readiness check finished with status: ready for upgrade
 Oracle Platform Security Services schema readiness check finished with status: ready for upgrade
Readiness Check Report File: /tmp/readiness2023-10-04-17-24-55PM.txt
Upgrade readiness check completed successfully.
UPGAST-00281: Upgrade is being skipped because the -readiness flag is set
Actual upgrades are not done when the -readiness command line option is set.
If you want to perform an actual upgrade remove the -readiness flag from the command line.  If you intended to perform just the readiness phase, no action is necessary.
```

If there are no errors and you are ready to upgrade, run the command again without the `-readiness` flag.

```shell
$ ./ua -response /tmp/response.txt -logDir /tmp
```

Check the output again.

```
Oracle Fusion Middleware Upgrade Assistant 14.1.2.0.0
Log file is located at: /u01/oracle/oracle_common/upgrade/logs/ua2023-10-05-14-03-18PM.log
Reading installer inventory, this will take a few moments...
...completed reading installer inventory.
Using response file /tmp/response.txt for input
 Oracle Platform Security Services schema examine is in progress
 Oracle Metadata Services schema examine is in progress
 Oracle Audit Services schema examine is in progress
 Common Infrastructure Services schema examine is in progress
 Common Infrastructure Services schema examine finished with status: ready for upgrade
 Oracle Platform Security Services schema examine finished with status: ready for upgrade
 Oracle Audit Services schema examine finished with status: ready for upgrade
 Oracle Metadata Services schema examine finished with status: ready for upgrade
Schema Version Registry saved to: /u01/oracle/oracle_common/upgrade/logs/ua2023-10-05-14-03-18PM.xml
 Oracle Platform Security Services schema upgrade is in progress
 Oracle Audit Services schema upgrade is in progress
 Oracle Metadata Services schema upgrade is in progress
 Common Infrastructure Services schema upgrade is in progress
 Common Infrastructure Services schema upgrade finished with status: succeeded
 Oracle Audit Services schema upgrade finished with status: succeeded
 Oracle Platform Security Services schema upgrade finished with status: succeeded
 Oracle Metadata Services schema upgrade finished with status: succeeded
```

If there are any errors, you need to correct them or contact Oracle Support for assistance.


####  Reconfigure the domain

The Reconfiguration Wizard will upgrade the domain configuration to the new WebLogic version.

If you have not yet deployed a WebLogic Server pod, see [Deploy the server pod](#deploy-a-weblogic-server-pod-attaching-a-persistent-volume).

From the `pvhelper` pod, use the following WLST commands to reconfigure a domain to the new version of WebLogic Server.


```shell
$ /u01/oracle/oracle_common/bin/wlst.sh

Initializing WebLogic Scripting Tool (WLST) ...

Welcome to WebLogic Server Administration Scripting Shell

Type help() for help on available commands

wls:/offline> readDomainForUpgrade('<your domain home directory path')
wls:/offline> updateDomain()
wls:/offline> closeDomain()
```

If there are any errors, you need to correct them or contact Oracle Support for assistance.

### Upgrade use cases

Consider the following use case scenarios, depending on your WebLogic domain type (WLS or FMW/JRF) and domain home source type (Domain on PV or Model in Image).

#### WLS Domain on Persistent Volume

1. Follow the steps in the [General upgrade procedures](#general-upgrade-procedures).  You can skip the database related steps.
2. Upgrade the domain configuration using the reconfiguration WLST commands. See [Reconfigure the domain](#reconfiguration-of-the-domain).
3. Update the domain resource to use the WebLogic 14120 base image, set `serverStartPolicy: IfNeeded` in the domain and cluster resource YAML file, and restart the domain.

```
spec:
  image: <wls 14120 image>
```

#### FMW/JRF Domain on Persistent Volume

1. Follow the steps in the [General upgrade procedures](#general-upgrade-procedures).
2. Run the Upgrade Assistant. See [Upgrade the JRF database](#upgrade-the-jrf-database).
3. Upgrade the domain configuration using the reconfiguration WLST commands. See [Reconfigure the domain](#reconfiguration-of-the-domain).
4. Update the domain resource to use the WebLogic 14120 base image, set `serverStartPolicy: IfNeeded` in the domain and cluster resource YAML file, and restart the domain.

```
spec:
  image: <wls 14120 image>
```

#### WLS domain using Model in Image

As described in [Important considerations](#important-considerations), in v14.2.1.0 production domains, the plain HTTP listening port is disabled and an SSL listening port is required.  

If your domain is already using secured production mode, then you can simply update the base image in the domain resource YAML file and redeploy the domain.

If your domain is not using secured production mode, the best approach is to switch to your domain to using it. See the [Sample WDT YAML](#sample-wdt-model-for-secured-production-mode-and-ssl). Before upgrading, change your applications, utilities, and ingresses to use SSL ports.

If for some reason, you cannot switch to your domain to secured production mode, you can still continue with the typical application lifecycle update process. In this case, when you upgrade the WebLogic version to 14.1.2.0, the operator will automatically disable the secure mode for you.

#### FMW/JRF domain using Model in Image

FMW/JRF domains using Model in Image has been deprecated since WebLogic Kubernetes Operator 4.1. Before upgrading to FMW v14.1.2.0, we recommend moving your domain home to Domain on Persistent Volume. For more information, see [Domain On Persistent Volume]({{< relref "/managing-domains/domain-on-pv/overview.md" >}}).

1. Follow the steps in the [General upgrade procedures](#general-upgrade-procedures).
2. If are not using an auxiliary image in your domain, then create a [Domain creation image]({{< relref "/managing-domains/domain-on-pv/domain-creation-images.md" >}}).
3. Create a new domain resource YAML file.  You should have at least the following changes:

```
# Change type to PersistentVolume
domainHomeSourceType: PersistentVolume
...
serverPod:
    ...
    # specify the volume and volume mount information

    volumes:
    - name: weblogic-domain-storage-volume
      persistentVolumeClaim:
         claimName: sample-domain1-pvc-rwm1
    volumeMounts:
    - mountPath: /share
      name: weblogic-domain-storage-volume

  # specify a new configuration section, remove the old configuration section.

  configuration:

    # secrets that are referenced by model yaml macros
    # sample-domain1-rcu-access is used for JRF domains
    secrets: [ sample-domain1-rcu-access ]

    initializeDomainOnPV:
      persistentVolumeClaim:
        metadata:
            name: sample-domain1-pvc-rwm1
        spec:
            storageClassName: my-storage-class
            resources:
                requests:
                    storage: 10Gi
      domain:
          createIfNotExists: Domain
          domainCreationImages:
          - image: 'myaux:v6'
          domainType: JRF
          domainCreationConfigMap: sample-domain1-wdt-config-map
          opss:
            walletFileSecret: sample-domain1-opss-walletfile-secret
            walletPasswordSecret: sample-domain1-opss-wallet-password-secret
```

4. Deploy the domain. If it is successful, then the domain has been migrated to a persistent volume.  You can proceed upgrade to version 14.1.2, see [FMW/JRF domain on PV](#fmwjrf-domain-on-persistent-volume).

### Sample WDT model for secured production mode and SSL

The following is a sample snippet of a WDT model for setting up secured production mode and SSL.

```
topology:
  ProductionModeEnabled: true
  # SecureMode is turned on by default in 14.1.2
  SecurityConfiguration:
    SecureMode:
      SecureModeEnabled: true
    Server:
        "admin-server":
            CustomTrustKeyStoreFileName: 'wlsdeploy/servers/admin-server/trust-keystore.jks'
            CustomIdentityKeyStoreFileName: 'wlsdeploy/servers/admin-server/identity-keystore.jks'
            KeyStores: CustomIdentityAndCustomTrust
            CustomIdentityKeyStoreType: JKS
            CustomTrustKeyStoreType: JKS           
            CustomIdentityKeyStorePassPhraseEncrypted:            
            CustomTrustKeyStorePassPhraseEncrypted:
            SSL:
                ListenPort: 7002
                Enabled : true            
                ServerPrivateKeyAlias: adminkey
                ServerPrivateKeyPassPhraseEncrypted:
    ServerTemplate:
       "cluster-1-template":
            CustomTrustKeyStoreFileName: 'wlsdeploy/servers/managed-server/trust-keystore.jks'
            CustomIdentityKeyStoreFileName: 'wlsdeploy/servers/managed-server/identity-keystore.jks'
            KeyStores: CustomIdentityAndCustomTrust
            CustomIdentityKeyStoreType: JKS
            CustomTrustKeyStoreType: JKS           
            CustomIdentityKeyStorePassPhraseEncrypted:            
            CustomTrustKeyStorePassPhraseEncrypted:
            SSL:
                ListenPort: 7102
                Enabled : true            
                ServerPrivateKeyAlias: mykey
                ServerPrivateKeyPassPhraseEncrypted:

```
