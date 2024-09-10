/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.api;

import com.google.common.collect.ImmutableCollection;
import java.io.IOException;
import java.net.URI;
import org.opendaylight.restconf.openapi.model.DocumentEntity;
import org.opendaylight.restconf.openapi.model.MetadataEntity;
import org.opendaylight.restconf.openapi.model.MountPointsEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * This service generates swagger (See <a
 * href="https://helloreverb.com/developers/swagger"
 * >https://helloreverb.com/developers/swagger</a>) compliant documentation for
 * RESTCONF APIs. The output of this is used by embedded Swagger UI.
 */
public interface OpenApiService {
    /**
     * Generate OpenAPI specification document. Generate OpenAPI specification document for modules of controller
     * model context starting with a module with index specified by {@code offset}. The resulting documentation contains
     * as many modules as defined by {@code limit}.
     *
     * <p>If user wishes to get document for all modules in controller's model context then value 0 should be used
     * for both {@code offset} and {@code limit}.
     *
     * <p>We relly on {@link EffectiveModelContext} usage of {@link ImmutableCollection} which preserves iteration
     * order, so we are able to read first 40 modules with {@code ?offset=0&limit=20} and consequent request with
     * parameters {@code ?offset=20&limit=20}.
     *
     * <p>If user uses value out of range for {@code offset} which is greater than number of modules in mount point
     * schema or negative value, the response will contain empty OpenAPI document. Same if user uses negative value for
     * {@code limit}.
     *
     * @param uri Requests {@link URI}.
     * @param width Width is the number of child nodes processed for each module/node. This means that for example with
     *      width=3 we will process schema only for first 3 nodes in each module and each node that we process after.
     *      Value set to 0 or lesser means ignore width and to process all child nodes of a YANG module.
     * @param depth Depth to which the OpenAPI document is generated, the number of levels of the module that are
     *      processed in depth. For example, depth=1 means that the module will be processed with its children, but
     *      their children will be ignored. Value set to 0 or lesser means ignore depth and to process all child nodes
     *      of a YANG module.
     * @param offset First model to read. 0 means read from the first model.
     * @param limit The number of models to read. 0 means read all models.
     * @return the OpenAPI document for number of modules specified by {@code offset} and {@code limit}, with number
     *         child nodes specified by {@code width}.
     * @throws IOException When I/O error occurs.
     */
    DocumentEntity getAllModulesDoc(URI uri, Integer width, Integer depth, Integer offset, Integer limit)
        throws IOException;

    /**
     * Generate a metadata document for all or paginated modules of the controller schema context.
     *
     * <p>Generates a metadata document for modules of the controller schema context. The resulting metadata will
     * contain information about all modules in the controller's model context if both {@code offset} and {@code limit}
     * are set to 0.
     *
     * <p>Generate metadata document for modules of controller schema context with specified index and limit. The
     * resulting metadata provides additional information about actual page and number of pages.
     *
     * @param offset First model to read. 0 means read from the first model.
     * @param limit The number of models to read. 0 means read all models.
     * @return Response containing the metadata document for ui implementation of pagination.
     * @throws IOException When I/O error occurs.
     */
    MetadataEntity getAllModulesMeta(Integer offset, Integer limit) throws IOException;

    /**
     * Generates Swagger compliant document listing APIs for module.
     */
    DocumentEntity getDocByModule(String module, String revision, URI uri, Integer width, Integer depth)
        throws IOException;

    /**
     * Generates index document for Swagger UI. This document lists out all modules with link to get APIs for each
     * module. The API for each module is served by <code> getDocByModule()</code> method.
     */
    MountPointsEntity getListOfMounts();

    /**
     * Generate OpenAPI specification document listing APIs for module.
     *
     * @param uri Requests {@link URI}.
     * @param width Width is the number of child nodes processed for each module/node. This means that for example with
     *      width=3 we will process schema only for first 3 nodes in each module and each node that we process after.
     *      Value set to 0 or lesser means ignore width and to process all child nodes of a YANG module.
     * @param depth Depth to which the OpenAPI document is generated, the number of levels of the module that are
     *      processed in depth. For example, depth=1 means that the module will be processed with it's children, but
     *      their children will be ignored. Value set to 0 or lesser means ignore depth and to process all child nodes
     *      of a YANG module.
     * @return the OpenAPI document for all modules with number child nodes specified by {@code width}.
     * @throws IOException When I/O error occurs.
     */
    DocumentEntity getMountDocByModule(long instanceNum, String module, String revision, URI uri, Integer width,
        Integer depth) throws IOException;

    /**
     * Generate OpenAPI specification document listing APIs for all modules of mount point. Generates OpenAPI
     * specification document listing APIs for all modules of mount point if value 0 is used for both {@code offset}
     * and {@code limit}.
     *
     * <p>Generate OpenAPI specification document for modules of mount point schema context starting with a module with
     * index specified by {@code offset}. The resulting documentation contains as many modules as defined
     * by {@code limit}.
     *
     * <p>We rely on {@link EffectiveModelContext} usage of {@link ImmutableCollection} which preserves iteration order,
     * so we are able to read first 40 modules with {@code ?offset=0&limit=20} and consequent request with parameters
     * {@code ?offset=20&limit=20}.
     *
     * <p>If user uses value out of range for {@code offset} which is greater than number of modules in mount point
     * schema or negative value, the response will contain empty OpenAPI document. Same if user uses negative value for
     * {@code limit}.
     *
     * @param instanceNum Instance number of the mount point.
     * @param uri Requests {@link URI}.
     * @param width Width is the number of child nodes processed for each module/node. This means that for example with
     *      width=3 we will process schema only for first 3 nodes in each module and each node that we process after.
     *      Value set to 0 or lesser means ignore width and to process all child nodes of a YANG module.
     * @param depth Depth to which the OpenAPI document is generated, the number of levels of the module that are
     *      processed in depth. For example, depth=1 means that the module will be processed with its children, but
     *      their children will be ignored. Value set to 0 or lesser means ignore depth and to process all child nodes
     *      of a YANG module.
     * @param offset First model to read. 0 means read from the first model.
     * @param limit The number of models to read. 0 means read all models.
     * @return the OpenAPI document for number of modules specified by {@code offset} and {@code limit} with number
     *         child nodes specified by {@code width}.
     * @throws IOException When I/O error occurs.
     */
    DocumentEntity getMountDoc(long instanceNum, URI uri, Integer width, Integer depth, Integer offset,
        Integer limit) throws IOException;

    /**
     * Generate a metadata document for all or paginated modules of the mount point schema context.
     *
     * <p>Generates a metadata document for modules of the mount point schema context. The resulting metadata will
     * contain information about all modules in the controller's model context if both {@code offset} and {@code limit}
     * are set to 0.
     *
     * <p>Generate metadata document for modules of mount point schema context with specified index and limit. The
     * resulting metadata provides additional information about the actual page and number of pages.
     *
     * @param offset First model to read. 0 means read from the first model.
     * @param limit The number of models to read. 0 means read all models.
     * @return Response containing the metadata document for ui implementation of pagination.
     * @throws IOException When I/O error occurs.
     */
    MetadataEntity getMountMeta(long instanceNum, Integer offset, Integer limit) throws IOException;
}
