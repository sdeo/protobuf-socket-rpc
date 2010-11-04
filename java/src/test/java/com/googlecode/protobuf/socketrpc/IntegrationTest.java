// Copyright (c) 2010 Shardul Deo
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.googlecode.protobuf.socketrpc;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.protobuf.BlockingRpcChannel;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.ServiceException;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Response;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService.BlockingInterface;

import junit.framework.TestCase;

/**
 * @author Shardul Deo
 */
public class IntegrationTest extends TestCase {

  private static final Request REQUEST = Request.newBuilder().setStrData(
      "Request").build();
  private static final Response RESPONSE = Response.newBuilder().setStrData(
      "Response").build();

  private ExecutorService threadPool;
  private RpcConnectionFactory clientConnectionFactory;
  private ServerRpcConnectionFactory serverConnectionFactory;
  private FakeServiceImpl service;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    threadPool = Executors.newFixedThreadPool(10);
    clientConnectionFactory = SocketRpcConnectionFactories
        .createRpcConnectionFactory("localhost", 8080);
    serverConnectionFactory = SocketRpcConnectionFactories
        .createServerRpcConnectionFactory(8080, -1, null);

    service = new FakeServiceImpl(REQUEST).withResponse(RESPONSE);
  }

  public void testBlockingService() throws InterruptedException,
      ServiceException, IOException {
    RpcServer rpcServer = new RpcServer(serverConnectionFactory, threadPool,
        true);
    rpcServer.registerBlockingService(TestService
        .newReflectiveBlockingService(service));

    doTest(rpcServer);
  }

  public void testNonBlockingService() throws InterruptedException,
      ServiceException, IOException {
    RpcServer rpcServer = new RpcServer(serverConnectionFactory, threadPool,
        true);
    rpcServer.registerService(service);

    doTest(rpcServer);
  }

  public void testBlockingService_persistent() throws InterruptedException,
      ServiceException, IOException {
    serverConnectionFactory = PersistentRpcConnectionFactory
        .createServerInstance(serverConnectionFactory);
    RpcServer rpcServer = new RpcServer(serverConnectionFactory, threadPool,
        true);
    rpcServer.registerBlockingService(TestService
        .newReflectiveBlockingService(service));

    clientConnectionFactory = PersistentRpcConnectionFactory
        .createInstance(clientConnectionFactory);
    doTest(rpcServer);
  }

  public void testNonBlockingService_persistent() throws InterruptedException,
      ServiceException, IOException {
    serverConnectionFactory = PersistentRpcConnectionFactory
        .createServerInstance(serverConnectionFactory);
    RpcServer rpcServer = new RpcServer(serverConnectionFactory, threadPool,
        true);
    rpcServer.registerService(service);

    clientConnectionFactory = PersistentRpcConnectionFactory
        .createInstance(clientConnectionFactory);
    doTest(rpcServer);
  }

  private void doTest(RpcServer rpcServer) throws InterruptedException,
      ServiceException, IOException {
    BlockingRpcChannel blockingChannel = RpcChannels
        .newBlockingRpcChannel(clientConnectionFactory);
    RpcChannel channel = RpcChannels.newRpcChannel(clientConnectionFactory,
        threadPool);
    BlockingInterface blockingStub = TestService
        .newBlockingStub(blockingChannel);
    TestService stub = TestService.newStub(channel);

    try {
      rpcServer.startServer();
      Thread.sleep(500);

      doRpc(stub);
      doBlockingRpc(blockingStub);
      doBlockingRpc(blockingStub);
      doRpc(stub);
    } finally {
      Thread.sleep(500);
      System.out.println("Closing Client");
      if (clientConnectionFactory instanceof Closeable) {
        ((PersistentRpcConnectionFactory) clientConnectionFactory).close();
      }
      Thread.sleep(100);
      System.out.println("Closing Server");
      rpcServer.shutDown();
    }
  }

  private static void doRpc(TestService stub) {
    final SocketRpcController controller = new SocketRpcController();
    stub.testMethod(controller, REQUEST, new RpcCallback<Response>() {
      @Override
      public void run(Response response) {
        assertFalse(controller.failed());
        assertEquals(RESPONSE, response);
      }
    });
  }

  private static void doBlockingRpc(BlockingInterface blockingStub)
      throws ServiceException {
    SocketRpcController controller = new SocketRpcController();
    Response response = blockingStub.testMethod(controller, REQUEST);
    assertFalse(controller.failed());
    assertEquals(RESPONSE, response);
  }
}
