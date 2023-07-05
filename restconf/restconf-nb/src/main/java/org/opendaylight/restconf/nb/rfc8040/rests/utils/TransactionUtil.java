/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import java.util.ArrayList;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Util class for common methods of transactions.
 */
public final class TransactionUtil {
    private TransactionUtil() {
        // Hidden on purpose
    }

    /**
     * Merged parents of data.
     *
     * @param path          path of data
     * @param schemaContext {@link SchemaContext}
     * @param transaction   A handle to a set of DS operations
     */
    // FIXME: this method should only be invoked in MdsalRestconfStrategy, and even then only if we are crossing
    //        an implicit list.
    public static void ensureParentsByMerge(final YangInstanceIdentifier path,
                                            final EffectiveModelContext schemaContext,
                                            final RestconfTransaction transaction) {
        final var normalizedPathWithoutChildArgs = new ArrayList<PathArgument>();
        YangInstanceIdentifier rootNormalizedPath = null;

        final var it = path.getPathArguments().iterator();

        while (it.hasNext()) {
            final var pathArgument = it.next();
            if (rootNormalizedPath == null) {
                rootNormalizedPath = YangInstanceIdentifier.of(pathArgument);
            }

            if (it.hasNext()) {
                normalizedPathWithoutChildArgs.add(pathArgument);
            }
        }

        if (normalizedPathWithoutChildArgs.isEmpty()) {
            return;
        }

        transaction.merge(rootNormalizedPath,
            ImmutableNodes.fromInstanceId(schemaContext, YangInstanceIdentifier.of(normalizedPathWithoutChildArgs)));
    }
}
