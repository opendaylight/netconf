/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.schema;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.opendaylight.restconfsb.communicator.impl.sender.NodeConnectionException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SchemaContextResolver creates {@link SchemaContext} from yang sources stored in distribution cache/schema/ directory.
 */
public class SchemaContextResolver {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaContextResolver.class);
    private static DirectorySchemaContextCache cache;

    public SchemaContextResolver(final DirectorySchemaContextCache cache) {
        this.cache = cache;
    }

    /**
     * Tries to build {@link SchemaContext} from sources stored in a cache/schema/ directory. If there are errors, method
     * tries to build context from the biggest possible subset of available sources.
     *
     * @param requiredModules
     * @return
     * @throws NodeConnectionException
     */
    public SchemaContext createSchemaContext(final List<Module> requiredModules) throws NodeConnectionException {
        Collection<SourceIdentifier> requiredSources = Collections2.transform(requiredModules, new Function<Module, SourceIdentifier>() {
            @Nullable
            @Override
            public SourceIdentifier apply(final Module input) {
                return new SourceIdentifier(input.getName().getValue(), input.getRevision().getValue());
            }
        });
        while (!requiredSources.isEmpty()) {
            LOG.trace("{}: Trying to build schema context from {}", requiredSources);
            try {
                final SchemaContext result = cache.createSchemaContext(requiredSources);
                LOG.debug("{}: Schema context built successfully from {}", requiredSources);
                return result;
            } catch (final SchemaResolutionException e) {
                // schemaBuilderFuture.checkedGet() throws only SchemaResolutionException
                // that might be wrapping a MissingSchemaSourceException so we need to look
                // at the cause of the exception to make sure we don't misinterpret it.
                if (e.getCause() instanceof MissingSchemaSourceException) {
                    requiredSources = handleMissingSchemaSourceException(requiredSources, (MissingSchemaSourceException) e.getCause());
                    continue;
                }
                requiredSources = handleSchemaResolutionException(e);
            } catch (final Exception e) {
                // unknown error, fail
                throw new NodeConnectionException(e);
            }
        }
        throw new NodeConnectionException("No more sources");
    }

    private Collection<SourceIdentifier> handleMissingSchemaSourceException(final Collection<SourceIdentifier> requiredSources,
                                                                            final MissingSchemaSourceException t) {
        // In case source missing, try without it
        final SourceIdentifier missingSource = t.getSourceId();
        LOG.warn("{}: Unable to build schema context, missing source {}, will reattempt without it", missingSource);
        LOG.debug("{}: Unable to build schema context, missing source {}, will reattempt without it", t);
        return stripMissingSource(requiredSources, missingSource);
    }

    private Collection<SourceIdentifier> handleSchemaResolutionException(final SchemaResolutionException resolutionException) {
        // In case resolution error, try only with resolved sources
        LOG.warn("Unable to build schema context, unsatisfied imports {}, will reattempt with resolved only", resolutionException.getUnsatisfiedImports());
        LOG.debug("Unable to build schema context, unsatisfied imports {}, will reattempt with resolved only", resolutionException);
        return resolutionException.getResolvedSources();
    }

    private Collection<SourceIdentifier> stripMissingSource(final Collection<SourceIdentifier> requiredSources, final SourceIdentifier sIdToRemove) {
        final List<SourceIdentifier> sourceIdentifiers = Lists.newLinkedList(requiredSources);
        final boolean removed = sourceIdentifiers.remove(sIdToRemove);
        Preconditions.checkState(removed, "Trying to remove {} from {} failed", sIdToRemove, requiredSources);
        return sourceIdentifiers;
    }

}
