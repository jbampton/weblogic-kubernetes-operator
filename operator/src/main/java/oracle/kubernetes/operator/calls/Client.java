// Copyright (c) 2017, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import io.kubernetes.client.monitoring.Monitoring;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.ClientBuilder;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

public class Client {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static ClientFactory factory = new DefaultClientFactory();

  private static final AtomicReference<ApiClient> singleton = new AtomicReference<>();

  /**
   * Get Kubernetes API client instance, creating if necessary.
   * @return API client
   */
  public static ApiClient getInstance() {
    return singleton.getAndUpdate(c -> {
      if (c != null) {
        return c;
      }
      try {
        LOGGER.fine(MessageKeys.CREATING_API_CLIENT);
        ApiClient client = factory.get();
        Monitoring.installMetrics(client);
        Configuration.setDefaultApiClient(client);
        return client;
      } catch (IOException e) {
        LOGGER.warning(MessageKeys.EXCEPTION, e);
        throw new RuntimeException(e);
      }
    });
  }

  private static class DefaultClientFactory implements ClientFactory {
    @Override
    public ApiClient get() throws IOException {
      return ClientBuilder.standard().build();
    }
  }
}
