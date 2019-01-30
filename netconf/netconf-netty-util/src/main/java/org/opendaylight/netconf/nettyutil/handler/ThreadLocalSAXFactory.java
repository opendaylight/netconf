/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import org.opendaylight.netconf.shaded.exificient.core.EXIFactory;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.EXIException;
import org.opendaylight.netconf.shaded.exificient.main.api.sax.SAXFactory;

/**
 * A SAXFactory which hands out {@link ThreadLocalSAXDecoder}s.
 */
final class ThreadLocalSAXFactory extends SAXFactory {
    ThreadLocalSAXFactory(final EXIFactory exiFactory) {
        super(exiFactory);
    }

    @Override
    public ThreadLocalSAXDecoder createEXIReader() throws EXIException {
        return new ThreadLocalSAXDecoder(exiFactory);
    }
}
