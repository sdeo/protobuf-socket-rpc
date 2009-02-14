package com.googlecode.protobuf.socketrpc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.Response.ServerErrorReason;

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
      ServerErrorReason reason) {
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
  public InputStream getInputStream() throws IOException {
    return input;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    output = new ByteArrayOutputStream();
    return output;
  }

  @Override
  public void shutdownOutput() throws IOException {
    // no-op
  }

  @Override
  public synchronized void close() throws IOException {
    // no-op
  }
}
