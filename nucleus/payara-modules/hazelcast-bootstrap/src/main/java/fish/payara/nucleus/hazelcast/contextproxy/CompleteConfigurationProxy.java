/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) [2016-2019] Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.nucleus.hazelcast.contextproxy;

import org.glassfish.internal.api.JavaEEContextUtil;
import org.glassfish.internal.api.JavaEEContextUtil.Context;
import java.util.Collection;
import java.util.Map;
import javax.cache.Cache;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CacheWriterException;

/**
 * Proxy all applicable factory calls
 * so Java EE context is propagated from within Hazelcast threads
 *
 * @author lprimak
 */
class CompleteConfigurationProxy<K, V> extends MutableConfiguration<K, V> {
    public CompleteConfigurationProxy(CompleteConfiguration<K, V> config, JavaEEContextUtil.Instance ctxUtilInst) {
        super(config);
        this.ctxUtilInst = ctxUtilInst;
        init();
    }

    private void init() {
        if(cacheLoaderFactory != null) {
            cacheLoaderFactory = proxyLoader(cacheLoaderFactory);
        }
        if(cacheWriterFactory != null) {
            cacheWriterFactory = proxyWriter(cacheWriterFactory);
        }
    }

    private Factory<CacheLoader<K, V>> proxyLoader(final Factory<CacheLoader<K, V>> fact) {
        return new Factory<CacheLoader<K, V>>() {
            @Override
            public CacheLoader<K, V> create() {
                try (Context ctx = ctxUtilInst.pushContext()) {
                    final CacheLoader<K, V> loader = fact.create();
                    return new CacheLoaderImpl(loader);
                }
            }

            class CacheLoaderImpl implements CacheLoader<K, V> {
                public CacheLoaderImpl(CacheLoader<K, V> loader) {
                    this.loader = loader;
                }

                @Override
                public V load(K k) throws CacheLoaderException {
                    try (Context context = ctxUtilInst.pushRequestContext()) {
                        return loader.load(k);
                    }
                }

                @Override
                public Map<K, V> loadAll(Iterable<? extends K> itrbl) throws CacheLoaderException {
                    try (Context context = ctxUtilInst.pushRequestContext()) {
                        return loader.loadAll(itrbl);
                    }
                }

                private final CacheLoader<K, V> loader;
            }

            private static final long serialVersionUID = 1L;
        };
    }

    private Factory<CacheWriter<? super K, ? super V>> proxyWriter(final Factory<CacheWriter<? super K, ? super V>> fact) {
        return new Factory<CacheWriter<? super K, ? super V>>() {
            @Override
            public CacheWriter<K, V> create() {
                try (Context ctx = ctxUtilInst.pushContext()) {
                    @SuppressWarnings("unchecked")
                    final CacheWriter<K, V> delegate = (CacheWriter<K, V>) fact.create();
                    return new CacheWriterImpl(delegate);
                }
            }

            class CacheWriterImpl implements CacheWriter<K, V> {
                @Override
                public void write(Cache.Entry<? extends K, ? extends V> entry) throws CacheWriterException {
                    try (Context context = ctxUtilInst.pushRequestContext()) {
                        delegate.write(entry);
                    }
                }

                @Override
                public void writeAll(Collection<Cache.Entry<? extends K, ? extends V>> clctn) throws CacheWriterException {
                    try (Context context = ctxUtilInst.pushRequestContext()) {
                        delegate.writeAll(clctn);
                    }
                }

                @Override
                public void delete(Object o) throws CacheWriterException {
                    try (Context context = ctxUtilInst.pushRequestContext()) {
                        delegate.delete(o);
                    }
                }

                @Override
                public void deleteAll(Collection<?> clctn) throws CacheWriterException {
                    try (Context context = ctxUtilInst.pushRequestContext()) {
                        delegate.deleteAll(clctn);
                    }
                }

                public CacheWriterImpl(CacheWriter<K, V> delegate) {
                    this.delegate = delegate;
                }

                private final CacheWriter<K, V> delegate;
            }

            private static final long serialVersionUID = 1L;
        };
    }

    private final JavaEEContextUtil.Instance ctxUtilInst;
    private static final long serialVersionUID = 1L;
}
