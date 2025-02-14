/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.databind.DatabindPath.Action;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.DatabindPath.InstanceRequest;
import org.opendaylight.netconf.databind.DatabindPath.Rpc;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.server.api.ChildBody;
import org.opendaylight.restconf.server.api.ChildBody.PrefixAndBody;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostBody;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.OptionsResult;
import org.opendaylight.restconf.server.api.PatchBody;
import org.opendaylight.restconf.server.api.PatchContext;
import org.opendaylight.restconf.server.api.ResourceBody;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.SourceRepresentation;

/**
 * Abstract base class for {@link ServerStrategy} implementations based on {@link ServerActionOperations}.
 */
@Beta
public abstract class AbstractServerStrategy implements ServerStrategy {
    protected final @NonNull ApiPathNormalizer pathNormalizer;
    protected final @NonNull DatabindContext databind;
    private final @NonNull Data emptyPath;
    private final HttpGetResource operations;

    protected AbstractServerStrategy(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
        pathNormalizer = new ApiPathNormalizer(databind);
        emptyPath = new DatabindPath.Data(databind);
        operations = new OperationsResource(pathNormalizer);
    }

    protected abstract @NonNull ServerActionOperations action();

    protected abstract @NonNull ServerDataOperations data();

    protected abstract @NonNull ServerModulesOperations modules();

    protected abstract @NonNull ServerRpcOperations rpc();

    protected abstract @NonNull ServerMountPointResolver resolver();

    public final @NonNull DatabindContext databind() {
        return databind;
    }

