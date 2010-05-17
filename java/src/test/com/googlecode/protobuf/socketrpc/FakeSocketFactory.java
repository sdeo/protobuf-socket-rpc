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
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

/**
 * Fake {@link SocketFactory} used for unit tests.
 *
 * @author Shardul Deo
 */
public class FakeSocketFactory extends SocketFactory {

  private Socket socket = null;
  private IOException ioException = null;
  private UnknownHostException unknownHostException = null;

  public FakeSocketFactory returnsSocket(Socket socket) {
    this.socket = socket;
    return this;
  }

  public FakeSocketFactory throwsException(IOException e) {
    ioException = e;
    return this;
  }

  public FakeSocketFactory throwsException(UnknownHostException e) {
    unknownHostException = e;
    return this;
  }

  @Override
  public Socket createSocket() throws IOException {
    if (unknownHostException != null) {
      throw unknownHostException;
    }
    if (ioException != null) {
      throw ioException;
    }
    return socket;
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException,
      UnknownHostException {
    return createSocket();
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    return createSocket();
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost,
      int localPort) throws IOException, UnknownHostException {
    return createSocket();
  }

  @Override
  public Socket createSocket(InetAddress address, int port,
      InetAddress localAddress, int localPort) throws IOException {
    return createSocket();
  }
}