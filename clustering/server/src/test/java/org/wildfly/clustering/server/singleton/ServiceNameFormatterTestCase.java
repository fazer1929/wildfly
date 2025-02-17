/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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

package org.wildfly.clustering.server.singleton;

import java.io.IOException;

import org.jboss.msc.service.ServiceName;
import org.junit.Test;
import org.wildfly.clustering.marshalling.ExternalizerTester;
import org.wildfly.clustering.marshalling.jboss.JBossMarshallingTesterFactory;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamTesterFactory;
import org.wildfly.clustering.marshalling.spi.FormatterTester;
import org.wildfly.clustering.server.singleton.ServiceNameFormatter.ServiceNameExternalizer;

/**
 * Unit test for {@link ServiceNameFormatter}.
 * @author Paul Ferraro
 */
public class ServiceNameFormatterTestCase {
    private final ServiceName name = ServiceName.JBOSS.append("foo", "bar");

    @Test
    public void test() throws IOException {
        new ExternalizerTester<>(new ServiceNameExternalizer()).test(this.name);
        new FormatterTester<>(new ServiceNameFormatter()).test(this.name);

        JBossMarshallingTesterFactory.INSTANCE.createTester().test(this.name);
        ProtoStreamTesterFactory.INSTANCE.createTester().test(this.name);
    }
}
