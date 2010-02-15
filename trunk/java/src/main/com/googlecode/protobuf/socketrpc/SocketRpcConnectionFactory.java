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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.net.SocketFactory;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLite.Builder;

/**
 * {@link RpcConnectionFactory} that creates a new socket for every rpc.
 *
 * @author Shardul Deo
 */
public class SocketRpcConnectionFactory implements RpcConnectionFactory {

  private final String host;
  private final int port;
  private final SocketFactory socketFactory;

  /**
   * Constructor to create sockets the given host/port.
   */
  public SocketRpcConnectionFactory(String host, int port) {
    this.host = host;
    this.port = port;
    this.socketFactory = SocketFactory.getDefault();
  }

  // Used for testing
  SocketRpcConnectionFactory(String host, int port,
      SocketFactory socketFactory) {
    this.host = host;
    this.port = port;
    this.socketFactory = socketFactory;
  }

  @Override
  public Connection createConnection() throws IOException {
    // Open socket
    Socket socket = socketFactory.createSocket(host, port);
    return new SocketConnection(socket);
  }

  /**
   * {@link Connection} impl that wraps a {@link Socket}.
   */
  private static class SocketConnection implements Connection {

    private final Socket socket;
    private final OutputStream out;
    private final InputStream in;

    private SocketConnection(Socket socket) throws IOException {
      this.socket = socket;

      // Create input/output streams
      try {
        out = new BufferedOutputStream(socket.getOutputStream());
        in = new BufferedInputStream(socket.getInputStream());
      } catch (IOException e) {
        // Cleanup and rethrow
        try {
          socket.close();
        } catch (IOException ioe) {
          // It's ok
        }
        throw e;
      }
    }

    @Override
    public void sendProtoMessage(MessageLite message) throws IOException {
      // Write message
      message.writeTo(out);
      out.flush();
      socket.shutdownOutput();
    }

    @Override
    public void receiveProtoMessage(Builder messageBuilder) throws IOException {
      // Read message
      messageBuilder.mergeFrom(in);
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }
}
