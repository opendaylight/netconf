/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.codecs;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.opendaylight.restconf.common.util.IdentityValuesDTO;
import org.opendaylight.restconf.common.util.RestUtil;
import org.opendaylight.restconf.nb.rfc8040.codecs.RestCodec.IdentityrefCodecImpl;
import org.opendaylight.restconf.nb.rfc8040.codecs.RestCodec.InstanceIdentifierCodecImpl;
import org.opendaylight.restconf.nb.rfc8040.codecs.RestCodec.LeafrefCodecImpl;
import org.opendaylight.yangtools.concepts.IllegalArgumentCodec;
import org.opendaylight.yangtools.yang.data.impl.codec.TypeDefinitionAwareCodec;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.InstanceIdentifierTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
// FIXME: IllegalArgumentCodec is not quite accurate
public final class ObjectCodec implements IllegalArgumentCodec<Object, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(ObjectCodec.class);
    private static final IllegalArgumentCodec LEAFREF_DEFAULT_CODEC = new LeafrefCodecImpl();

    private final IllegalArgumentCodec instanceIdentifier;
    private final IllegalArgumentCodec identityrefCodec;
    private final EffectiveModelContext schemaContext;
    private final TypeDefinition<?> type;

    private ObjectCodec(final EffectiveModelContext schemaContext, final TypeDefinition<?> typeDefinition) {
        this.schemaContext = requireNonNull(schemaContext);
        type = RestUtil.resolveBaseTypeFrom(typeDefinition);
        if (type instanceof IdentityrefTypeDefinition) {
            identityrefCodec = new IdentityrefCodecImpl(schemaContext);
        } else {
            identityrefCodec = null;
        }
        if (type instanceof InstanceIdentifierTypeDefinition) {
            instanceIdentifier = new InstanceIdentifierCodecImpl(schemaContext);
        } else {
            instanceIdentifier = null;
        }
    }

    public static ObjectCodec of(final EffectiveModelContext schemaContext, final TypeDefinition<?> typeDefinition) {
        return new ObjectCodec(schemaContext, typeDefinition);
    }

    @SuppressWarnings("unchecked")
    @Override
    @SuppressFBWarnings(value = "NP_NONNULL_RETURN_VIOLATION", justification = "Legacy returns")
    public Object deserialize(final Object input) {
        try {
            if (type instanceof IdentityrefTypeDefinition) {
                if (input instanceof IdentityValuesDTO) {
                    return identityrefCodec.deserialize(input);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                        "Value is not instance of IdentityrefTypeDefinition but is {}. "
                                + "Therefore NULL is used as translation of - {}",
                        input == null ? "null" : input.getClass(), String.valueOf(input));
                }
                // FIXME: this should be a hard error
                return null;
            } else if (type instanceof InstanceIdentifierTypeDefinition) {
                return input instanceof IdentityValuesDTO ? instanceIdentifier.deserialize(input)
                    // FIXME: what is it that we are trying to decode here and why?
                    : new StringModuleInstanceIdentifierCodec(schemaContext).deserialize((String) input);
            } else {
                final TypeDefinitionAwareCodec<Object, ? extends TypeDefinition<?>> typeAwarecodec =
                        TypeDefinitionAwareCodec.from(type);
                if (typeAwarecodec != null) {
                    if (input instanceof IdentityValuesDTO) {
                        return typeAwarecodec.deserialize(((IdentityValuesDTO) input).getOriginValue());
                    }
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

    @SuppressWarnings("unchecked")
    @Override
    @SuppressFBWarnings(value = "NP_NONNULL_RETURN_VIOLATION", justification = "Legacy returns")
    public Object serialize(final Object input) {
        try {
            if (type instanceof IdentityrefTypeDefinition) {
                return identityrefCodec.serialize(input);
            } else if (type instanceof LeafrefTypeDefinition) {
                return LEAFREF_DEFAULT_CODEC.serialize(input);
            } else if (type instanceof InstanceIdentifierTypeDefinition) {
                return instanceIdentifier.serialize(input);
            } else {
                final TypeDefinitionAwareCodec<Object, ? extends TypeDefinition<?>> typeAwarecodec =
                        TypeDefinitionAwareCodec.from(type);
                if (typeAwarecodec != null) {
                    return typeAwarecodec.serialize(input);
                } else {
                    // FIXME: this needs to be a hard error
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Codec for type \"{}\" is not implemented yet.", type.getQName().getLocalName());
                    }
                    return null;
                }
            }
        } catch (final ClassCastException e) {
            // FIXME: remove this catch when everyone use codecs
            // FIXME: this needs to be a hard error
            LOG.error("ClassCastException was thrown when codec is invoked with parameter {}", input, e);
            return input;
        }
    }
}