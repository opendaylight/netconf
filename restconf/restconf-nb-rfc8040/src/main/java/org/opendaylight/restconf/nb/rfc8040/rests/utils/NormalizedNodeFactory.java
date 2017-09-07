/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.base.Optional;
import org.apache.commons.lang3.builder.Builder;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

class NormalizedNodeFactory extends FutureDataFactory<Optional<NormalizedNode<?, ?>>>
        implements Builder<NormalizedNode<?, ?>> {

    @Override
    public NormalizedNode<?, ?> build() {
        if (this.result.isPresent()) {
            return this.result.get();
        }
        return null;
    }

}
