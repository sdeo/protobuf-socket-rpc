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
import java.net.Socket;

import javax.net.SocketFactory;


/**
 * Client-side {@link RpcConnectionFactory} that creates a new socket for every
 * RPC.
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
}
