/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.netty;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalAddress;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyConsumer extends DefaultConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(NettyConsumer.class);
    private final ChannelGroup allChannels;
    private CamelContext context;
    private NettyConfiguration configuration;
    private ChannelFactory channelFactory;
    private DatagramChannelFactory datagramChannelFactory;
    private DefaultLocalServerChannelFactory localChannelFactory;
    private ServerBootstrap serverBootstrap;
    private ConnectionlessBootstrap connectionlessServerBootstrap;
    private Channel channel;

    public NettyConsumer(NettyEndpoint nettyEndpoint, Processor processor, NettyConfiguration configuration) {
        super(nettyEndpoint, processor);
        this.context = this.getEndpoint().getCamelContext();
        this.configuration = configuration;
        this.allChannels = new DefaultChannelGroup("NettyProducer-" + nettyEndpoint.getEndpointUri());
    }

    @Override
    public NettyEndpoint getEndpoint() {
        return (NettyEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        LOG.debug("Netty consumer binding to: {}", configuration.getAddress());

        super.doStart();
        if (isTcp()) {
            initializeTCPServerSocketCommunicationLayer();
        } else if (isUdp()) {
            initializeUDPServerSocketCommunicationLayer();
        } else if (isLocal()) {
        	initializeLocalSocketCommunicationLayer();
        } else {
        	throw new Exception("Unrecognized Protocol [" + configuration.getProtocol() + "]", new Throwable());
        }

        LOG.info("Netty consumer bound to: " + configuration.getAddress());
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Netty consumer unbinding from: {}", configuration.getAddress());

        // close all channels
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();

        // and then release other resources
        if (channelFactory != null) {
            channelFactory.releaseExternalResources();
        }

        super.doStop();

        LOG.info("Netty consumer unbound from: " + configuration.getAddress());
    }

    public CamelContext getContext() {
        return context;
    }

    public ChannelGroup getAllChannels() {
        return allChannels;
    }

    public NettyConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(NettyConfiguration configuration) {
        this.configuration = configuration;
    }

    public ChannelFactory getChannelFactory() {
        return channelFactory;
    }

    public void setChannelFactory(ChannelFactory channelFactory) {
        this.channelFactory = channelFactory;
    }

    public DatagramChannelFactory getDatagramChannelFactory() {
        return datagramChannelFactory;
    }

    public void setDatagramChannelFactory(DatagramChannelFactory datagramChannelFactory) {
        this.datagramChannelFactory = datagramChannelFactory;
    }

    public ServerBootstrap getServerBootstrap() {
        return serverBootstrap;
    }

    public void setServerBootstrap(ServerBootstrap serverBootstrap) {
        this.serverBootstrap = serverBootstrap;
    }

    public ConnectionlessBootstrap getConnectionlessServerBootstrap() {
        return connectionlessServerBootstrap;
    }

    public void setConnectionlessServerBootstrap(ConnectionlessBootstrap connectionlessServerBootstrap) {
        this.connectionlessServerBootstrap = connectionlessServerBootstrap;
    }

    protected boolean isTcp() {
        return configuration.getProtocol().equalsIgnoreCase("tcp");
    }
    protected boolean isUdp() {
        return configuration.getProtocol().equalsIgnoreCase("udp");
    }
    protected boolean isLocal() {
        return configuration.getProtocol().equalsIgnoreCase("local");
    }
    

    private void initializeTCPServerSocketCommunicationLayer() throws Exception {
        ExecutorService bossExecutor = context.getExecutorServiceManager().newThreadPool(this, "NettyTCPBoss",
                configuration.getCorePoolSize(), configuration.getMaxPoolSize());
        ExecutorService workerExecutor = context.getExecutorServiceManager().newThreadPool(this, "NettyTCPWorker",
                configuration.getCorePoolSize(), configuration.getMaxPoolSize());

        if (configuration.getWorkerCount() == 0) {
            channelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor);
        } else {
            channelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor,
                                                               configuration.getWorkerCount());
        }
        serverBootstrap = new ServerBootstrap(channelFactory);
        if (configuration.getServerPipelineFactory() != null) {
            configuration.getServerPipelineFactory().setConsumer(this);
            serverBootstrap.setPipelineFactory(configuration.getServerPipelineFactory());
        } else {
            serverBootstrap.setPipelineFactory(new DefaultServerPipelineFactory(this));
        }
        serverBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
        serverBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
        serverBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
        serverBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeout());

        channel = serverBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        // to keep track of all channels in use
        allChannels.add(channel);
    }

    private void initializeUDPServerSocketCommunicationLayer() throws Exception {
        ExecutorService workerExecutor = context.getExecutorServiceManager().newThreadPool(this, "NettyUDPWorker",
                configuration.getCorePoolSize(), configuration.getMaxPoolSize());

        datagramChannelFactory = new NioDatagramChannelFactory(workerExecutor);
        connectionlessServerBootstrap = new ConnectionlessBootstrap(datagramChannelFactory);
        if (configuration.getServerPipelineFactory() != null) {
            configuration.getServerPipelineFactory().setConsumer(this);
            connectionlessServerBootstrap.setPipelineFactory(configuration.getServerPipelineFactory());
        } else {
            connectionlessServerBootstrap.setPipelineFactory(new DefaultServerPipelineFactory(this));
        }
        connectionlessServerBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
        connectionlessServerBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
        connectionlessServerBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
        connectionlessServerBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeout());
        connectionlessServerBootstrap.setOption("child.broadcast", configuration.isBroadcast());
        connectionlessServerBootstrap.setOption("sendBufferSize", configuration.getSendBufferSize());
        connectionlessServerBootstrap.setOption("receiveBufferSize", configuration.getReceiveBufferSize());
        // only set this if user has specified
        if (configuration.getReceiveBufferSizePredictor() > 0) {
            connectionlessServerBootstrap.setOption("receiveBufferSizePredictorFactory",
                new FixedReceiveBufferSizePredictorFactory(configuration.getReceiveBufferSizePredictor()));
        }

        channel = connectionlessServerBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        // to keep track of all channels in use
        allChannels.add(channel);
    }
    
    private void initializeLocalSocketCommunicationLayer() throws Exception {

    	localChannelFactory = new DefaultLocalServerChannelFactory();
        serverBootstrap = new ServerBootstrap(localChannelFactory);
        if (configuration.getServerPipelineFactory() != null) {
            configuration.getServerPipelineFactory().setConsumer(this);
            serverBootstrap.setPipelineFactory(configuration.getServerPipelineFactory());
        } else {
            serverBootstrap.setPipelineFactory(new DefaultServerPipelineFactory(this));
        }
        serverBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());

        channel = serverBootstrap.bind(new LocalAddress(configuration.getLocalName()));
        // to keep track of all channels in use
        allChannels.add(channel);
    }
    

}
