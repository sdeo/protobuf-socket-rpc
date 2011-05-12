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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLite.Builder;
import com.googlecode.protobuf.socketrpc.RpcConnectionFactory.Connection;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;

/**
 * Tests for {@link PersistentRpcConnectionFactory}.
 *
 * @author Shardul Deo
 */
public class PersistentRpcConnectionFactoryTest extends TestCase {

  private static final Request MESSAGE1 = Request.newBuilder()
      .setStrData("test data 1")
      .build();

  private static final Request MESSAGE2 = Request.newBuilder()
      .setStrData("test data 2")
      .build();

  private AtomicBoolean failed;
  private FakeSocket socket;
  private Connection connection;
  private FakeRpcConnectionFactory factory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    failed = new AtomicBoolean(false);
    socket = new FakeSocket(true /* delimited */);
    factory = new FakeRpcConnectionFactory();

    // Put 3 messages in the input
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    MESSAGE1.writeDelimitedTo(os);
    MESSAGE2.writeDelimitedTo(os);
    MESSAGE1.writeDelimitedTo(os);
    socket.withInputBytes(os.toByteArray());
    connection = new SocketConnection(socket, true /* delimited */);
  }

  public void testClientFactory() throws IOException {
    PersistentRpcConnectionFactory persistentFactory =
        PersistentRpcConnectionFactory.createInstance(factory);
    Connection persistent = doTestFactoryTest(persistentFactory);

    // Check that connection is closed only when factory is closed
    assertFalse(persistent.isClosed());
    assertFalse(connection.isClosed());
    persistentFactory.close();
    assertTrue(persistent.isClosed());
    assertTrue(connection.isClosed());
  }

  public void testServerFactory() throws IOException {
    ServerRpcConnectionFactory persistentFactory =
        PersistentRpcConnectionFactory.createServerInstance(factory);
    Connection persistent = doTestFactoryTest(persistentFactory);

    // Check that connection is closed only when factory is closed
    assertFalse(persistent.isClosed());
    assertFalse(connection.isClosed());
    assertFalse(factory.closed);
    persistentFactory.close();
    assertTrue(persistent.isClosed());
    assertTrue(connection.isClosed());
    assertTrue(factory.closed);
  }

  public Connection doTestFactoryTest(RpcConnectionFactory persistentFactory)
      throws IOException {
    // Send and receive first
    Connection persistent = persistentFactory.createConnection();
    persistent.sendProtoMessage(MESSAGE2);
    Builder builder = Request.newBuilder();
    persistent.receiveProtoMessage(builder);
    assertEquals(MESSAGE1, builder.build());
    persistent.close();

    // Receive and send second
    persistent = persistentFactory.createConnection();
    builder = Request.newBuilder();
    persistent.receiveProtoMessage(builder);
    assertEquals(MESSAGE2, builder.build());
    persistent.sendProtoMessage(MESSAGE1);
    persistent.close();

    // Send and receive third
    persistent = persistentFactory.createConnection();
    persistent.sendProtoMessage(MESSAGE2);
    builder = Request.newBuilder();
    persistent.receiveProtoMessage(builder);
    assertEquals(MESSAGE1, builder.build());
    persistent.close();

    // Get 3 messages from output
    ByteArrayInputStream is = new ByteArrayInputStream(
        socket.getOutputBytes());
    assertEquals(MESSAGE2, Request.parseDelimitedFrom(is));
    assertEquals(MESSAGE1, Request.parseDelimitedFrom(is));
    assertEquals(MESSAGE2, Request.parseDelimitedFrom(is));
    return persistent;
  }

  public void testServerConcurrency() throws InterruptedException, IOException {
    final ServerRpcConnectionFactory persistentFactory =
        PersistentRpcConnectionFactory.createServerInstance(factory);
    FakeConnection fakeConnection = new FakeConnection();
    connection = fakeConnection;

    final AtomicInteger count = new AtomicInteger();
    final AtomicBoolean runServer = new AtomicBoolean(true);
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          while (runServer.get()) {
            Connection connection = persistentFactory.createConnection();
            count.incrementAndGet();
            receiveRequest(connection);
          }
        } catch (IOException e) {
          failed.set(true);
          throw new RuntimeException(e);
        }
      }
    }).start();

    for (int i = 1; i < 5; i++) {
      // Wait some time for all threads to get locked
      Thread.sleep(100);
      assertEquals(i, count.get());
      assertEquals(1, fakeConnection.incoming.getQueueLength());

      // Release incoming
      fakeConnection.incoming.release();
    }

    // Stop server
    runServer.set(false);
    fakeConnection.incoming.release();

    // Close factory
    assertFalse(fakeConnection.isClosed());
    assertFalse(factory.closed);
    persistentFactory.close();
    assertTrue(fakeConnection.isClosed());
    assertTrue(factory.closed);
  }

  private void receiveRequest(final Connection connection) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        Builder builder = Request.newBuilder();
        try {
          connection.receiveProtoMessage(builder);
          connection.sendProtoMessage(MESSAGE2);
        } catch (IOException e) {
          failed.set(true);
          throw new RuntimeException(e);
        }
        assertEquals(MESSAGE1, builder.build());
      }
    }).start();
  }

  private class FakeConnection implements Connection {

    private boolean closed = false;
    private final Semaphore incoming = new Semaphore(0);

    @Override
    public void close() {
      closed = true;
    }

    @Override
    public boolean isClosed() {
      return closed;
    }

    @Override
    public void receiveProtoMessage(Builder messageBuilder) throws IOException {
      try {
        incoming.acquire();
      } catch (InterruptedException e) {
        failed.set(true);
        throw new RuntimeException(e);
      }
      messageBuilder.mergeFrom(MESSAGE1.toByteString());
    }

    @Override
    public void sendProtoMessage(MessageLite message) {
      assertEquals(MESSAGE2, message);
    }
  }

  private class FakeRpcConnectionFactory implements ServerRpcConnectionFactory {

    private boolean created = false;
    private boolean closed = false;

    @Override
    public void close() {
      closed = true;
    }

    @Override
    public Connection createConnection() {
      // Should only be called once.
      if (created) {
        fail();
        return null;
      }
      created = true;
      return connection;
    }
  }
}
