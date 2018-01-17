/*
 * Copyright (c) 2016 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.exi;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.siemens.ct.exi.GrammarFactory;
import com.siemens.ct.exi.exceptions.EXIException;
import com.siemens.ct.exi.grammars.Grammars;
import java.io.IOException;
import java.io.InputStream;

/**
 * Enumeration of schema modes defined by the NETCONF EXI capability.
 */
public enum EXISchema {
    NONE("none", GrammarFactory.newInstance().createSchemaLessGrammars()),
    BUILTIN("builtin", createBuiltinGrammar()),
    BASE_1_1("base:1.1", createNetconfGrammar());

    private String option;
    private Grammars grammar;

    private EXISchema(final String option, final Grammars grammar) {
        this.option = Preconditions.checkNotNull(option);
        this.grammar = Preconditions.checkNotNull(grammar);
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
