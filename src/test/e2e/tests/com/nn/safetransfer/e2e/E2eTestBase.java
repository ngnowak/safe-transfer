package com.nn.safetransfer.e2e;

import com.nn.safetransfer.e2e.client.ActuatorApiClient;
import com.nn.safetransfer.e2e.client.TransferApiClient;
import com.nn.safetransfer.e2e.client.WalletApiClient;

public abstract class E2eTestBase {
    private static final String BASE_URL = System.getenv().getOrDefault("SAFETRANSFER_BASE_URL", "http://localhost:8080");
    protected static final WalletApiClient WALLET_API_CLIENT = new WalletApiClient(BASE_URL);
    protected static final TransferApiClient TRANSFER_API_CLIENT = new TransferApiClient(BASE_URL);
    protected static final ActuatorApiClient ACTUATOR_API_CLIENT = new ActuatorApiClient(BASE_URL);

}
