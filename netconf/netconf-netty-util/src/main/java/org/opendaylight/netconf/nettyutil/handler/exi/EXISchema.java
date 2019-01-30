/*
 * Copyright (c) 2018 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.exi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Suppliers;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.EXIException;
import org.opendaylight.netconf.shaded.exificient.core.grammars.Grammars;
import org.opendaylight.netconf.shaded.exificient.grammars.GrammarFactory;

/**
 * Enumeration of schema modes defined by the NETCONF EXI capability.
 */
public enum EXISchema {
    NONE("none") {
        @Override
        Grammars createGrammar() {
            return GrammarFactory.newInstance().createSchemaLessGrammars();
        }
    },
    BUILTIN("builtin") {
        @Override
        Grammars createGrammar() {
            try {
                return GrammarFactory.newInstance().createXSDTypesOnlyGrammars();
            } catch (EXIException e) {
                throw new IllegalStateException("Failed to create builtin grammar", e);
            }
        }
    },
    BASE_1_1("base:1.1") {
        @Override
        Grammars createGrammar() {
            final ByteSource source = Resources.asByteSource(EXISchema.class.getResource("/rfc6241.xsd"));
            try (InputStream is = source.openStream()) {
                final Grammars g = GrammarFactory.newInstance().createGrammars(is);
                g.setSchemaId(getOption());
                return g;
            } catch (EXIException | IOException e) {
                throw new IllegalStateException("Failed to create RFC6241 grammar", e);
            }
        }
    };

    private String option;
    private Supplier<Grammars> grammarsSupplier;

    EXISchema(final String option) {
        this.option = requireNonNull(option);
        // Grammar instantiation can be CPU-intensive, hence we instantiate it lazily through a memoizing supplier
        this.grammarsSupplier = Suppliers.memoize(this::createGrammar);
    }

    final String getOption() {
        return option;
    }

    /**
     * Return the grammar associated with this EXISchema.
     *
     * @return An EXI grammar.
     */
    final Grammars getGrammar() {
        return grammarsSupplier.get();
    }

    /**
     * Create grammars associated with this EXISchema. This is a potentially expensive operation for internal use only,
     * use {@link #getGrammar()} instead.
     *
     * @return An EXI grammar.
     */
    abstract Grammars createGrammar();

    static @Nullable EXISchema forOption(final String id) {
        for (EXISchema s : EXISchema.values()) {
            if (id.equals(s.getOption())) {
                return s;
            }
        }
        return null;
    }
}
