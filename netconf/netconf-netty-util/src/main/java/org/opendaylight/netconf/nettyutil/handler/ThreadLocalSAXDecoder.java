/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import java.io.IOException;
import org.opendaylight.netconf.shaded.exificient.core.EXIFactory;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.EXIException;
import org.opendaylight.netconf.shaded.exificient.main.api.sax.SAXDecoder;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Utility SAXDecoder, which reuses a thread-local buffer during parse operations.
 */
final class ThreadLocalSAXDecoder extends SAXDecoder {
    // Note these limits are number of chars, i.e. 2 bytes
    private static final int INITIAL_SIZE = 4096;
    private static final int CACHE_MAX_SIZE = 32768;
    private static final ThreadLocal<char[]> CBUFFER_CACHE = ThreadLocal.withInitial(() -> new char[INITIAL_SIZE]);

    ThreadLocalSAXDecoder(final EXIFactory noOptionsFactory) throws EXIException {
        super(noOptionsFactory, null);
    }

    @Override
    public void parse(final InputSource source) throws IOException, SAXException {
        try {
            cbuffer = CBUFFER_CACHE.get();
            super.parse(source);
        } finally {
            if (cbuffer.length <= CACHE_MAX_SIZE) {
                CBUFFER_CACHE.set(cbuffer);
            }
            cbuffer = null;
        }
    }
}
