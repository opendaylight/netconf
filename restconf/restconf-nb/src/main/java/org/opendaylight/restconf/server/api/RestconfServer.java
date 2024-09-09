/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * An implementation of a RESTCONF server, implementing the
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3">RESTCONF API Resource</a>.
 */
@NonNullByDefault
public interface RestconfServer {
    /**
     * Delete a data resource.
     *
     * @param identifier resource identifier
     * @return A {@link RestconfFuture} of the operation
     */
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    RestconfFuture<Empty> dataDELETE(ServerRequest request, ApiPath identifier);

    /**
     * Return the content of the datastore.
     *
     * @param request {@link ServerRequest} for this request
     * @return A {@link RestconfFuture} of the {@link DataGetResult} content
     */
    RestconfFuture<DataGetResult> dataGET(ServerRequest request);

    /**
     * Return the content of a data resource.
     *
     * @param request {@link ServerRequest} for this request
     * @param identifier resource identifier
     * @return A {@link RestconfFuture} of the {@link DataGetResult} content
     */
    RestconfFuture<DataGetResult> dataGET(ServerRequest request, ApiPath identifier);

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param body data node for put to config DS
     * @return A {@link RestconfFuture} of the operation
     */
    RestconfFuture<DataPatchResult> dataPATCH(ServerRequest request, ResourceBody body);

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param identifier resource identifier
     * @param body data node for put to config DS
     * @return A {@link RestconfFuture} of the operation
     */
    RestconfFuture<DataPatchResult> dataPATCH(ServerRequest request, ApiPath identifier, ResourceBody body);

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param body YANG Patch body
     * @return A {@link RestconfFuture} of the {@link DataYangPatchResult} content
     */
    RestconfFuture<DataYangPatchResult> dataPATCH(ServerRequest request, PatchBody body);

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param identifier path to target
     * @param body YANG Patch body
     * @return A {@link RestconfFuture} of the {@link DataYangPatchResult} content
     */
    RestconfFuture<DataYangPatchResult> dataPATCH(ServerRequest request, ApiPath identifier, PatchBody body);

    RestconfFuture<CreateResourceResult> dataPOST(ServerRequest request, ChildBody body);

    /**
     * Create or invoke a operation, as described in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.4">RFC8040 section 4.4</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param identifier path to target
     * @param body body of the post request
     */
    RestconfFuture<? extends DataPostResult> dataPOST(ServerRequest request, ApiPath identifier, DataPostBody body);

    /**
     * Replace the data store, as described in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 section 4.5</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param body data node for put to config DS
     * @return A {@link RestconfFuture} completing with {@link DataPutResult}
     */
    RestconfFuture<DataPutResult> dataPUT(ServerRequest request, ResourceBody body);

    /**
     * Create or replace a data store resource, as described in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 section 4.5</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param identifier resource identifier
     * @param body data node for put to config DS
     * @return A {@link RestconfFuture} completing with {@link DataPutResult}
     */
    RestconfFuture<DataPutResult> dataPUT(ServerRequest request, ApiPath identifier, ResourceBody body);

    /**
     * Return the set of supported RPCs supported by
     * {@link #operationsPOST(ServerRequest, URI, ApiPath, OperationInputBody)},
     * as expressed in the <a href="https://www.rfc-editor.org/rfc/rfc8040#page-84">ietf-restconf.yang</a>
     * {@code container operations} statement.
     *
     * @param request {@link ServerRequest} for this request
     * @return A {@link RestconfFuture} completing with an {@link FormattableBody}
     */
    RestconfFuture<FormattableBody> operationsGET(ServerRequest request);

    /*
     * Return the details about a particular operation supported by
     * {@link #operationsPOST(URI, String, OperationInputBody)}, as expressed in the
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#page-84">ietf-restconf.yang</a>
     * {@code container operations} statement.
     *
     * @param request {@link ServerRequest} for this request
     * @param operation An operation
     * @return A {@link RestconfFuture} completing with an {@link FormattableBody}
     */
    RestconfFuture<FormattableBody> operationsGET(ServerRequest request, ApiPath operation);

    /**
     * Invoke an RPC operation, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.6">RFC8040 Operation Resource</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param restconfURI Base URI of the request, the absolute equivalent to {@code {+restconf}} URI with a trailing
     *                    slash
     * @param operation {@code <operation>} path, really an {@link ApiPath} to an {@code rpc}
     * @param body RPC operation
     * @return A {@link RestconfFuture} completing with {@link InvokeResult}
     */
    // FIXME: 'operation' should really be an ApiIdentifier with non-null module, but we also support yang-ext:mount,
    //        and hence it is a path right now
    RestconfFuture<InvokeResult> operationsPOST(ServerRequest request, URI restconfURI, ApiPath operation,
        OperationInputBody body);

    /**
     * Return the revision of {@code ietf-yang-library} module implemented by this server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.3">RFC8040 {+restconf}/yang-library-version</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @return A {@link RestconfFuture} completing with {@link FormattableBody} containing a single
     *        {@code yang-library-version} leaf element.
     */
    RestconfFuture<FormattableBody> yangLibraryVersionGET(ServerRequest request);

    RestconfFuture<ModulesGetResult> modulesYangGET(ServerRequest request, String fileName, @Nullable String revision);

    RestconfFuture<ModulesGetResult> modulesYangGET(ServerRequest request, ApiPath mountPath, String fileName,
        @Nullable String revision);

    RestconfFuture<ModulesGetResult> modulesYinGET(ServerRequest request, String fileName, @Nullable String revision);

    RestconfFuture<ModulesGetResult> modulesYinGET(ServerRequest request, ApiPath mountPath, String fileName,
        @Nullable String revision);
}
