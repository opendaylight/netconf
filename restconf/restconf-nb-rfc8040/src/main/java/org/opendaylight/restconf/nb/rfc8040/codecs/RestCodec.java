/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.codecs;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.opendaylight.restconf.common.util.IdentityValuesDTO;
import org.opendaylight.restconf.common.util.IdentityValuesDTO.IdentityValue;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.codec.IdentityrefCodec;
import org.opendaylight.yangtools.yang.data.api.codec.LeafrefCodec;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: what is this class even trying to do?
public final class RestCodec {
    private static final Logger LOG = LoggerFactory.getLogger(RestCodec.class);

    private RestCodec() {
        // Hidden on purpose
    }

    public static class IdentityrefCodecImpl implements IdentityrefCodec<IdentityValuesDTO> {
        private static final Logger LOG = LoggerFactory.getLogger(IdentityrefCodecImpl.class);

        private final SchemaContext schemaContext;

        public IdentityrefCodecImpl(final SchemaContext schemaContext) {
            this.schemaContext = schemaContext;
        }

        @Override
        public IdentityValuesDTO serialize(final QName data) {
            return new IdentityValuesDTO(data.getNamespace().toString(), data.getLocalName(), null, null);
        }

        @Override
        @SuppressFBWarnings(value = "NP_NONNULL_RETURN_VIOLATION", justification = "Legacy return")
        public QName deserialize(final IdentityValuesDTO data) {
            final IdentityValue valueWithNamespace = data.getValuesWithNamespaces().get(0);
            final Module module = getModuleByNamespace(valueWithNamespace.getNamespace(), schemaContext);
            // FIXME: this needs to be a hard error
            if (module == null) {
                LOG.info("Module was not found for namespace {}", valueWithNamespace.getNamespace());
                LOG.info("Idenetityref will be translated as NULL for data - {}", String.valueOf(valueWithNamespace));
                return null;
            }

            return QName.create(module.getNamespace(), module.getRevision(), valueWithNamespace.getValue());
        }

    }

    public static class LeafrefCodecImpl implements LeafrefCodec<String> {

        @Override
        public String serialize(final Object data) {
            return String.valueOf(data);
        }

        @Override
        public Object deserialize(final String data) {
            return data;
        }

    }

    private static Module getModuleByNamespace(final String namespace, final SchemaContext schemaContext) {
        final var validNamespace = resolveValidNamespace(namespace, schemaContext);
        final var it = schemaContext.findModules(validNamespace).iterator();
        if (!it.hasNext()) {
            LOG.info("Module for namespace {} was not found.", validNamespace);
            return null;
        }
        return it.next();
    }

    private static XMLNamespace resolveValidNamespace(final String namespace, final SchemaContext schemaContext) {
        XMLNamespace validNamespace = findFirstModuleByName(schemaContext, namespace);
        return validNamespace != null ? validNamespace
            // FIXME: what the heck?!
            : XMLNamespace.of(namespace);
    }

    private static XMLNamespace findFirstModuleByName(final SchemaContext schemaContext, final String name) {
        for (final Module module : schemaContext.getModules()) {
            if (module.getName().equals(name)) {
                return module.getNamespace();
            }
        }
        return null;
    }
}
