package org.opendaylight.netconf.topology.singleton.messages.transactions;

import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;

public class MergeEditConfigRequest extends EditConfigRequest {
    private static final long serialVersionUID = 1L;

    public MergeEditConfigRequest(LogicalDatastoreType store, NormalizedNodeMessage data,
                                  ModifyAction defaultOperation) {
        super(store, data, defaultOperation);
    }
}
