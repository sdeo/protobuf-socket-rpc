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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ServerSocketFactory;

import com.google.protobuf.ByteString;
import com.googlecode.protobuf.socketrpc.RpcConnectionFactory.Connection;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.Request;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.Response;

import junit.framework.TestCase;

/**
 * Tests for {@link SocketServerRpcConnectionFactory}.
 *
 * @author Shardul Deo
 */
public class SocketServerRpcConnectionFactoryTest extends TestCase {

  private FakeServerSocketFactory fakeSocketFactory;
  private SocketServerRpcConnectionFactory socketServerConnectionFactory;

  private boolean closed = false;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    fakeSocketFactory = new FakeServerSocketFactory();
    socketServerConnectionFactory = new SocketServerRpcConnectionFactory(0,
        0, null, true, fakeSocketFactory);
  }

  public void testCreateConnection() throws IOException {
    Request request1 = createRequest(1);
    FakeSocket socket1 = new FakeSocket(true).withRequest(request1);
    fakeSocketFactory.returnsSocket(socket1);
    Connection connection1 = socketServerConnectionFactory.createConnection();

    Request request2 = createRequest(1);
    FakeSocket socket2 = new FakeSocket(true).withRequest(request2);
    fakeSocketFactory.returnsSocket(socket2);
    Connection connection2 = socketServerConnectionFactory.createConnection();

    Request.Builder builder = Request.newBuilder();
    connection1.receiveProtoMessage(builder);
    assertEquals(request1, builder.build());

    builder = Request.newBuilder();
    connection2.receiveProtoMessage(builder);
    assertEquals(request2, builder.build());

    Response response1 = createResponse(1);
    connection1.sendProtoMessage(response1);

    Response response2 = createResponse(2);
    connection2.sendProtoMessage(response2);

    assertEquals(response1, socket1.getResponse());
    assertEquals(response2, socket2.getResponse());
  }

  public void testClosed() throws IOException {
    FakeSocket socket = new FakeSocket(true).withRequest(createRequest(1));
    fakeSocketFactory.returnsSocket(socket);
    socketServerConnectionFactory.createConnection();
    assertFalse(closed);
    socketServerConnectionFactory.close();
    assertTrue(closed);
  }

  private Request createRequest(int index) {
    Request request = Request.newBuilder()
        .setMethodName("method" + index)
        .setServiceName("service" + index)
        .setRequestProto(ByteString.EMPTY)
        .build();
    return request;
  }

  private Response createResponse(int index) {
    Response response = Response.newBuilder()
        .setError("Error" + index)
        .build();
    return response;
  }

  private class FakeServerSocketFactory extends ServerSocketFactory {

    private FakeSocket socket;

    private FakeServerSocketFactory returnsSocket(FakeSocket fakeSocket) {
      this.socket = fakeSocket;
      return this;
    }

    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
      return createServerSocket(port, 0);
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog)
        throws IOException {
      return createServerSocket(port, backlog, null);
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog,
        InetAddress ifAddress) throws IOException {
      return new ServerSocket() {

        @Override
        public Socket accept() {
          return socket;
        }

        @Override
        public void close() {
          closed = true;
        }

        @Override
        public boolean isClosed() {
          return closed;
        }
      };
    }
  }
}
