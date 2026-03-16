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
import org.opendaylight.yang.gen.v1.urn.opendaylight.aaa.app.config.rev170619.ShiroConfiguration;
import org.opendaylight.yang.gen.v1.urn.opendaylight.aaa.app.config.rev170619.ShiroIni;
import org.opendaylight.yang.gen.v1.urn.opendaylight.aaa.app.config.rev170619.shiro.ini.Main;
import org.opendaylight.yang.gen.v1.urn.opendaylight.aaa.app.config.rev170619.shiro.ini.MainKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.aaa.app.config.rev170619.shiro.ini.Urls;
import org.opendaylight.yang.gen.v1.urn.opendaylight.aaa.app.config.rev170619.shiro.ini.UrlsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.aaa.cert.rev151126.AaaCertServiceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.aaa.cert.rev151126.aaa.cert.service.config.CtlKeystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.aaa.cert.rev151126.aaa.cert.service.config.TrustKeystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.aaa.cert.rev151126.aaa.cert.service.config.ctlkeystore.CipherSuites;
import org.opendaylight.yangtools.binding.Augmentation;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

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
        return configLoader.getConfig(ShiroConfig.class, "shiro-configuration",
            Path.of("shiro-config.yaml"));
    }

    @Provides
    @Singleton
    static AaaCertServiceConfig aaaCertServiceConfig(final ConfigLoader configLoader) {
        return configLoader.getConfig(AaaCertConfig.class, "aaa-cert-service-config",
            Path.of("shiro-config.yaml"));
    }

    @Provides
    @Singleton
    static ICertificateManager iCertificateManager(final RpcProviderService rpcProviderService,
            final DataBroker dataBroker, final AAAEncryptionService encryptionSrv,
            final AaaCertServiceConfig aaaCertServiceConfig) {
        return new CertificateManagerService(rpcProviderService, dataBroker, encryptionSrv, aaaCertServiceConfig);
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

    @ConfigurationProperties(prefix = "shiro-configuration")
    class ShiroConfig implements ShiroConfiguration {
        private final List<Main> main;
        private final List<Urls> urls;

        @ConstructorBinding
        public ShiroConfig(@Name("main") final List<MainRecord> main, @Name("urls") final List<UrlsRecord> urls) {
            this.main = List.copyOf(main);
            this.urls = List.copyOf(urls);
        }

        @Override
        public List<Main> getMain() {
            return main;
        }

        @Override
        public List<Urls> getUrls() {
            return urls;
        }

        @Override
        public Map<Class<? extends Augmentation<ShiroConfiguration>>,
                Augmentation<ShiroConfiguration>> augmentations() {
            return Map.of();
        }
    }

    record MainRecord(@Name("pair-key") String pairKey, @Name("pair-value") String pairValue) implements Main {
        @Override
        public String getPairKey() {
            return pairKey;
        }

        @Override
        public String getPairValue() {
            return pairValue;
        }

        @Override
        public MainKey key() {
            return new MainKey(pairKey);
        }

        @Override
        public Map<Class<? extends Augmentation<Main>>, Augmentation<Main>> augmentations() {
            return Map.of();
        }
    }

    record UrlsRecord(@Name("pair-key") String pairKey, @Name("pair-value") String pairValue) implements Urls {
        @Override
        public String getPairKey() {
            return pairKey;
        }

        @Override
        public String getPairValue() {
            return pairValue;
        }

        @Override
        public UrlsKey key() {
            return new UrlsKey(pairKey);
        }

        @Override
        public Map<Class<? extends Augmentation<Urls>>, Augmentation<Urls>> augmentations() {
            return Map.of();
        }
    }

    @ConfigurationProperties(prefix = "aaa-cert-service-config")
    class AaaCertConfig implements AaaCertServiceConfig {
        private final Boolean useConfig;
        private final Boolean useMdsal;
        private final String bundleName;
        private final CtlKeystore ctlKeystore;
        private final TrustKeystore trustKeystore;

        @ConstructorBinding
        public AaaCertConfig(@Name("use-config") @DefaultValue("true") final Boolean useConfig,
                @Name("use-mdsal") @DefaultValue("true") final Boolean useMdsal,
                @Name("bundle-name") @DefaultValue("opendaylight") final String bundleName,
                @Name("ctl-keystore") final CtlKeystoreRecord ctlKeystore,
                @Name("trust-keystore") final TrustKeystoreRecord trustKeystore) {
            this.useConfig = useConfig;
            this.useMdsal = useMdsal;
            this.bundleName = bundleName;
            this.ctlKeystore = ctlKeystore;
            this.trustKeystore = trustKeystore;
        }

        @Override
        public Boolean getUseConfig() {
            return useConfig;
        }

        @Override
        public Boolean getUseMdsal() {
            return useMdsal;
        }

        @Override
        public String getBundleName() {
            return bundleName;
        }

        @Override
        public CtlKeystore getCtlKeystore() {
            return ctlKeystore;
        }

        @Override
        public CtlKeystore nonnullCtlKeystore() {
            return ctlKeystore;
        }

        @Override
        public TrustKeystore getTrustKeystore() {
            return trustKeystore;
        }

        @Override
        public TrustKeystore nonnullTrustKeystore() {
            return trustKeystore;
        }

        @Override
        public Map<Class<? extends Augmentation<AaaCertServiceConfig>>,
                Augmentation<AaaCertServiceConfig>> augmentations() {
            return Map.of();
        }
    }

    record CtlKeystoreRecord(String name, String alias, String storePassword, String dname, Integer validity,
            String keyAlg, String signAlg, Integer keysize, String tlsProtocols,
            @Name("cipher-suites") List<CipherSuitesRecord> cipherSuitesRecords) implements CtlKeystore {

        public CtlKeystoreRecord {
            cipherSuitesRecords = List.copyOf(cipherSuitesRecords);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getAlias() {
            return alias;
        }

        @Override
        public String getStorePassword() {
            return storePassword;
        }

        @Override
        public String getDname() {
            return dname;
        }

        @Override
        public Integer getValidity() {
            return validity;
        }

        @Override
        public String getKeyAlg() {
            return keyAlg;
        }

        @Override
        public String getSignAlg() {
            return signAlg;
        }

        @Override
        public Integer getKeysize() {
            return keysize;
        }

        @Override
        public String getTlsProtocols() {
            return tlsProtocols;
        }

        @Override
        public List<CipherSuites> getCipherSuites() {
            return List.copyOf(cipherSuitesRecords);
        }

        @Override
        public Map<Class<? extends Augmentation<CtlKeystore>>, Augmentation<CtlKeystore>> augmentations() {
            return Map.of();
        }
    }

    record CipherSuitesRecord(String suiteName) implements CipherSuites {
        @Override
        public String getSuiteName() {
            return suiteName;
        }

        @Override
        public Map<Class<? extends Augmentation<CipherSuites>>, Augmentation<CipherSuites>> augmentations() {
            return Map.of();
        }
    }

    record TrustKeystoreRecord(String name, String storePassword) implements TrustKeystore {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getStorePassword() {
            return storePassword;
        }

        @Override
        public Map<Class<? extends Augmentation<TrustKeystore>>, Augmentation<TrustKeystore>> augmentations() {
            return Map.of();
        }
    }
}
