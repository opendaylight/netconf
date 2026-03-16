/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.aaa;

import com.google.errorprone.annotations.DoNotMock;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.aaa.api.AuthenticationService;
import org.opendaylight.aaa.api.password.service.PasswordHashService;
import org.opendaylight.aaa.cert.api.ICertificateManager;
import org.opendaylight.aaa.cert.impl.CertificateManagerService;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.aaa.impl.password.service.DefaultPasswordHashService;
import org.opendaylight.aaa.shiro.realm.EmptyRealmAuthProvider;
import org.opendaylight.aaa.shiro.realm.RealmAuthProvider;
import org.opendaylight.aaa.shiro.web.env.AAAShiroWebEnvironment;
import org.opendaylight.aaa.shiro.web.env.AAAWebEnvironment;
import org.opendaylight.aaa.tokenauthrealm.auth.AuthenticationManager;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.netconf.dagger.springboot.config.ConfigLoader;
import org.opendaylight.odlparent.dagger.ResourceSupport;
import org.opendaylight.yang.gen.v1.urn.opendaylight.aaa.app.config.rev170619.ShiroConfigurationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.aaa.app.config.rev170619.ShiroIni;
import org.opendaylight.yang.gen.v1.urn.opendaylight.aaa.app.config.rev170619.shiro.ini.MainBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.aaa.app.config.rev170619.shiro.ini.UrlsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.aaa.cert.rev151126.AaaCertServiceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.aaa.cert.rev151126.AaaCertServiceConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.aaa.cert.rev151126.aaa.cert.service.config.CtlKeystoreBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.aaa.cert.rev151126.aaa.cert.service.config.TrustKeystoreBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.aaa.cert.rev151126.aaa.cert.service.config.ctlkeystore.CipherSuitesBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * A Dagger module providing insecure {@link AAAShiroWebEnvironment} only for testing purpose.
 */
@Module
@DoNotMock
@NonNullByDefault
public interface AAAShiroWebEnvironmentModule {

    @Provides
    @Singleton
    static ShiroIni shiroIni(final ConfigLoader configLoader) {
        final var props = configLoader.getConfig(ShiroProperties.class, "odl.netconf.prototype.shiro-configuration",
            Path.of("application.yaml"));
        final var mainList = props.main().entrySet().stream()
                    .map(e -> new MainBuilder()
                        .setPairKey(e.getKey())
                        .setPairValue(e.getValue())
                        .build())
                    .collect(Collectors.toList());
        final var urlsList = props.urls().entrySet().stream()
                    .map(u -> new UrlsBuilder()
                        .setPairKey(u.getKey())
                        .setPairValue(u.getValue())
                        .build())
                    .collect(Collectors.toList());

        return new ShiroConfigurationBuilder()
            .setMain(mainList)
            .setUrls(urlsList)
            .build();
    }

    @Provides
    @Singleton
    static AaaCertServiceConfig aaaCertServiceConfig(final ConfigLoader configLoader) {
        final var config = configLoader.getConfig(AaaCertProperties.class,
            "odl.netconf.prototype.aaa-cert-service-config", Path.of("application.yaml"));

        final var cipherSuites = config.ctlKeystore().cipherSuites().stream()
            .map(suite -> new CipherSuitesBuilder().setSuiteName(suite).build())
            .collect(Collectors.toList());
        final var ctlKeystore = new CtlKeystoreBuilder()
            .setName(config.ctlKeystore().name())
            .setAlias(config.ctlKeystore().alias())
            .setStorePassword(config.ctlKeystore().storePassword())
            .setDname(config.ctlKeystore().dname())
            .setValidity(config.ctlKeystore().validity())
            .setKeyAlg(config.ctlKeystore().keyAlg())
            .setSignAlg(config.ctlKeystore().signAlg())
            .setKeysize(config.ctlKeystore().keysize())
            .setTlsProtocols(config.ctlKeystore().tlsProtocols())
            .setCipherSuites(cipherSuites)
            .build();
        final var trustKeystore = new TrustKeystoreBuilder()
            .setName(config.trustKeystore().name())
            .setStorePassword(config.trustKeystore().storePassword())
            .build();

        return new AaaCertServiceConfigBuilder()
            .setUseConfig(config.useConfig())
            .setUseMdsal(config.useMdsal())
            .setBundleName(config.bundleName())
            .setCtlKeystore(ctlKeystore)
            .setTrustKeystore(trustKeystore)
            .build();
    }

