/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
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
 *
 */
package org.apache.camel.component.netty;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * <p>Title: NettyLocalSyncNotLazyChannelTest</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.apache.camel.component.netty.NettyLocalSyncNotLazyChannelTest</code></p>
 */
public class NettyLocalSyncNotLazyChannelTest extends BaseNettyTest {
    @Produce(uri = "direct:start")
    protected ProducerTemplate producerTemplate;

    @Test
    public void testTCPStringInOutWithNettyConsumer() throws Exception {
        String response = producerTemplate.requestBody(
            "netty:local://localhost:{{port}}?sync=true&lazyChannelCreation=false",
            "Epitaph in Kohima, India marking the WWII Battle of Kohima and Imphal, Burma Campaign - Attributed to John Maxwell Edmonds", String.class);
        assertEquals("When You Go Home, Tell Them Of Us And Say, For Your Tomorrow, We Gave Our Today.", response);
    }

    @Test
    public void testTCPObjectInOutWithNettyConsumer() throws Exception {
        Poetry poetry = new Poetry();
        Poetry response = (Poetry) producerTemplate.requestBody("netty:local://localhost:{{port}}?sync=true&lazyChannelCreation=false", poetry);
        assertEquals("Dr. Sarojini Naidu", response.getPoet());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty:local://localhost:{{port}}?sync=true")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            if (exchange.getIn().getBody() instanceof Poetry) {
                                Poetry poetry = (Poetry) exchange.getIn().getBody();
                                poetry.setPoet("Dr. Sarojini Naidu");
                                exchange.getOut().setBody(poetry);
                                return;
                            }
                            exchange.getOut().setBody("When You Go Home, Tell Them Of Us And Say, For Your Tomorrow, We Gave Our Today.");
                        }
                    });
            }
        };
    }

}
