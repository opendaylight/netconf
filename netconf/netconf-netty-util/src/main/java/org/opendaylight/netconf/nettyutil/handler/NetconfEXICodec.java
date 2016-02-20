/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil.handler;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.siemens.ct.exi.EXIFactory;
import com.siemens.ct.exi.api.sax.SAXEncoder;
import com.siemens.ct.exi.api.sax.SAXFactory;
import com.siemens.ct.exi.exceptions.EXIException;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

public final class NetconfEXICodec {
    /**
     * OpenEXI does not allow us to directly prevent resolution of external entities. In order
     * to prevent XXE attacks, we reuse a single no-op entity resolver.
     */
    private static final EntityResolver ENTITY_RESOLVER = new EntityResolver() {
        @Override
        public InputSource resolveEntity(final String publicId, final String systemId) {
            return new InputSource();
        }
    };

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

    private final SAXFactory exiFactory;

    private NetconfEXICodec(final EXIFactory exiFactory) {
        this.exiFactory = new SAXFactory(Preconditions.checkNotNull(exiFactory));
    }

    public static NetconfEXICodec forParameters(final EXIParameters parameters) {
        return CODECS.getUnchecked(parameters);
    }

    XMLReader getReader() throws EXIException {
        final XMLReader reader = exiFactory.createEXIReader();
        reader.setEntityResolver(ENTITY_RESOLVER);
        return reader;
    }

    SAXEncoder getWriter() throws EXIException {
        final SAXEncoder writer = exiFactory.createEXIWriter();
        return writer;
    }
}
