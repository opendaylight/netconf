/*
 * Copyright (c) 2018 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.exi;

import static java.util.Objects.requireNonNull;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.EXIException;
import org.opendaylight.netconf.shaded.exificient.core.grammars.Grammars;
import org.opendaylight.netconf.shaded.exificient.grammars.GrammarFactory;

/**
 * Enumeration of schema modes defined by the NETCONF EXI capability.
 */
public enum EXISchema {
    NONE("none", GrammarFactory.newInstance().createSchemaLessGrammars()),
    BUILTIN("builtin", createBuiltinGrammar()),
    BASE_1_1("base:1.1", createNetconfGrammar());

    private String option;
    private Grammars grammar;

    EXISchema(final String option, final Grammars grammar) {
        this.option = requireNonNull(option);
        this.grammar = requireNonNull(grammar);
    }

    final String getOption() {
        return option;
    }

    final Grammars getGrammar() {
        return grammar;
    }

    static EXISchema forOption(final String id) {
        for (EXISchema s : EXISchema.values()) {
            if (id.equals(s.getOption())) {
                return s;
            }
        }

        return null;
    }

    private static Grammars createNetconfGrammar() {
        final ByteSource source = Resources.asByteSource(EXISchema.class.getResource("/rfc6241.xsd"));
        try (InputStream is = source.openStream()) {
            final Grammars g = GrammarFactory.newInstance().createGrammars(is);
            g.setSchemaId("base:1.1");
            return g;
        } catch (EXIException | IOException e) {
            throw new IllegalStateException("Failed to create RFC6241 grammar", e);
        }
    }

    private static Grammars createBuiltinGrammar() {
        try {
            return GrammarFactory.newInstance().createXSDTypesOnlyGrammars();
        } catch (EXIException e) {
            throw new IllegalStateException("Failed to create builtin grammar", e);
        }
    }
}
