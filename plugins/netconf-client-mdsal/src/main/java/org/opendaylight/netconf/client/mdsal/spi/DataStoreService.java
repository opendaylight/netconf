package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public interface DataStoreService {

    ListenableFuture<? extends DOMRpcResult> commit();

    ListenableFuture<? extends DOMRpcResult> editConfig(EffectiveOperation operation, NormalizedNode child,
        YangInstanceIdentifier path);

    ListenableFuture<? extends DOMRpcResult> editConfig(EffectiveOperation operation,
        YangInstanceIdentifier path);

    ListenableFuture<? extends DOMRpcResult> editConfig(AnyxmlNode<DOMSource> node);

    ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store, final YangInstanceIdentifier path,
        final List<YangInstanceIdentifier> fields);

    ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store, final YangInstanceIdentifier path);
}
