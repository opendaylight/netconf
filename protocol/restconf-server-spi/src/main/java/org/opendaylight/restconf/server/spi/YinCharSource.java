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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import javax.xml.stream.XMLStreamException;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SubmoduleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.export.YinExportUtils;

abstract sealed class YinCharSource extends CharSource {
    static final class OfModule extends YinCharSource {
        private final ModuleEffectiveStatement module;

        OfModule(final ModuleEffectiveStatement module) {
            this.module = requireNonNull(module);
        }

        @Override
        void writeTo(final OutputStream out) throws XMLStreamException {
            YinExportUtils.writeModuleAsYinText(module, out);
        }
    }

    static final class OfSubmodule extends YinCharSource {
        private final ModuleEffectiveStatement module;
        private final SubmoduleEffectiveStatement submodule;

        OfSubmodule(final ModuleEffectiveStatement module, final SubmoduleEffectiveStatement submodule) {
            this.module = requireNonNull(module);
            this.submodule = requireNonNull(submodule);
        }

        @Override
        void writeTo(final OutputStream out) throws XMLStreamException {
            YinExportUtils.writeSubmoduleAsYinText(module, submodule, out);
        }
    }

    @Override
    public final Reader openStream() throws IOException {
        final var bos = new ByteArrayOutputStream();
        try {
            writeTo(bos);
        } catch (XMLStreamException e) {
            throw new IOException("Failed to export source", e);
        }
        return new StringReader(new String(bos.toByteArray(), StandardCharsets.UTF_8));
    }

    abstract void writeTo(OutputStream out) throws XMLStreamException;
}
