/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import com.google.common.base.Preconditions;
import java.net.URI;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.yangtools.yang.data.util.AbstractModuleStringInstanceIdentifierCodec;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Codec for module instance identifiers.
 *
 * @deprecated This class will be replaced by StringModuleInstanceIdentifierCodec from restconf-nb-rfc8040
 */
@Deprecated
public final class StringModuleInstanceIdentifierCodec extends AbstractModuleStringInstanceIdentifierCodec {

    private final DataSchemaContextTree dataContextTree;
    private final SchemaContext context;
    private final String defaultPrefix;

    public StringModuleInstanceIdentifierCodec(final SchemaContext context) {
        this.context = Preconditions.checkNotNull(context);
        this.dataContextTree = DataSchemaContextTree.from(context);
        this.defaultPrefix = "";
    }

    StringModuleInstanceIdentifierCodec(final SchemaContext context, @Nonnull final String defaultPrefix) {
        this.context = Preconditions.checkNotNull(context);
        this.dataContextTree = DataSchemaContextTree.from(context);
        this.defaultPrefix = defaultPrefix;
    }

    @Override
    protected Module moduleForPrefix(@Nonnull final String prefix) {
        if (prefix.isEmpty() && !this.defaultPrefix.isEmpty()) {
            return this.context.findModules(this.defaultPrefix).stream().findFirst().orElse(null);
        } else {
            return this.context.findModules(prefix).stream().findFirst().orElse(null);
        }
    }

    @Nonnull
    @Override
    protected DataSchemaContextTree getDataContextTree() {
        return this.dataContextTree;
    }

    @Nullable
    @Override
    protected String prefixForNamespace(@Nonnull final URI namespace) {
        return this.context.findModules(namespace).stream().findFirst().map(Module::getName).orElse(null);
    }
}