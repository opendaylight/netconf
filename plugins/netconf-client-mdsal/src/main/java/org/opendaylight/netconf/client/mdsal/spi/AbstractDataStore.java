package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractDataStore implements DataStoreService {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDataStore.class);

    static final ListenableFuture<DOMRpcResult> RPC_SUCCESS = Futures.immediateFuture(new DefaultDOMRpcResult());

    final Map<DatabindPath.Data, Collection<? extends NormalizedNode>> readListCache = new ConcurrentHashMap<>();
    final AtomicBoolean lock = new AtomicBoolean(false);
    final NetconfBaseOps netconfOps;
    final @NonNull RemoteDeviceId id;
    final boolean rollbackSupport;
    final boolean lockDatastore;

    AbstractDataStore(final NetconfBaseOps netconfOps, final RemoteDeviceId id, boolean rollbackSupport,
        boolean lockDatastore) {
        this.netconfOps = netconfOps;
        this.id = id;
        this.rollbackSupport = rollbackSupport;
        this.lockDatastore = lockDatastore;
    }

    public static @NonNull AbstractDataStore of(final RemoteDeviceId id, final DatabindContext databind,
        final RemoteDeviceServices.Rpcs rpcs, final NetconfSessionPreferences sessionPreferences, final boolean lockDatastore) {
        final var netconfOps = new NetconfBaseOps(databind, rpcs);
        return of(id, netconfOps, sessionPreferences, lockDatastore);
    }

    public static @NonNull AbstractDataStore of(final RemoteDeviceId id, final NetconfBaseOps netconfOps,
        final NetconfSessionPreferences sessionPreferences, final boolean lockDatastore) {
        final boolean rollbackSupport = sessionPreferences.isRollbackSupported();

        // Examine preferences and decide which implementation to use
        if (sessionPreferences.isCandidateSupported()) {
            return new Candidate(netconfOps, id, rollbackSupport, lockDatastore);
        } else if (sessionPreferences.isRunningWritable()) {
            return new Running(netconfOps, id, rollbackSupport, lockDatastore);
        } else {
            throw new IllegalArgumentException("Device " + id.name() + " has advertised neither :writable-running nor "
                + ":candidate capability. Failed to establish session, as at least one of these must be advertised.");
        }
    }


    public abstract ListenableFuture<? extends DOMRpcResult> commit();

    public abstract ListenableFuture<? extends DOMRpcResult> editConfig(EffectiveOperation operation,
            NormalizedNode child, YangInstanceIdentifier path);

    public abstract ListenableFuture<? extends DOMRpcResult> editConfig(EffectiveOperation operation,
        YangInstanceIdentifier path);

    public abstract ListenableFuture<? extends DOMRpcResult> editConfig(AnyxmlNode<DOMSource> node);

    public ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store, final YangInstanceIdentifier path,
        final List<YangInstanceIdentifier> fields) {
        return switch (store) {
            case CONFIGURATION -> getConfig(path, fields);
            case OPERATIONAL -> get(path, fields);
        };
    }

    public ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        return switch (store) {
            case CONFIGURATION -> getConfig(path);
            case OPERATIONAL -> get(path);
        };
    }

    abstract ListenableFuture<? extends DOMRpcResult> unlock();

    abstract void cancel();

    ListenableFuture<Optional<NormalizedNode>> get(final YangInstanceIdentifier path) {
        return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path));
    }

    ListenableFuture<Optional<NormalizedNode>> get(final YangInstanceIdentifier path,
        final List<YangInstanceIdentifier> fields) {
        return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path), fields);
    }

    ListenableFuture<Optional<NormalizedNode>> getConfig(final YangInstanceIdentifier path) {
        return netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id),
            Optional.ofNullable(path));
    }

    ListenableFuture<Optional<NormalizedNode>> getConfig(final YangInstanceIdentifier path,
        final List<YangInstanceIdentifier> fields) {
        return netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id),
            Optional.ofNullable(path), fields);
    }

    static void executeWithLogging(final Supplier<ListenableFuture<? extends DOMRpcResult>> operation) {
        var operationResult = operation.get();
        Futures.addCallback(operationResult, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult rpcResult) {
                if (rpcResult != null && !rpcResult.errors().isEmpty()) {
                    LOG.error("Errors occurred during processing of the RPC operation: {}",
                        rpcResult.errors().stream().map(Object::toString).collect(Collectors.joining(",")));
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Error processing operation", throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    static boolean allWarnings(final Collection<? extends @NonNull RpcError> errors) {
        return errors.stream().allMatch(error -> error.getSeverity() == ErrorSeverity.WARNING);
    }

    ListenableFuture<? extends DOMRpcResult> addIntoFutureChain(
            final ListenableFuture<? extends DOMRpcResult> futureChain,
            final Supplier<ListenableFuture<? extends DOMRpcResult>> nextFuture, final boolean unlockInSuccess) {
        return Futures.transformAsync(futureChain,
            result -> {
                if (result != null && !result.errors().isEmpty() && !allWarnings(result.errors())) {
                    return Futures.immediateFailedFuture(getNetconfDocumentedException(result.errors()));
                }
                final var listenableFuture = nextFuture.get();
                addCallback(listenableFuture, unlockInSuccess);
                return listenableFuture;
            }, Executors.newSingleThreadExecutor());
    }

    void addCallback(final ListenableFuture<? extends DOMRpcResult> listenableFuture, final boolean unlockInSuccess) {
        Futures.addCallback(listenableFuture, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (unlockInSuccess) {
                    unlock();
                }
            }

            @Override
            public void onFailure(final Throwable cause) {
                cancel();
            }
        }, Executors.newSingleThreadExecutor());
    }

    static NetconfDocumentedException getNetconfDocumentedException(
        final Collection<? extends RpcError> errors) {
        var errType = ErrorType.APPLICATION;
        var errSeverity = ErrorSeverity.ERROR;
        final var msgBuilder = new StringJoiner(" ");
        var errorTag = ErrorTag.OPERATION_FAILED;
        for (final RpcError error : errors) {
            errType = error.getErrorType();
            errSeverity = error.getSeverity();
            msgBuilder.add(error.getMessage());
            msgBuilder.add(error.getInfo());
            errorTag = error.getTag();
        }
        return new NetconfDocumentedException("RPC during tx failed. " + msgBuilder, errType, errorTag, errSeverity);
    }
}
