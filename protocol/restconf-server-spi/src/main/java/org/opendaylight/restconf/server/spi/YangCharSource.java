/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.io.CharSource;
import java.io.Reader;
import java.io.StringReader;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SubmoduleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.export.DeclaredStatementFormatter;
import org.opendaylight.yangtools.yang.model.export.YangTextSnippet;

final class YangCharSource extends CharSource {
    private static final DeclaredStatementFormatter FORMATTER = DeclaredStatementFormatter.builder()
        .retainDefaultStatements()
        .build();

    private final YangTextSnippet snippet;

    private YangCharSource(final YangTextSnippet snippet) {
        this.snippet = requireNonNull(snippet);
    }

    YangCharSource(final ModuleEffectiveStatement module) {
        this(FORMATTER.toYangTextSnippet(module, module.getDeclared()));
    }

    YangCharSource(final ModuleEffectiveStatement module, final SubmoduleEffectiveStatement submodule) {
        this(FORMATTER.toYangTextSnippet(submodule, submodule.getDeclared()));
    }

    @Override
    public Reader openStream() {
        // FIXME: improve this by implementing a Reader which funnels from Iterator<String>
        return new StringReader(snippet.toString());
    }
}
