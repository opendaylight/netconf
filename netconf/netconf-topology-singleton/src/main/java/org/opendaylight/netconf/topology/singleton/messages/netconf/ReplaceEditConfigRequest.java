package org.opendaylight.netconf.topology.singleton.messages.netconf;

import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;

public class ReplaceEditConfigRequest extends EditConfigRequest {
    private static final long serialVersionUID = 1L;

    public ReplaceEditConfigRequest(final LogicalDatastoreType store, final NormalizedNodeMessage data,
                                    final ModifyAction defaultOperation) {
        super(store, data, defaultOperation);
    }
}
