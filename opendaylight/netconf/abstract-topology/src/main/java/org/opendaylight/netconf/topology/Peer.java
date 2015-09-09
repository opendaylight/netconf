package org.opendaylight.netconf.topology;

/**
 *
 */
public interface Peer<T extends Peer<T>> {

    boolean isMaster();

    Iterable<T> getPeers();

    /**
     * Used for communication between NodeAdministratorCallbacks
     */
    public interface PeerContext<M> {

        // TODO the message needs to be serialized and sent through AKKA, how to achieve it ?
        void notifyPeers(M msg);

    }
}
