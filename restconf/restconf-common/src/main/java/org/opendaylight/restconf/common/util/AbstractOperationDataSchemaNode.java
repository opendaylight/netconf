/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.util;

import java.util.Optional;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Status;
import org.opendaylight.yangtools.yang.xpath.api.YangXPathExpression.QualifiedBound;

abstract class AbstractOperationDataSchemaNode implements DataSchemaNode {
    @Override
    public final Status getStatus() {
        return Status.CURRENT;
    }

    @Override
    public final boolean isConfiguration() {
        return false;
    }

    @Override
    @Deprecated
    public final boolean isAugmenting() {
        return false;
    }

    @Override
    @Deprecated
    public final boolean isAddedByUses() {
        return false;
    }

    @Override
    public final Optional<String> getDescription() {
        return Optional.empty();
    }

    @Override
    public final Optional<String> getReference() {
        return Optional.empty();
    }

    @Override
    public final Optional<? extends QualifiedBound> getWhenCondition() {
        return Optional.empty();
    }
}
