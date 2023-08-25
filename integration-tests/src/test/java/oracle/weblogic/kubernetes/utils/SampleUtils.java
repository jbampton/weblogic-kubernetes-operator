// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.Map;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;

import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;

/**
 * The Sample utility class for tests.
 */
public class SampleUtils {

  /**
   * Create PV hostPath and change permission in Kind cluster.
   * @param hostPath - hostPath for PV
   * @param envMap - envMap for running the docker command
   */
  public static void createPVHostPathAndChangePermissionInKindCluster(String hostPath, Map<String, String> envMap) {
    String command = WLSIMG_BUILDER_DEFAULT
        + " exec kind-worker sh -c \"mkdir "
        + hostPath
        + " && chmod g+w "
        + hostPath
        + "\"";

    Command.withParams(
        new CommandParams()
            .command(command)
            .env(envMap)
            .redirect(true)
    ).execute();
  }
}