    @Override
    public final void dataDELETE(final ServerRequest<Empty> request, final ApiPath apiPath) {
        final Data path;
        try {
            path = pathNormalizer.normalizeDataPath(apiPath);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        final var instance = path.instance();
        if (instance.isEmpty()) {
            request.completeWith(new RequestException(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED,
                "Cannot delete a datastore"));
            return;
        }
        data().deleteData(request, path);
    }

    @Override
    public final void dataGET(final ServerRequest<DataGetResult> request) {
        dataGET(request, emptyPath);
    }

    @Override
    public final void dataGET(final ServerRequest<DataGetResult> request, final ApiPath apiPath) {
        final Data path;
        try {
            path = pathNormalizer.normalizeDataPath(apiPath);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        dataGET(request, path);
    }

    @NonNullByDefault
    private void dataGET(final ServerRequest<DataGetResult> request, final Data path) {
        final DataGetParams params;
        try {
            params = DataGetParams.of(request.queryParameters());
        } catch (IllegalArgumentException e) {
            request.completeWith(new RequestException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Invalid GET /data parameters", e));
            return;
        }
        data().getData(request, path, params);
    }

    @Override
    public final void dataOPTIONS(final ServerRequest<OptionsResult> request) {
        request.completeWith(isNonConfigContent(request) ? OptionsResult.READ_ONLY : OptionsResult.DATASTORE);
    }

    @Override
    public final void dataOPTIONS(final ServerRequest<OptionsResult> request, final ApiPath apiPath) {
        final InstanceRequest path;
        try {
            path = pathNormalizer.normalizeDataOrActionPath(apiPath);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        request.completeWith(switch (path) {
            case Action action -> OptionsResult.ACTION;
            case Data data -> isNonConfigContent(request) ? OptionsResult.READ_ONLY : OptionsResult.RESOURCE;
        });
    }

    private static boolean isNonConfigContent(final ServerRequest<?> request) {
        return ContentParam.NONCONFIG.paramValue().equals(request.queryParameters().lookup(ContentParam.uriName));
    }

    @Override
    public final void dataPATCH(final ServerRequest<DataPatchResult> request, final ResourceBody body) {
        dataPATCH(request, emptyPath, body);
    }

    @Override
    public final void dataPATCH(final ServerRequest<DataPatchResult> request, final ApiPath apiPath,
            final ResourceBody body) {
        final Data path;
        try {
            path = pathNormalizer.normalizeDataPath(apiPath);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        dataPATCH(request, path, body);
    }

    private void dataPATCH(final ServerRequest<DataPatchResult> request, final Data path, final ResourceBody body) {
        final NormalizedNode data;
        try {
            data = body.toNormalizedNode(path);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        data().mergeData(request, path, data);
    }

    @Override
    public final void dataPATCH(final ServerRequest<DataYangPatchResult> request, final PatchBody body) {
        dataPATCH(request, emptyPath, body);
    }

    @Override
    public final void dataPATCH(final ServerRequest<DataYangPatchResult> request, final ApiPath apiPath,
            final PatchBody body) {
        final Data path;
        try {
            path = pathNormalizer.normalizeDataPath(apiPath);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        dataPATCH(request, path, body);
    }

    private void dataPATCH(final ServerRequest<DataYangPatchResult> request, final Data path, final PatchBody body) {
        final PatchContext patch;
        try {
            patch = body.toPatchContext(new DefaultResourceContext(path));
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        data().patchData(request, path, patch);
    }

    @Override
    public final void dataPOST(final ServerRequest<? super CreateResourceResult> request, final ChildBody body) {
        dataCreate(request, emptyPath, body);
    }

    @Override
    public final void dataPOST(final ServerRequest<DataPostResult> request, final ApiPath path,
            final DataPostBody body) {
        final InstanceRequest ref;
        try {
            ref = pathNormalizer.normalizeDataOrActionPath(path);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }

        switch (ref) {
            case Action actionPath -> {
                try (var inputBody = body.toOperationInput()) {
                    dataInvoke(request, actionPath, inputBody);
                }
            }
            case Data dataPath -> {
                try (var resourceBody = body.toResource()) {
                    dataCreate(request, dataPath, resourceBody);
                }
            }
        }
    }

    private void dataCreate(final ServerRequest<? super CreateResourceResult> request, final Data path,
            final ChildBody body) {
        final Insert insert;
        try {
            insert = Insert.of(path.databind(), request.queryParameters());
        } catch (IllegalArgumentException e) {
            request.completeWith(new RequestException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e));
            return;
        }
        final PrefixAndBody payload;
        try {
            payload = body.toPayload(path);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }

        if (insert != null) {
            data().createData(request, path, insert, payload);
        } else {
            data().createData(request, path, payload);
        }
    }

    @NonNullByDefault
    private void dataInvoke(final ServerRequest<? super InvokeResult> request, final Action path,
            final OperationInputBody body) {
        final ContainerNode input;
        try {
            input = body.toContainerNode(path);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        action().invokeAction(request, path, input);
    }

    @Override
    public final void dataPUT(final ServerRequest<DataPutResult> request, final ResourceBody body) {
        dataPUT(request, emptyPath, body);
    }

    @Override
    public final void dataPUT(final ServerRequest<DataPutResult> request, final ApiPath apiPath,
            final ResourceBody body) {
        final Data path;
        try {
            path = pathNormalizer.normalizeDataPath(apiPath);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        dataPUT(request, path, body);
    }

    @NonNullByDefault
    private void dataPUT(final ServerRequest<DataPutResult> request, final Data path, final ResourceBody body) {
        final Insert insert;
        try {
            insert = Insert.of(databind, request.queryParameters());
        } catch (IllegalArgumentException e) {
            request.completeWith(new RequestException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e));
            return;
        }
        final NormalizedNode data;
        try {
            data = body.toNormalizedNode(path);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }

        if (insert != null) {
            data().putData(request, path, insert, data);
        } else {
            data().putData(request, path, data);
        }
    }

    @Override
    public final void operationsGET(final ServerRequest<FormattableBody> request) {
        operations.httpGET(request);
    }

    @Override
    public final void operationsGET(final ServerRequest<FormattableBody> request, final ApiPath operation) {
        operations.httpGET(request, operation);
    }

    @Override
    public final void operationsOPTIONS(final ServerRequest<OptionsResult> request, final ApiPath operation) {
        try {
            pathNormalizer.normalizeRpcPath(operation);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        // RFC8040 is not consistent on this point:
        // - section 3.3.2 states:
        //        The access point for each RPC operation is represented as an empty
        //        leaf.  If an operation resource is retrieved, the empty leaf
        //        representation is returned by the server.
        // - section 4.3 states:
        //        The RESTCONF server MUST support the GET method.  The GET method is
        //        sent by the client to retrieve data and metadata for a resource.  It
        //        is supported for all resource types, except operation resources.
        // We implement the former, as it seems to be more in-line with the original intent, whereas we take the latter
        // to apply to /data only.
        request.completeWith(OptionsResult.RPC);
    }

    @Override
    public final void operationsPOST(final ServerRequest<InvokeResult> request, final URI restconfURI,
            final ApiPath apiPath, final OperationInputBody body) {
        final Rpc path;
        try {
            path = pathNormalizer.normalizeRpcPath(apiPath);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }

        final ContainerNode input;
        try {
            input = body.toContainerNode(path);
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        rpc().invokeRpc(request, restconfURI, path, input);
    }

    @Override
    public final void modulesGET(final ServerRequest<ModulesGetResult> request, final SourceIdentifier source,
            final Class<? extends SourceRepresentation> representation) {
        modules().getModelSource(request, source, representation);
    }

    @Override
    public final StrategyAndPath resolveStrategy(final ApiPath path) throws RequestException {
        var mount = path.indexOf("yang-ext", "mount");
        return mount == -1 ? new StrategyAndPath(this, path)
            : resolver().resolveMountPoint(pathNormalizer.normalizeDataPath(path.subPath(0, mount)))
                .resolveStrategy(path.subPath(mount + 1));
    }
}
