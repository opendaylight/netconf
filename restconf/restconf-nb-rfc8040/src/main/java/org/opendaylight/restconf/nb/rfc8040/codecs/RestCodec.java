/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.codecs;

import org.opendaylight.restconf.common.util.RestUtil;
import org.opendaylight.yangtools.yang.data.impl.codec.TypeDefinitionAwareCodec;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.InstanceIdentifierTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: what is this class even trying to do?
public final class RestCodec {
    private static final Logger LOG = LoggerFactory.getLogger(RestCodec.class);

    private RestCodec() {
        // Hidden on purpose
    }

    public static Object deserialize(final EffectiveModelContext schemaContext, final TypeDefinition<?> typeDefinition,
            final String input) {
        final TypeDefinition<?> type = RestUtil.resolveBaseTypeFrom(typeDefinition);

        try {
            if (type instanceof IdentityrefTypeDefinition) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                        "Value is not instance of IdentityrefTypeDefinition but is {}. "
                                + "Therefore NULL is used as translation of - {}",
                        input == null ? "null" : input.getClass(), String.valueOf(input));
                }
                // FIXME: this should be a hard error
                return null;
            } else if (type instanceof InstanceIdentifierTypeDefinition) {
                // FIXME: what is it that we are trying to decode here and why?
                return new StringModuleInstanceIdentifierCodec(schemaContext).deserialize(input);
            } else {
                final TypeDefinitionAwareCodec<Object, ? extends TypeDefinition<?>> typeAwarecodec =
                        TypeDefinitionAwareCodec.from(type);
                if (typeAwarecodec != null) {
                    return typeAwarecodec.deserialize(String.valueOf(input));
                } else {
                    // FIXME: this should be a hard error
                    LOG.debug("Codec for type \"{}\" is not implemented yet.", type.getQName().getLocalName());
                    return null;
                }
            }
        } catch (final ClassCastException e) {
            // FIXME: remove this catch when everyone use codecs
            // FIXME: this should be a hard error
            LOG.error("ClassCastException was thrown when codec is invoked with parameter {}", input, e);
            return null;
        }
    }
}