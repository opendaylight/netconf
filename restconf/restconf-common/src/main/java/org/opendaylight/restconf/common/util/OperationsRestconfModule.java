/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.util;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

final class OperationsRestconfModule extends AbstractOperationsModule {
    // There is no need to intern this nor add a revision, as we are providing the corresponding context anyway
    static final QNameModule NAMESPACE = QNameModule.create(URI.create("urn:ietf:params:xml:ns:yang:ietf-restconf"));

    private final OperationsContainerSchemaNode operations;

    OperationsRestconfModule(final OperationsContainerSchemaNode operations) {
        this.operations = requireNonNull(operations);
    }

    @Override
    public String getName() {
        return "ietf-restconf";
    }

    @Override
    public QNameModule getQNameModule() {
        return NAMESPACE;
    }

    @Override
    public Collection<DataSchemaNode> getChildNodes() {
        return Collections.singleton(operations);
    }

    @Override
    public Optional<DataSchemaNode> findDataChildByName(final QName name) {
        return operations.getQName().equals(requireNonNull(name)) ? Optional.of(operations) : Optional.empty();
    }

    @Override
    public String getPrefix() {
        return "rc";
    }
}
