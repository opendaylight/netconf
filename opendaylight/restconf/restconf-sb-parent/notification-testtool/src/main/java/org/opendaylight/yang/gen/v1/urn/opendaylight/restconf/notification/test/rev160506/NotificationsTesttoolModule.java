package org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.test.rev160506;

import org.opendaylight.restconf.notification.testtool.sb.NotificationStoreServiceImpl;
import org.opendaylight.restconf.notification.testtool.sb.NotificationsProvider;

/**
 * Module for device notifications testing. Notifications from all devices are written to the data store.
 */
public class NotificationsTesttoolModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.test.rev160506.AbstractNotificationsTesttoolModule {
    public NotificationsTesttoolModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public NotificationsTesttoolModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, final org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.test.rev160506.NotificationsTesttoolModule oldModule, final java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        final NotificationStoreServiceImpl notificationStoreService = new NotificationStoreServiceImpl();
        getBindingRegistryDependency().registerProvider(notificationStoreService);
        final NotificationsProvider notificationsProvider = new NotificationsProvider(notificationStoreService);
        getDomRegistryDependency().registerProvider(notificationsProvider);
        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
                notificationsProvider.close();
                notificationStoreService.close();
            }
        };
    }

}
