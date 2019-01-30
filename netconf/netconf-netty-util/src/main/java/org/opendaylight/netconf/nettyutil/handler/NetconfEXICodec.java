/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static java.util.Objects.requireNonNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.netconf.shaded.exificient.core.EXIFactory;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.EXIException;
import org.opendaylight.netconf.shaded.exificient.main.api.sax.SAXEncoder;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

public final class NetconfEXICodec {
    /**
     * OpenEXI does not allow us to directly prevent resolution of external entities. In order
     * to prevent XXE attacks, we reuse a single no-op entity resolver.
     */
    private static final EntityResolver ENTITY_RESOLVER = (publicId, systemId) -> new InputSource();

    /**
     * Since we have a limited number of options we can have, instantiating a weak cache
     * will allow us to reuse instances where possible.
     */
    private static final LoadingCache<EXIParameters, NetconfEXICodec> CODECS =
            CacheBuilder.newBuilder().weakValues().build(new CacheLoader<EXIParameters, NetconfEXICodec>() {
                @Override
                public NetconfEXICodec load(final EXIParameters key) {
                    return new NetconfEXICodec(key.getFactory());
                }
            });

    private final ThreadLocalSAXFactory exiFactory;

    private NetconfEXICodec(final EXIFactory exiFactory) {
        this.exiFactory = new ThreadLocalSAXFactory(requireNonNull(exiFactory));
    }

    public static NetconfEXICodec forParameters(final EXIParameters parameters) {
        return CODECS.getUnchecked(parameters);
    }

    ThreadLocalSAXDecoder getReader() throws EXIException {
        final ThreadLocalSAXDecoder reader = exiFactory.createEXIReader();
        reader.setEntityResolver(ENTITY_RESOLVER);
        return reader;
    }

    SAXEncoder getWriter() throws EXIException {
        return exiFactory.createEXIWriter();
    }
}
