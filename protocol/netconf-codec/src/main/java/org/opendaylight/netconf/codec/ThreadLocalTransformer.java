/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2024 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static java.util.Objects.requireNonNull;

import com.google.common.base.VerifyException;
import java.util.function.Consumer;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Utility class for cached thread-local transformers. This class exists mostly for use by handlers.
 */
@NonNullByDefault
final class ThreadLocalTransformer extends ThreadLocal<Transformer> {
    private static final TransformerFactory FACTORY = TransformerFactory.newInstance();

    private final Consumer<Transformer> configurator;

    ThreadLocalTransformer(final Consumer<Transformer> configurator) {
        this.configurator = requireNonNull(configurator);
    }

    @Override
    public void set(final Transformer value) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Transformer initialValue() {
        final Transformer transformer;
        try {
            transformer = FACTORY.newTransformer();
        } catch (TransformerConfigurationException | TransformerFactoryConfigurationError e) {
            throw new VerifyException(e);
        }
        configurator.accept(transformer);
        return transformer;
    }
}
