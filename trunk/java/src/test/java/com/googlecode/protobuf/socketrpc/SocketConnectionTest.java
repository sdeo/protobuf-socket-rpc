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

import com.google.protobuf.Message;
import com.googlecode.protobuf.socketrpc.RpcConnectionFactory.Connection;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Request.Builder;

import junit.framework.TestCase;

/**
 * Tests for {@link SocketConnection}.
 *
 * @author Shardul Deo
 */
public class SocketConnectionTest extends TestCase {

  private static final Request MESSAGE1 = Request.newBuilder()
      .setStrData("test data 1")
      .build();

  private static final Request MESSAGE2 = Request.newBuilder()
      .setStrData("test data 2")
      .build();

  private FakeSocket delimited;
  private FakeSocket undelimited;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    delimited = new FakeSocket(true);
    undelimited = new FakeSocket(false);
  }

  public void testClose_delimited() throws IOException {
    doTestClose(delimited, true);
  }

  public void testClose_undelimited() throws IOException {
    doTestClose(undelimited, false);
  }

  private void doTestClose(FakeSocket socket, boolean isDelimited) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    writeToOutputStream(MESSAGE1, os, isDelimited);
    socket.withInputBytes(os.toByteArray());

    Connection connection = new SocketConnection(socket, isDelimited);
    assertFalse(connection.isClosed());
    connection.close();
    assertTrue(connection.isClosed());
  }

  public void testConnectionOutputInput_delimited() throws IOException {
    doTestOutputInput(delimited, true);
  }

  public void testConnectionOutputInput_undelimited() throws IOException {
    doTestOutputInput(undelimited, false);
  }

  private void doTestOutputInput(FakeSocket socket, boolean isDelimited)
      throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    writeToOutputStream(MESSAGE1, os, isDelimited);
    socket.withInputBytes(os.toByteArray());

    Connection connection = new SocketConnection(socket, isDelimited);
    connection.sendProtoMessage(MESSAGE1);
    ByteArrayInputStream is = new ByteArrayInputStream(socket.getOutputBytes());
    assertEquals(MESSAGE1, readFromInputStream(is, isDelimited));

    Builder builder = Request.newBuilder();
    connection.receiveProtoMessage(builder);
    assertEquals(MESSAGE1, builder.build());
  }

  public void testConnectionInputOutput_delimited() throws IOException {
    doTestInputOutput(delimited, true);
  }

  public void testConnectionInputOutput_undelimited() throws IOException {
    doTestInputOutput(undelimited, false);
  }

  public void doTestInputOutput(FakeSocket socket, boolean isDelimited)
      throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    writeToOutputStream(MESSAGE1, os, isDelimited);
    socket.withInputBytes(os.toByteArray());

    Connection connection = new SocketConnection(socket, isDelimited);
    Builder builder = Request.newBuilder();
    connection.receiveProtoMessage(builder);
    assertEquals(MESSAGE1, builder.build());

    connection.sendProtoMessage(MESSAGE1);
    ByteArrayInputStream is = new ByteArrayInputStream(socket.getOutputBytes());
    assertEquals(MESSAGE1, readFromInputStream(is, isDelimited));
  }

  public void testDoMultiple() throws IOException {
    // Put 3 messages in the input
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    writeToOutputStream(MESSAGE1, os, true);
    writeToOutputStream(MESSAGE2, os, true);
    writeToOutputStream(MESSAGE1, os, true);
    delimited.withInputBytes(os.toByteArray());

    // Send and receive first
    Connection connection = new SocketConnection(delimited, true);
    connection.sendProtoMessage(MESSAGE2);
    Builder builder = Request.newBuilder();
    connection.receiveProtoMessage(builder);
    assertEquals(MESSAGE1, builder.build());

    // Receive and send second
    builder = Request.newBuilder();
    connection.receiveProtoMessage(builder);
    assertEquals(MESSAGE2, builder.build());
    connection.sendProtoMessage(MESSAGE1);

    // Send and receive third
    connection.sendProtoMessage(MESSAGE2);
    builder = Request.newBuilder();
    connection.receiveProtoMessage(builder);
    assertEquals(MESSAGE1, builder.build());

    // Get 3 messages from output
    ByteArrayInputStream is = new ByteArrayInputStream(
        delimited.getOutputBytes());
    assertEquals(MESSAGE2, readFromInputStream(is, true));
    assertEquals(MESSAGE1, readFromInputStream(is, true));
    assertEquals(MESSAGE2, readFromInputStream(is, true));
  }

  private static Message readFromInputStream(ByteArrayInputStream is,
      boolean isDelimited) throws IOException {
    if (isDelimited) {
      return Request.parseDelimitedFrom(is);
    } else {
      return Request.newBuilder().mergeFrom(is).build();
    }
  }

  private static void writeToOutputStream(Message message,
      ByteArrayOutputStream os, boolean isDelimited) throws IOException {
    if (isDelimited) {
      message.writeDelimitedTo(os);
    } else {
      message.writeTo(os);
    }
  }
}
