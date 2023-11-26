/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.net.URI;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.databind.ChildBody;
import org.opendaylight.restconf.nb.rfc8040.databind.DataPostBody;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.PatchBody;
import org.opendaylight.restconf.nb.rfc8040.databind.ResourceBody;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.server.api.DataPostResult.CreateResource;
import org.opendaylight.restconf.server.spi.OperationOutput;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * An implementation of a RESTCONF server, implementing the
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3">RESTCONF API Resource</a>.
 */
// FIXME: configuration datastore should maintain ETag and Last-Modified headers, so that these can be returned when
//        GET/PATCH/POST/PUT modify the data.
// FIXME: NETCONF-773: as a first step in doing that we should carry those fields in our responses
@NonNullByDefault
public interface RestconfServer {
    /**
     * Delete a data resource.
     *
     * @param identifier resource identifier
     * @return A {@link RestconfFuture} of the operation
     */
    @SuppressWarnings("checkstyle:abbreviationAsWordInName")
    RestconfFuture<Empty> dataDELETE(ApiPath identifier);

    /**
     * Return the content of the datastore.
     *
     * @param readParams {@link ReadDataParams} for this request
     * @return A {@link RestconfFuture} of the {@link NormalizedNodePayload} content
     */
    RestconfFuture<NormalizedNodePayload> dataGET(ReadDataParams readParams);

    /**
     * Return the content of a data resource.
     *
     * @param identifier resource identifier
     * @param readParams {@link ReadDataParams} for this request
     * @return A {@link RestconfFuture} of the {@link NormalizedNodePayload} content
     */
    RestconfFuture<NormalizedNodePayload> dataGET(ApiPath identifier, ReadDataParams readParams);

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param body data node for put to config DS
     * @return A {@link RestconfFuture} of the operation
     */
    RestconfFuture<Empty> dataPATCH(ResourceBody body);

    /**
     * Partially modify the target data resource, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040, section 4.6.1</a>.
     *
     * @param identifier resource identifier
     * @param body data node for put to config DS
     * @return A {@link RestconfFuture} of the operation
     */
    RestconfFuture<Empty> dataPATCH(ApiPath identifier, ResourceBody body);

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param body YANG Patch body
     * @return A {@link RestconfFuture} of the {@link PatchStatusContext} content
     */
    RestconfFuture<PatchStatusContext> dataPATCH(PatchBody body);

    /**
     * Ordered list of edits that are applied to the datastore by the server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072, section 2</a>.
     *
     * @param identifier path to target
     * @param body YANG Patch body
     * @return A {@link RestconfFuture} of the {@link PatchStatusContext} content
     */
    RestconfFuture<PatchStatusContext> dataPATCH(ApiPath identifier, PatchBody body);

    RestconfFuture<CreateResource> dataPOST(ChildBody body, Map<String, String> queryParameters);

    RestconfFuture<? extends DataPostResult> dataPOST(ApiPath identifier, DataPostBody body,
        Map<String, String> queryParameters);

    /**
     * Replace the data store.
     *
     * @param body data node for put to config DS
     * @param queryParameters Query parameters
     * @return A {@link RestconfFuture} completing with {@link DataPutResult}
     */
    RestconfFuture<DataPutResult> dataPUT(ResourceBody body, Map<String, String> queryParameters);

    /**
     * Create or replace a data store resource.
     *
     * @param identifier resource identifier
     * @param body data node for put to config DS
     * @param queryParameters Query parameters
     * @return A {@link RestconfFuture} completing with {@link DataPutResult}
     */
    RestconfFuture<DataPutResult> dataPUT(ApiPath identifier, ResourceBody body, Map<String, String> queryParameters);

    /**
     * Return the set of supported RPCs supported by {@link #operationsPOST(URI, ApiPath, OperationInputBody)},
     * as expressed in the <a href="https://www.rfc-editor.org/rfc/rfc8040#page-84">ietf-restconf.yang</a>
     * {@code container operations} statement.
     *
     * @return A {@link RestconfFuture} completing with an {@link OperationsGetResult}
     */
    RestconfFuture<OperationsGetResult> operationsGET();

    /*
     * Return the details about a particular operation supported by
     * {@link #operationsPOST(URI, String, OperationInputBody)}, as expressed in the
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#page-84">ietf-restconfig.yang</a>
     * {@code container operations} statement.
     *
     * @param operation An operation
     * @return A {@link RestconfFuture} completing with an {@link OperationsGetResult}
     */
    RestconfFuture<OperationsGetResult> operationsGET(ApiPath operation);

    /**
     * Invoke an RPC operation, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.6">RFC8040 Operation Resource</a>.
     *
     * @param restconfURI Base URI of the request
     * @param operation {@code <operation>} path, really an {@link ApiPath} to an {@code rpc}
     * @param body RPC operation
     * @return A {@link RestconfFuture} completing with {@link OperationOutput}
     */
    // FIXME: 'operation' should really be an ApiIdentifier with non-null module, but we also support ang-ext:mount,
    //        and hence it is a path right now
    // FIXME: use ApiPath instead of String
    RestconfFuture<OperationOutput> operationsPOST(URI restconfURI, ApiPath operation, OperationInputBody body);

    /**
     * Return the revision of {@code ietf-yang-library} module implemented by this server, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.3">RFC8040 {+restconf}/yang-library-version</a>.
     *
     * @return A {@link RestconfFuture} completing with {@link NormalizedNodePayload} containing a single
     *        {@code yang-library-version} leaf element.
     */
    // FIXME: this is a simple encoding-variadic return, similar to how OperationsContent is handled use a common
    //        construct for both cases -- in this case it carries a yang.common.Revision
    RestconfFuture<NormalizedNodePayload> yangLibraryVersionGET();

    RestconfFuture<ModulesGetResult> modulesYangGET(String identifier);

    RestconfFuture<ModulesGetResult> modulesYinGET(String identifier);
}
