/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.config;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Map;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component instantiating global NETCONF resources.
 */
@Component(service = { }, configurationPid = "org.opendaylight.netconf.config")
@Designate(ocd = Configuration.class)
public final class GlobalNetconfConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalNetconfConfiguration.class);

    private final ComponentFactory<GlobalNetconfProcessingExecutor> processingFactory;

    private GlobalNetconfThreadFactory threadFactory;
    private ComponentInstance<GlobalNetconfProcessingExecutor> processingExecutor;
    private Map<String, ?> processingProps;

    @Activate
    public GlobalNetconfConfiguration(
            @Reference(target = "(component.factory=" + GlobalNetconfProcessingExecutor.FACTORY_NAME + ")")
            final ComponentFactory<GlobalNetconfProcessingExecutor> processingFactory,
            final Configuration configuration) {
        this.processingFactory = requireNonNull(processingFactory);

        threadFactory = new GlobalNetconfThreadFactory(configuration.name$_$prefix());
        processingProps = GlobalNetconfProcessingExecutor.props(threadFactory, configuration);
        processingExecutor = processingFactory.newInstance(FrameworkUtil.asDictionary(processingProps));
        LOG.info("Global NETCONF configuration pools started");
    }

    @Modified
    void modified(final Configuration configuration) {
        final var newNamePrefix = configuration.name$_$prefix();
        if (!threadFactory.getNamePrefix().equals(newNamePrefix)) {
            threadFactory = new GlobalNetconfThreadFactory(newNamePrefix);
            processingProps = null;
            LOG.debug("Forcing restart of all executors");
        }

        // We want to instantiate new services before we dispose old ones, so
        final var toDispose = new ArrayList<ComponentInstance<?>>();

        final var newProcessingProps = GlobalNetconfProcessingExecutor.props(threadFactory, configuration);
        if (!newProcessingProps.equals(processingProps)) {
            processingProps = newProcessingProps;
            toDispose.add(processingExecutor);
            processingExecutor = processingFactory.newInstance(FrameworkUtil.asDictionary(processingProps));
            LOG.debug("Processing executor restarted with {}", processingProps);
        }

        toDispose.forEach(ComponentInstance::dispose);
    }

    @Deactivate
    void deactivate() {
        processingExecutor.dispose();
        processingExecutor = null;
        threadFactory = null;
        LOG.info("Global NETCONF configuration pools stopped");
    }

    static <T> T extractProp(final Map<String, ?> properties, final String key, final Class<T> valueType) {
        return valueType.cast(verifyNotNull(properties.get(requireNonNull(key))));
    }
}
