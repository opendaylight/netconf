package org.opendaylight.netconf.topology.singleton.messages.transactions;

import java.io.Serializable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class DeleteEditConfigRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final LogicalDatastoreType store;
    private final YangInstanceIdentifier path;

    public DeleteEditConfigRequest(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        this.store = store;
        this.path = path;
    }

    public LogicalDatastoreType getStore() {
        return store;
    }

    public YangInstanceIdentifier getPath() {
        return path;
    }
}
