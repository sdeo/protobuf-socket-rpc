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

import java.io.IOException;

import junit.framework.TestCase;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.google.protobuf.MessageLite.Builder;
import com.googlecode.protobuf.socketrpc.RpcConnectionFactory.Connection;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Response;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService;

/**
 * Tests for {@link RpcServer}. Only contains async tests. Rest is tested by
 * {@link SocketRpcServerTest}.
 *
 * @author Shardul Deo
 */
public class RpcServerTest extends TestCase {

  private static final Request REQUEST;
  private static final SocketRpcProtos.Request RPC_REQUEST;

  static {
    REQUEST = Request.newBuilder().setStrData("Request Data").build();
    RPC_REQUEST = createRpcRequest(TestService.getDescriptor().getFullName(),
        TestService.getDescriptor().getMethods().get(0).getName(),
        REQUEST.toByteString());
  }

  private RpcServer rpcServer;
  private FakeServiceImpl fakeServiceImpl;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    rpcServer = new RpcServer(null, null,
        false /* closeConnectionAfterInvokingService */);
    fakeServiceImpl = new FakeServiceImpl();
  }

  /**
   * Fake Service impl for testing.
   */
  private class FakeServiceImpl extends TestService {

    private RpcCallback<Response> callback;

    @Override
    public void testMethod(RpcController controller, Request request,
        RpcCallback<Response> done) {
      assertEquals(REQUEST, request);
      callback = done;
    }
  }

  /**
   * Fake connection impl for testing.
   */
  private static class FakeConnection implements Connection {

    private final SocketRpcProtos.Request rpcRequest;
    private boolean closed = false;
    private SocketRpcProtos.Response response = null;

    private FakeConnection(SocketRpcProtos.Request rpcRequest) {
      this.rpcRequest = rpcRequest;
    }

    @Override
    public void receiveProtoMessage(Builder messageBuilder) throws IOException {
      messageBuilder.mergeFrom(rpcRequest.toByteArray());
    }

    @Override
    public void sendProtoMessage(MessageLite message) {
      response = (SocketRpcProtos.Response) message;
    }

    @Override
    public void close() {
      closed = true;
    }

    @Override
    public boolean isClosed() {
      return closed;
    }
  }

  private void runHandler(Connection connection) {
    rpcServer.new ConnectionHandler(connection).run();
  }

  /**
   * Test async request/response.
   */
  public void testAsyncRpc_goodResponse() {
    // Create data
    Response response = Response.newBuilder()
        .setStrData("Response Data")
        .build();

    // Create fakes
    FakeConnection fakeConnection = new FakeConnection(RPC_REQUEST);
    rpcServer.registerService(fakeServiceImpl);

    // Call handler
    runHandler(fakeConnection);

    // Check that connection is open and no callback yet
    assertFalse(fakeConnection.closed);
    assertNull(fakeConnection.response);

    // Do delayed callback
    fakeServiceImpl.callback.run(response);

    // Verify result
    assertTrue(fakeConnection.response.getCallback());
    assertEquals(response.toByteString(),
        fakeConnection.response.getResponseProto());
  }

  /**
   * Test async request with null response.
   */
  public void testAsyncRpc_nullResponse() {
    // Create fakes
    FakeConnection fakeConnection = new FakeConnection(RPC_REQUEST);
    rpcServer.registerService(fakeServiceImpl);

    // Call handler
    runHandler(fakeConnection);

    // Check that connection is open and no callback yet
    assertFalse(fakeConnection.closed);
    assertNull(fakeConnection.response);

    // Do delayed callback
    fakeServiceImpl.callback.run(null);

    // Verify result
    assertTrue(fakeConnection.response.getCallback());
    assertFalse(fakeConnection.response.hasResponseProto());
  }

  private static SocketRpcProtos.Request createRpcRequest(String service,
      String method, ByteString request) {
    return SocketRpcProtos.Request.newBuilder()
        .setServiceName(service)
        .setMethodName(method)
        .setRequestProto(request)
        .build();
  }
}
