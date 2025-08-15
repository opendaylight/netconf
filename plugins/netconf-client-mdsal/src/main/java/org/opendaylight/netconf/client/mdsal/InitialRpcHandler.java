package org.opendaylight.netconf.client.mdsal;

import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;

public interface InitialRpcHandler {

    /**
     * Sets the listener responsible for session operations, like disconnect.
     * This listener will be invoked if an RPC times out or fails.
     */
    void setListener(NetconfDeviceCommunicator listener);

    /**
     * Returns the underlying RemoteDeviceHandler (e.g., the KeepaliveSalFacade)
     * which will be used for the device's steady-state operation.
     */
    RemoteDeviceHandler remoteDeviceHandler();

    /**
     * Wraps the given Rpcs service with the initialization logic
     * (e.g., timeout and disconnect-on-failure).
     */
    Rpcs decorateRpcs(Rpcs service);
}
