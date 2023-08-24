/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class OpenApiInputStream extends InputStream implements Iterator<JsonNode> {

    @Override
    public int read() throws IOException {
        return 0;
    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public JsonNode next() {
        return null;
    }
}
