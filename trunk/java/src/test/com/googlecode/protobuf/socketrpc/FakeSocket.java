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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.ErrorReason;

/**
 * Fake {@link Socket} used for unit tests.
 *
 * @author Shardul Deo
 */
public class FakeSocket extends Socket {

  private ByteArrayInputStream input;
  private ByteArrayOutputStream output;

  // Methods used when the socket is used as a server socket

  public FakeSocket withRequest(SocketRpcProtos.Request request) {
    input = new ByteArrayInputStream(request.toByteArray());
    return this;
  }

  public SocketRpcProtos.Response getResponse()
      throws InvalidProtocolBufferException {
    return SocketRpcProtos.Response.newBuilder()
        .mergeFrom(output.toByteArray()).build();
  }

  // Methods that can be used for client or server socket

  public FakeSocket withInputBytes(byte[] inputBytes) {
    input = new ByteArrayInputStream(inputBytes);
    return this;
  }

  // Methods used when the socket is used as a client socket

  public FakeSocket withNoResponse(boolean callback) {
    SocketRpcProtos.Response rpcResponse = SocketRpcProtos.Response
        .newBuilder().setCallback(callback).build();
    input = new ByteArrayInputStream(rpcResponse.toByteArray());
    return this;
  }

  public FakeSocket withResponseProto(ByteString response) {
    SocketRpcProtos.Response rpcResponse = SocketRpcProtos.Response
        .newBuilder().setCallback(true).setResponseProto(response).build();
    input = new ByteArrayInputStream(rpcResponse.toByteArray());
    return this;
  }

  public FakeSocket withResponseProto(Message message) {
    SocketRpcProtos.Response rpcResponse = SocketRpcProtos.Response
        .newBuilder().setCallback(true)
        .setResponseProto(message.toByteString()).build();
    input = new ByteArrayInputStream(rpcResponse.toByteArray());
    return this;
  }

  public FakeSocket withErrorResponseProto(String error,
      ErrorReason reason) {
    SocketRpcProtos.Response rpcResponse = SocketRpcProtos.Response
        .newBuilder().setError(error).setErrorReason(reason).build();
    input = new ByteArrayInputStream(rpcResponse.toByteArray());
    return this;
  }

  public SocketRpcProtos.Request getRequest()
      throws InvalidProtocolBufferException {
    return SocketRpcProtos.Request.newBuilder().mergeFrom(output.toByteArray())
        .build();
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
    // no-op
  }
}
