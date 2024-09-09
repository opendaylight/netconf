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
     * @param request {@link ServerRequest} for this request
     * @param identifier resource identifier
     */
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    void dataDELETE(ServerRequest<Empty> request, ApiPath identifier);

    /**
     * Return the content of the datastore.
     *
     * @param request {@link ServerRequest} for this request
     */
    void dataGET(ServerRequest<DataGetResult> request);

    /**
     * Return the content of a data resource.
     *
     * @param request {@link ServerRequest} for this request
     * @param identifier resource identifier
     */
    void dataGET(ServerRequest<DataGetResult> request, ApiPath identifier);

    /**
     * Return the HTTP methods supported by the target data resource.
     *
     * @param request {@link ServerRequest} for this request
     * @param identifier resource identifier
     */
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    void dataOPTIONS(ServerRequest<OptionsResult> request, ApiPath identifier);

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param body data node for put to config DS
     */
    void dataPATCH(ServerRequest<DataPatchResult> request, ResourceBody body);

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param identifier resource identifier
     * @param body data node for put to config DS
     */
    void dataPATCH(ServerRequest<DataPatchResult> request, ApiPath identifier, ResourceBody body);

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param body YANG Patch body
     */
    void dataPATCH(ServerRequest<DataYangPatchResult> request, PatchBody body);

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param identifier path to target
     * @param body YANG Patch body
     */
    void dataPATCH(ServerRequest<DataYangPatchResult> request, ApiPath identifier, PatchBody body);

    void dataPOST(ServerRequest<CreateResourceResult> request, ChildBody body);

    /**
     * Create or invoke a operation, as described in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.4">RFC8040 section 4.4</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param identifier path to target
     * @param body body of the post request
     */
    void dataPOST(ServerRequest<DataPostResult> request, ApiPath identifier, DataPostBody body);

    /**
     * Replace the data store, as described in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 section 4.5</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param body data node for put to config DS
     */
    void dataPUT(ServerRequest<DataPutResult> request, ResourceBody body);

    /**
     * Create or replace a data store resource, as described in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 section 4.5</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param identifier resource identifier
     * @param body data node for put to config DS
     */
    void dataPUT(ServerRequest<DataPutResult> request, ApiPath identifier, ResourceBody body);

    /**
     * Return the set of supported RPCs supported by
     * {@link #operationsPOST(ServerRequest, URI, ApiPath, OperationInputBody)},
     * as expressed in the <a href="https://www.rfc-editor.org/rfc/rfc8040#page-84">ietf-restconf.yang</a>
     * {@code container operations} statement.
     *
     * @param request {@link ServerRequest} for this request
     */
    void operationsGET(ServerRequest<FormattableBody> request);

    /*
     * Return the details about a particular operation supported by
     * {@link #operationsPOST(URI, String, OperationInputBody)}, as expressed in the
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#page-84">ietf-restconf.yang</a>
     * {@code container operations} statement.
     *
     * @param request {@link ServerRequest} for this request
     * @param operation An operation
     */
    void operationsGET(ServerRequest<FormattableBody> request, ApiPath operation);

    /**
     * Return the HTTP methods supported by the target data resource.
     *
     * @param request {@link ServerRequest} for this request
     * @param operation An operation
     */
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    void operationsOPTIONS(ServerRequest<OptionsResult> request, ApiPath operation);

    /**
     * Invoke an RPC operation, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.6">RFC8040 Operation Resource</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param restconfURI Base URI of the request, the absolute equivalent to {@code {+restconf}} URI with a trailing
     *                    slash
     * @param operation {@code <operation>} path, really an {@link ApiPath} to an {@code rpc}
     * @param body RPC operation
     */
    // FIXME: 'operation' should really be an ApiIdentifier with non-null module, but we also support yang-ext:mount,
    //        and hence it is a path right now
    void operationsPOST(ServerRequest<InvokeResult> request, URI restconfURI, ApiPath operation,
        OperationInputBody body);

    /**
     * Return the revision of {@code ietf-yang-library} module implemented by this server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.3">RFC8040 {+restconf}/yang-library-version</a>.
     *
     * @param request {@link ServerRequest} for this request
     */
    void yangLibraryVersionGET(ServerRequest<FormattableBody> request);

    void modulesYangGET(ServerRequest<ModulesGetResult> request, String fileName, @Nullable String revision);

    void modulesYangGET(ServerRequest<ModulesGetResult> request, ApiPath mountPath, String fileName,
        @Nullable String revision);

    void modulesYinGET(ServerRequest<ModulesGetResult> request, String fileName, @Nullable String revision);

    void modulesYinGET(ServerRequest<ModulesGetResult> request, ApiPath mountPath, String fileName,
        @Nullable String revision);
}
