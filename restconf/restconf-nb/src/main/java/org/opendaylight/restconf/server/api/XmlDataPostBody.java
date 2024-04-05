/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public final class XmlDataPostBody extends DataPostBody {
    public XmlDataPostBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    public XmlOperationInputBody toOperationInput() {
        return new XmlOperationInputBody(consume());
    }

    @Override
    public XmlChildBody toResource() {
        return new XmlChildBody(consume());
    }
}
