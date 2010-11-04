// Copyright (c) 2009 Shardul Deo
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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.ErrorReason;

/**
 * Fake {@link Socket} used for unit tests.
 *
 * @author Shardul Deo
 */
public class FakeSocket extends Socket {

  private final boolean delimited;
  private boolean closed = false;

  public FakeSocket(boolean delimited) {
    this.delimited = delimited;
  }

  private ByteArrayInputStream input;
  private ByteArrayOutputStream output;

  // Methods used when the socket is used as a server socket

  private void setMessage(Message message)
      throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    if (delimited) {
      message.writeDelimitedTo(os);
    } else {
      message.writeTo(os);
    }
    input = new ByteArrayInputStream(os.toByteArray());
  }

  public FakeSocket withRequest(SocketRpcProtos.Request request)
      throws IOException {
    setMessage(request);
    return this;
  }

  public SocketRpcProtos.Response getResponse() throws IOException {
    ByteArrayInputStream is = new ByteArrayInputStream(output.toByteArray());
    if (delimited) {
      return SocketRpcProtos.Response.parseDelimitedFrom(is);
    } else {
      return SocketRpcProtos.Response.newBuilder().mergeFrom(is).build();
    }
  }

  // Methods that can be used for client or server socket

  public FakeSocket withInputBytes(byte[] inputBytes) {
    input = new ByteArrayInputStream(inputBytes);
    return this;
  }

  public byte[] getOutputBytes() {
    return output.toByteArray();
  }

  // Methods used when the socket is used as a client socket

  public FakeSocket withNoResponse(boolean callback) throws IOException {
    SocketRpcProtos.Response rpcResponse = SocketRpcProtos.Response
        .newBuilder().setCallback(callback).build();
    setMessage(rpcResponse);
    return this;
  }

  public FakeSocket withResponseProto(ByteString response) throws IOException {
    SocketRpcProtos.Response rpcResponse = SocketRpcProtos.Response
        .newBuilder().setCallback(true).setResponseProto(response).build();
    setMessage(rpcResponse);
    return this;
  }

  public FakeSocket withResponseProto(Message message) throws IOException {
    SocketRpcProtos.Response rpcResponse = SocketRpcProtos.Response
        .newBuilder().setCallback(true)
        .setResponseProto(message.toByteString()).build();
    setMessage(rpcResponse);
    return this;
  }

  public FakeSocket withErrorResponseProto(String error,
      ErrorReason reason) throws IOException {
    SocketRpcProtos.Response rpcResponse = SocketRpcProtos.Response
        .newBuilder().setError(error).setErrorReason(reason).build();
    setMessage(rpcResponse);
    return this;
  }

  public SocketRpcProtos.Request getRequest() throws IOException {
    ByteArrayInputStream is = new ByteArrayInputStream(output.toByteArray());
    if (delimited) {
      return SocketRpcProtos.Request.parseDelimitedFrom(is);
    } else {
      return SocketRpcProtos.Request.newBuilder().mergeFrom(is).build();
    }
  }

  // Overriden methods

  @Override
  public InputStream getInputStream() {
    input.reset();
    return input;
  }

  @Override
  public OutputStream getOutputStream() {
    output = new ByteArrayOutputStream();
    return output;
  }

  @Override
  public void shutdownOutput() {
    // no-op
  }

  @Override
  public synchronized void close() {
    closed = true;
  }

  @Override
  public boolean isClosed() {
    return closed;
  }
}
