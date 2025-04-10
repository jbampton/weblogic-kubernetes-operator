### WebLogic Kubernetes Operator

The WebLogic Kubernetes Operator (the “operator”) supports running your WebLogic Server and Fusion Middleware Infrastructure domains on Kubernetes, an industry standard, cloud neutral deployment platform. It lets you encapsulate your entire WebLogic Server installation and layered applications into a portable set of cloud neutral images and simple resource description files. You can run them on any on-premises or public cloud that supports Kubernetes where you've deployed the operator.

Furthermore, the operator is well suited to CI/CD processes. You can easily inject changes when moving between environments, such as from test to production. For example, you can externally inject database URLs and credentials during deployment, or you can inject arbitrary changes to most WebLogic configurations.

The operator takes advantage of the [Kubernetes operator pattern](https://kubernetes.io/docs/concepts/extend-kubernetes/operator/), which means that it uses Kubernetes APIs to provide support for operations, such as: provisioning, lifecycle management, application versioning, product patching, scaling, and security. The operator also enables the use of tooling that is native to this infrastructure for monitoring, logging, tracing, and security.

You can:
* Deploy an operator that manages all WebLogic domains in all namespaces in a Kubernetes cluster, or that only manages domains in a specific subset of the namespaces, or that manages only domains that are located in the same namespace as the operator. At most, a namespace can be managed by one operator.
* Supply WebLogic domain configuration using:
  * _Model in Image_: Includes [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling) models and archives in a container image.
     * **NOTE**: Model in Image without auxiliary images (the WDT model and installation files are included in the same image with the WebLogic Server installation) is deprecated in WebLogic Kubernetes Operator version 4.0.7. Oracle recommends that you use Model in Image _with_ [auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}).
  * _Domain in Image_: Includes a WebLogic domain home in a container image.
     * **NOTE**: The Domain in Image [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) is deprecated in WebLogic Kubernetes Operator version 4.0. Oracle recommends that you choose either Domain on PV or Model in Image, depending on your needs.
  * _Domain on PV_: Locates WebLogic domain homes in a Kubernetes PersistentVolume (PV). This PV can reside in an NFS file system or other Kubernetes volume types.
* Configure deployment of WebLogic domains as Kubernetes resources (using Kubernetes custom resource definitions).
* Override certain aspects of the WebLogic domain configuration; for example, use a different database password for different deployments.
* Start and stop servers and clusters in the domain based on declarative startup parameters and desired states.
* Scale WebLogic domains by starting and stopping Managed Servers on demand, Kubernetes scale commands, setting up a Kubernetes Horizontal Pod Autoscaler, or by integrating with a REST API to initiate scaling based on the WebLogic Diagnostics Framework (WLDF), Prometheus, Grafana, or other rules.
* Expose HTTP paths on a WebLogic domain outside the Kubernetes domain with load balancing, and automatically update the load balancer when Managed Servers in the WebLogic domain are started or stopped.
* Expose the WebLogic Server Administration Console outside the Kubernetes cluster, if desired.
* Expose T3 channels outside the Kubernetes domain, if desired.
* Publish operator and WebLogic Server logs into Elasticsearch and interact with them in Kibana.

{{% notice tip %}}
The fastest way to experience the operator is to follow the [Quick Start guide]({{< relref "/quickstart/_index.md" >}}), or you can peruse our [documentation]({{< relref "/introduction/_index.md" >}}), read our [blogs](https://blogs.oracle.com/weblogicserver/how-to-weblogic-server-on-kubernetes), or try out the [samples]({{< relref "/samples/_index.md" >}}).
Also, you can step through the [Tutorial](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/hands-on-lab/README.md)
using the operator to deploy and run a WebLogic domain container-packaged web application on an Oracle Container Engine for Kubernetes (OKE) cluster.
{{% /notice %}}

***
#### Current production release

This is the [current release of the operator](https://github.com/oracle/weblogic-kubernetes-operator/releases).
See the [operator prerequisites]({{< relref "/introduction/prerequisites/introduction.md" >}}) and [supported environments]({{< relref "/introduction/platforms/environments.md" >}}).

***

#### Recent changes and known issues

See the [Release Notes](https://github.com/oracle/weblogic-kubernetes-operator/releases) for recent changes to the operator and [Known Limitations]({{< relref "/known-limitations/_index.md" >}}) for the current set of known issues.

#### Operator earlier versions

{{% notice note %}}
As of November 2024, version 3.4.x of the WebLogic Kubernetes Operator is no longer supported.
{{% /notice %}}

Documentation for prior releases of the operator: [3.4](https://oracle.github.io/weblogic-kubernetes-operator/3.4/), [4.0](https://oracle.github.io/weblogic-kubernetes-operator/4.0/), and [4.1](https://oracle.github.io/weblogic-kubernetes-operator/4.1/).

#### Backward compatibility guidelines

Starting from the 2.0.1 release, operator releases are backward compatible with respect to the domain
resource schema, operator Helm chart input values, configuration overrides template, Kubernetes resources created
by the operator Helm chart, Kubernetes resources created by the operator, and the operator REST interface. We intend to
maintain compatibility for three releases, except in the case of a clearly communicated deprecated feature, which will be
maintained for one release after a replacement is available.

#### Getting help

See [Get help]({{< relref "/introduction/get-help.md" >}}).

#### Related projects

* [Oracle Fusion Middleware on Kubernetes](https://oracle.github.io/fmw-kubernetes/)
* [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/)
* [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/)
* [WebLogic Monitoring Exporter](https://github.com/oracle/weblogic-monitoring-exporter)
* [WebLogic Remote Console](https://oracle.github.io/weblogic-remote-console/)
* [WebLogic Kubernetes Toolkit UI](https://oracle.github.io/weblogic-toolkit-ui/)
