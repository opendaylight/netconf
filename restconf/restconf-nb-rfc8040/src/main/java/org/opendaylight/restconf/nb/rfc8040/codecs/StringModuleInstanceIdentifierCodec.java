/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.codecs;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.util.AbstractStringInstanceIdentifierCodec;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public final class StringModuleInstanceIdentifierCodec extends AbstractStringInstanceIdentifierCodec {
    private final @NonNull DataSchemaContextTree dataContextTree;
    private final EffectiveModelContext context;

    public StringModuleInstanceIdentifierCodec(final EffectiveModelContext context) {
        this.context = requireNonNull(context);
        this.dataContextTree = DataSchemaContextTree.from(context);
    }

    @Override
    public DataSchemaContextTree getDataContextTree() {
        return dataContextTree;
    }

    @Override
    protected @Nullable String prefixForNamespace(final XMLNamespace namespace) {
        return context.findModules(namespace).stream().findFirst().map(Module::getName).orElse(null);
    }

    @Override
    protected QName createQName(final @Nullable QNameModule lastModule, final @Nullable String localName) {
        checkArgument(lastModule != null, "Unprefixed leading name %s", localName);
        return QName.create(lastModule, localName);
    }

    @Override
    protected QName createQName(final String prefix, final String localName) {
        return QName.create(context.findModules(prefix).stream()
            .findFirst().orElseThrow(() -> new IllegalArgumentException("No module named '" + prefix + "'"))
            .getQNameModule(), localName);
    }
}
