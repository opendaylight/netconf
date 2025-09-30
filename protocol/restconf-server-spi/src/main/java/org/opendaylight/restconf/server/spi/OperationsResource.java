/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindPath.Rpc;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;

/**
 * RESTCONF {@code /operations} content for a {@code GET} operation as per
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.2">RFC8040</a>.
 */
@NonNullByDefault
public final class OperationsResource implements HttpGetResource {
    private final ApiPathNormalizer pathNormalizer;

    public OperationsResource(final ApiPathNormalizer pathNormalizer) {
        this.pathNormalizer = requireNonNull(pathNormalizer);
    }

    @Override
    public void httpGET(final ServerRequest<FormattableBody> request) {
        // RPC QNames by their XMLNamespace/Revision. This should be a Table, but Revision can be null, which wrecks us.
        final var table = new HashMap<XMLNamespace, Map<Revision, ImmutableSet<QName>>>();
        final var modelContext = pathNormalizer.databind().modelContext();
        for (var entry :  modelContext.getModuleStatements().entrySet()) {
            final var module = entry.getValue();
            final var rpcNames = module.streamEffectiveSubstatements(RpcEffectiveStatement.class)
                .map(RpcEffectiveStatement::argument)
                .collect(ImmutableSet.toImmutableSet());
            if (!rpcNames.isEmpty()) {
                final var namespace = entry.getKey();
                table.computeIfAbsent(namespace.namespace(), ignored -> new HashMap<>())
                    .put(namespace.revision(), rpcNames);
            }
        }

        // Now pick the latest revision for each namespace
        final var rpcs = ImmutableSetMultimap.<QNameModule, QName>builder();
        for (var entry : table.entrySet()) {
            entry.getValue().entrySet().stream()
                .sorted(Comparator.comparing(Entry::getKey, (first, second) -> Revision.compare(second, first)))
                .findFirst()
                .ifPresent(row -> rpcs.putAll(QNameModule.ofRevision(entry.getKey(), row.getKey()), row.getValue()));
        }
        request.completeWith(new AllOperations(modelContext, rpcs.build()));
    }

    @Override
    public void httpGET(final ServerRequest<FormattableBody> request, final ApiPath apiPath) {
        final Rpc path;
        try {
            path = pathNormalizer.normalizeRpcPath(apiPath);
        } catch (RequestException e) {
            request.failWith(e);
            return;
        }
        request.completeWith(new OneOperation(path.inference().modelContext(), path.statement().argument()));
    }
}
