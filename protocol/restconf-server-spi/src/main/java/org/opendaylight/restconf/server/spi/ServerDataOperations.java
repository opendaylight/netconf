/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.restconf.server.api.ChildBody.PrefixAndBody;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.PatchContext;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * A concrete implementation of RESTCONF datastore resource, as specified by
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.1">RFC8040 {+restconf}/data</a>, based on
 * {@code yang-data-api}.
 */
@NonNullByDefault
public interface ServerDataOperations {
    /**
     * Create a resource, as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1">RFC8040 Create Resource Mode</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param path resource path
     * @param data resource data
     */
    void createData(ServerRequest<? super CreateResourceResult> request, Data path, PrefixAndBody data);

    /**
     * Create a resource, as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1">RFC8040 Create Resource Mode</a>, at specified
     * insertion point.
     *
     * @param request {@link ServerRequest} for this request
     * @param path resource path
     * @param insert {@link Insert} parameter
     * @param data resource data
     */
    void createData(ServerRequest<? super CreateResourceResult> request, Data path, Insert insert, PrefixAndBody data);

    /**
     * Delete a resource, as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.7">RFC8040 DELETE</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param path resource path
     */
    void deleteData(ServerRequest<Empty> request, Data path);

    /**
     * Get the contents of a resource, as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.3">RFC8040 GET</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param path resource path
     * @param params operation parameters
     */
    @Beta
    void getData(ServerRequest<DataGetResult> request, Data path, DataGetParams params);

    /**
     * Merge the contents of a resource, as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040 Plain Patch</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param path resource path
     * @param data resource data
     */
    void mergeData(ServerRequest<DataPatchResult> request, Data path, NormalizedNode data);

    /**
     * Patch a resource, as specified by <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6">RFC8040 PATH</a>
     * and <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2">RFC8072 YANG Patch</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param path resource path
     * @param patch {@link PatchContext} to be processed
     */
    void patchData(ServerRequest<DataYangPatchResult> request, Data path, PatchContext patch);

    /**
     * Create or replace a resource, as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 PUT</a>.
     *
     * @param request {@link ServerRequest} for this request
     * @param path path of data
     * @param data data
     */
    void putData(ServerRequest<DataPutResult> request, Data path, NormalizedNode data);

    /**
     * Create or replace a resource, as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 PUT</a>, at specified insertion point.
     *
     * @param request {@link ServerRequest} for this request
     * @param path path of data
     * @param insert {@link Insert} parameter
     * @param data data
     */
    void putData(ServerRequest<DataPutResult> request, Data path, Insert insert, NormalizedNode data);
}
