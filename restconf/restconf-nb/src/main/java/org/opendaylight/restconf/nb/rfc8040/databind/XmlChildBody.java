/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import java.io.InputStream;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

public final class XmlChildBody extends ChildBody {
    public XmlChildBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    PrefixAndBody toPayload(final InputStream inputStream, final Inference inference) {
        // TODO Auto-generated method stub
        return null;
    }
}
