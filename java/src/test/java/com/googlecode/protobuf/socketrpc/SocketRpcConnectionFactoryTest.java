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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;

import com.googlecode.protobuf.socketrpc.RpcConnectionFactory.Connection;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Request.Builder;

import junit.framework.TestCase;

/**
 * Tests for {@link SocketRpcConnectionFactory}.
 *
 * @author Shardul Deo
 */
public class SocketRpcConnectionFactoryTest extends TestCase {

  private static final Request MESSAGE = Request.newBuilder()
      .setStrData("test data")
      .build();

  private FakeSocketFactory socketFactory;
  private RpcConnectionFactory connectionFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    socketFactory = new FakeSocketFactory();
    connectionFactory = new SocketRpcConnectionFactory("host", 8080,
        socketFactory, true /* delimited */);
  }

  public void testCreateConnection_unknownHost() throws IOException {
    UnknownHostException uhe = new UnknownHostException();
    socketFactory.throwsException(uhe);
    try {
      connectionFactory.createConnection();
    } catch (UnknownHostException e) {
      assertSame(uhe, e);
    }
  }

  public void testCreateConnection_ioError() {
    IOException ioe = new IOException();
    socketFactory.throwsException(ioe);
    try {
      connectionFactory.createConnection();
    } catch (IOException e) {
      assertSame(ioe, e);
    }
  }

  private Connection connectionForSocket(FakeSocket socket) throws IOException {
    socketFactory.returnsSocket(socket);
    return connectionFactory.createConnection();
  }

  public void testConnectionOutputInput() throws IOException {
    FakeSocket socket = new FakeSocket(true);
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    MESSAGE.writeDelimitedTo(os);
    socket.withInputBytes(os.toByteArray());
    Connection connection = connectionForSocket(socket);

    connection.sendProtoMessage(MESSAGE);
    ByteArrayInputStream is = new ByteArrayInputStream(socket.getOutputBytes());
    assertEquals(MESSAGE, Request.parseDelimitedFrom(is));

    Builder builder = Request.newBuilder();
    connection.receiveProtoMessage(builder);
    assertEquals(MESSAGE, builder.build());
  }

  public void testConnectionInputOutput() throws IOException {
    FakeSocket socket = new FakeSocket(true);
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    MESSAGE.writeDelimitedTo(os);
    socket.withInputBytes(os.toByteArray());
    Connection connection = connectionForSocket(socket);

    Builder builder = Request.newBuilder();
    connection.receiveProtoMessage(builder);
    assertEquals(MESSAGE, builder.build());

    connection.sendProtoMessage(MESSAGE);
    ByteArrayInputStream is = new ByteArrayInputStream(socket.getOutputBytes());
    assertEquals(MESSAGE, Request.parseDelimitedFrom(is));
  }
}