    @Provides
    @Singleton
    static ICertificateManager iCertificateManager(final RpcProviderService rpcProviderService,
            final DataBroker dataBroker, final AAAEncryptionService encryptionSrv,
            final AaaCertServiceConfig aaaCertServiceConfig, final ResourceSupport resourceSupport) {
        final var service = new CertificateManagerService(rpcProviderService, dataBroker, encryptionSrv,
            aaaCertServiceConfig);
        resourceSupport.register(service);
        return service;
    }

    @Provides
    @Singleton
    static AuthenticationService authenticationService() {
        return new AuthenticationManager();
    }

    @Provides
    @Singleton
    static RealmAuthProvider realmAuthProvider() {
        return new EmptyRealmAuthProvider();
    }

    @Provides
    @Singleton
    static PasswordHashService passwordHashService() {
        return new DefaultPasswordHashService();
    }

    @Provides
    @Singleton
    static AAAShiroWebEnvironment jettyWebServer(final ShiroIni shiroConfiguration, final DataBroker dataBroker,
            final ICertificateManager certificateManager, final AuthenticationService authenticationService,
            final RealmAuthProvider realmAuthProvider, final PasswordHashService passwordHashService,
            final ServletSupport servletSupport) {
        return new AAAWebEnvironment(shiroConfiguration, dataBroker, certificateManager, authenticationService,
            realmAuthProvider, passwordHashService, servletSupport);
    }

    /**
     * This class represents the <b>shiro-configuration</b> YANG schema container defined in module
     * <b>aaa-app-config</b>.
     *
     * @param main This represents the <b>main</b> YANG schema list defined in module <b>aaa-app-config</b>.
     * @param urls This represents the <b>urls</b> YANG schema list defined in module <b>aaa-app-config</b>.
     */
    @ConfigurationProperties(prefix = "odl.netconf.prototype.shiro-configuration")
    record ShiroProperties(
        Map<String, String> main,
        Map<String, String> urls) {}

    /**
     * This class represents the <b>aaa-cert-service-config</b> YANG schema container defined in module <b>aaa-cert</b>.
     *
     * @param useConfig Use the configuration data to create the keystores.
     * @param useMdsal Use Mdsal as Data store for the keystore and certificates.
     * @param bundleName Bundle name of the default TLS config in MdsaL.
     * @param ctlKeystore Configuration for the identity keystore.
     * @param trustKeystore Configuration for the truststore.
     */
    @ConfigurationProperties(prefix = "odl.netconf.prototype.aaa-cert-service-config")
    record AaaCertProperties(
        @DefaultValue("true") Boolean useConfig,
        @DefaultValue("true") Boolean useMdsal,
        @DefaultValue("opendaylight") String bundleName,
        CtlKeystoreProperty ctlKeystore,
        TrustKeystoreProperty trustKeystore
    ) {}

    /**
     * This class represents the <b>ctlKeystore</b> YANG schema container defined in module <b>aaa-cert</b>.
     *
     * @param name Keystore name default is ctl.
     * @param alias Key alias.
     * @param storePassword Keystore password.
     * @param dname X.500 Distinguished Names should be in the following formate
     *          CN=commonName
     *          OU=organizationUnit
     *          O=organizationName
     *          L=localityName
     *          S=stateName
     *          C=country
     * @param validity Validity.
     * @param keyAlg The supported key generation algorithms i.e: DSA or RSA.
     * @param signAlg The supported sign algorithmes i.e: SHA1withDSA or SHA1withRSA.
     * @param keysize The key size i.e: 1024.
     * @param tlsProtocols The TLS supported protocols SSLv2Hello,TLSv1.1,TLSv1.2.
     * @param cipherSuites List of cipher suites records.
     */
    record CtlKeystoreProperty(
        String name, String alias, String storePassword, String dname, Integer validity,
        String keyAlg, String signAlg, Integer keysize, String tlsProtocols,
        List<String> cipherSuites
    ) {}

    /**
     * This class represents the <b>trustKeystore</b> YANG schema container defined in module <b>aaa-cert</b>.
     *
     * @param name Keystore name.
     * @param storePassword Keystore password.
     */
    record TrustKeystoreProperty(String name, String storePassword) {}
}
