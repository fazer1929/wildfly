/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.clustering.ejb.infinispan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.ExpirationConfiguration;
import org.infinispan.configuration.cache.StorageType;
import org.infinispan.eviction.EvictionStrategy;
import org.jboss.as.clustering.controller.CapabilityServiceConfigurator;
import org.jboss.as.controller.ServiceNameFactory;
import org.jboss.as.server.deployment.Services;
import org.jboss.msc.service.ServiceName;
import org.wildfly.clustering.ee.CompositeIterable;
import org.wildfly.clustering.ejb.StatefulBeanConfiguration;
import org.wildfly.clustering.ejb.BeanManagerFactory;
import org.wildfly.clustering.ejb.BeanManagerFactoryServiceConfiguratorConfiguration;
import org.wildfly.clustering.ejb.BeanManagementProvider;
import org.wildfly.clustering.ejb.infinispan.logging.InfinispanEjbLogger;
import org.wildfly.clustering.infinispan.spi.DataContainerConfigurationBuilder;
import org.wildfly.clustering.infinispan.spi.InfinispanCacheRequirement;
import org.wildfly.clustering.infinispan.spi.service.CacheServiceConfigurator;
import org.wildfly.clustering.infinispan.spi.service.TemplateConfigurationServiceConfigurator;
import org.wildfly.clustering.service.ServiceDependency;
import org.wildfly.clustering.service.ServiceNameRegistry;
import org.wildfly.clustering.spi.CacheServiceConfiguratorProvider;
import org.wildfly.clustering.spi.ClusteringCacheRequirement;
import org.wildfly.clustering.spi.DistributedCacheServiceConfiguratorProvider;

/**
 * Builds an infinispan-based {@link BeanManagerFactory}.
 *
 * @author Paul Ferraro
 *
 * @param <G> the group identifier type
 * @param <I> the bean identifier type
 */
public class InfinispanBeanManagementProvider<I> implements BeanManagementProvider {

    static String getCacheName(ServiceName deploymentUnitServiceName, String beanManagerFactoryName) {
        List<String> parts = new ArrayList<>(3);
        if (Services.JBOSS_DEPLOYMENT_SUB_UNIT.isParentOf(deploymentUnitServiceName)) {
            parts.add(deploymentUnitServiceName.getParent().getSimpleName());
        }
        parts.add(deploymentUnitServiceName.getSimpleName());
        parts.add(beanManagerFactoryName);
        return String.join("/", parts);
    }

    private final String name;
    private final BeanManagerFactoryServiceConfiguratorConfiguration config;

    public InfinispanBeanManagementProvider(String name, BeanManagerFactoryServiceConfiguratorConfiguration config) {
        this.name = name;
        this.config = config;
    }

    @Override
    public Iterable<CapabilityServiceConfigurator> getDeploymentServiceConfigurators(final ServiceName name) {
        String cacheName = getCacheName(name, this.name);
        String containerName = this.config.getContainerName();
        String templateCacheName = this.config.getCacheName();

        // Ensure eviction and expiration are disabled
        Consumer<ConfigurationBuilder> configurator = builder -> {
            // Ensure expiration is not enabled on cache
            ExpirationConfiguration expiration = builder.expiration().create();
            if ((expiration.lifespan() >= 0) || (expiration.maxIdle() >= 0)) {
                builder.expiration().lifespan(-1).maxIdle(-1);
                InfinispanEjbLogger.ROOT_LOGGER.expirationDisabled(InfinispanCacheRequirement.CONFIGURATION.resolve(containerName, templateCacheName));
            }

            Integer size = this.config.getMaxActiveBeans();
            EvictionStrategy strategy = (size != null) ? EvictionStrategy.REMOVE : EvictionStrategy.MANUAL;
            builder.memory().storage(StorageType.HEAP).whenFull(strategy).maxCount((size != null) ? size.longValue() : 0);
            if (strategy.isEnabled()) {
                // Only evict bean group entries
                // We will cascade eviction to the associated beans
                builder.addModule(DataContainerConfigurationBuilder.class).evictable(BeanGroupKey.class::isInstance);
            }
        };

        CapabilityServiceConfigurator configurationConfigurator = new TemplateConfigurationServiceConfigurator(ServiceNameFactory.parseServiceName(InfinispanCacheRequirement.CONFIGURATION.getName()).append(containerName, cacheName), containerName, cacheName, templateCacheName, configurator);
        CapabilityServiceConfigurator cacheConfigurator = new CacheServiceConfigurator<>(ServiceNameFactory.parseServiceName(InfinispanCacheRequirement.CACHE.getName()).append(containerName, cacheName), containerName, cacheName).require(new ServiceDependency(name.append("marshalling")));

        List<Iterable<CapabilityServiceConfigurator>> configurators = new LinkedList<>();
        configurators.add(Arrays.asList(configurationConfigurator, cacheConfigurator));

        ServiceNameRegistry<ClusteringCacheRequirement> registry = new ServiceNameRegistry<ClusteringCacheRequirement>() {
            @Override
            public ServiceName getServiceName(ClusteringCacheRequirement requirement) {
                return (requirement == ClusteringCacheRequirement.GROUP) ? ServiceNameFactory.parseServiceName(requirement.getName()).append(containerName, cacheName) : null;
            }
        };
        for (CacheServiceConfiguratorProvider provider : ServiceLoader.load(DistributedCacheServiceConfiguratorProvider.class, DistributedCacheServiceConfiguratorProvider.class.getClassLoader())) {
            configurators.add(provider.getServiceConfigurators(registry, containerName, cacheName));
        }
        return new CompositeIterable<>(configurators);
    }

    @Override
    public CapabilityServiceConfigurator getBeanManagerFactoryServiceConfigurator(StatefulBeanConfiguration context) {
        return new InfinispanBeanManagerFactoryServiceConfigurator<>(this.name, context, this.config);
    }
}
