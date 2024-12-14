// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;

import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

public class Service {

  /**
   * Check is a service exists in given namespace.
   *
   * @param serviceName the name of the service to check for
   * @param label a Map of key value pairs the service is decorated with
   * @param namespace in which the service is running
   * @return true if the service exists otherwise false
   */
  public static Callable<Boolean> serviceExists(String serviceName, Map<String, String> label,
      String namespace) {
    return () -> {
      try {
        return Kubernetes.doesServiceExist(serviceName, label, namespace);
      } catch (ApiException aex) {
        getLogger().info("Failed to check whether service {0} in namespace {1} exists! Caught ApiException!",
            serviceName, namespace);
        getLogger().info("Printing aex.getCode:");
        aex.getCode();
        getLogger().info("Printing aex.getResponseBody:");
        aex.getResponseBody();
        getLogger().info("Printing aex.printStackTrace:");
        aex.printStackTrace();

        // try one more time
        getLogger().info("Try one more time to check whether service {0} in namespace {1} exists!",
            serviceName, namespace);
        return Kubernetes.doesServiceExist(serviceName, label, namespace);
      }
    };
  }
}
