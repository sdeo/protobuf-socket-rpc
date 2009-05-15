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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.SocketFactory;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.RpcController;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.ErrorReason;

/**
 * Socket implementation of {@link RpcChannel}. Makes a synchronous rpc call to
 * a server running {@link SocketRpcServer} with the rpc method implementation
 * running on it.
 * <p>
 * If an {@link RpcCallback} is given, the
 * {@link #callMethod(MethodDescriptor, RpcController, Message, Message, RpcCallback)}
 * method will invoke it with the same protobuf that the rpc method
 * implementation on the server side invoked the callback with, or will not
 * invoke it if that was the case on the server side.
 *
 * @author Shardul Deo
 */
public class SocketRpcChannel implements RpcChannel {

  private final static Logger LOG =
    Logger.getLogger(SocketRpcChannel.class.getName());

  private final String host;
  private final int port;
  private final SocketFactory socketFactory;

  /**
   * Create a channel for TCP/IP RPCs.
   */
  public SocketRpcChannel(String host, int port) {
    this.host = host;
    this.port = port;
    this.socketFactory = SocketFactory.getDefault();
  }

  // Used for testing
  SocketRpcChannel(String host, int port, SocketFactory socketFactory) {
    this.host = host;
    this.port = port;
    this.socketFactory = socketFactory;
  }

  /**
   * Create new rpc controller to be used to control one request.
   */
  public SocketRpcController newRpcController() {
    return new SocketRpcController();
  }

  public void callMethod(MethodDescriptor method, RpcController controller,
      Message request, Message responsePrototype, RpcCallback<Message> done) {
    // Must pass in a SocketRpcController
    SocketRpcController socketController = (SocketRpcController) controller;
    Socket socket = null;
    OutputStream out = null;
    InputStream in = null;

    // Check request
    if (!request.isInitialized()) {
      handleError(socketController, ErrorReason.INVALID_REQUEST_PROTO,
          "Request is uninitialized", null);
      return;
    }

    // Open socket
    try {
      socket = socketFactory.createSocket(host, port);
      out = new BufferedOutputStream(socket.getOutputStream());
      in = new BufferedInputStream(socket.getInputStream());
    } catch (UnknownHostException e) {
      handleError(socketController, ErrorReason.UNKNOWN_HOST,
          "Could not find host: " + host, e);
      return;
    } catch (IOException e) {
      handleError(socketController, ErrorReason.IO_ERROR, String.format(
          "Could not open I/O for %s:%s", host, port), e);
      if (socket != null) {
        try {
          socket.close();
        } catch (IOException ioe) {
          // It's ok
        }
      }
      return;
    }

    // Create RPC request protobuf
    SocketRpcProtos.Request rpcRequest = SocketRpcProtos.Request.newBuilder()
        .setRequestProto(request.toByteString())
        .setServiceName(method.getService().getFullName())
        .setMethodName(method.getName())
        .build();

    try {
      // Write request
      rpcRequest.writeTo(out);
      out.flush();
      socket.shutdownOutput();

      // Read and handle response
      SocketRpcProtos.Response.Builder builder = SocketRpcProtos.Response
          .newBuilder().mergeFrom(in);
      if (!builder.isInitialized()) {
        handleError(socketController, ErrorReason.BAD_RESPONSE_PROTO,
            "Bad response from server", null);
        return;
      }
      SocketRpcProtos.Response rpcResponse = builder.build();
      handleRpcResponse(responsePrototype, rpcResponse, socketController, done);
    } catch (IOException e) {
      handleError(socketController, ErrorReason.IO_ERROR, String.format(
          "Error reading/writing for %s:%s", host, port), e);
    } finally {
      try {
        out.close();
        in.close();
        socket.close();
      } catch (IOException e) {
        LOG.log(Level.WARNING, "Error closing I/O", e);
      }
    }
  }

  private void handleRpcResponse(Message responsePrototype,
      SocketRpcProtos.Response rpcResponse,
      SocketRpcController socketController, RpcCallback<Message> callback) {

    // Check for error
    socketController.success = true;
    if (rpcResponse.hasError()) {
      handleError(socketController, rpcResponse.getErrorReason(),
          rpcResponse.getError(), null);
    }

    if ((callback == null) || !rpcResponse.getCallback()) {
      // No callback needed
      return;
    }

    if (!rpcResponse.hasResponseProto()) {
      // Callback was called with null on server side
      callback.run(null);
      return;
    }

    try {
      Message.Builder builder = responsePrototype.newBuilderForType()
          .mergeFrom(rpcResponse.getResponseProto());
      if (!builder.isInitialized()) {
        handleError(socketController, ErrorReason.BAD_RESPONSE_PROTO,
            "Uninitialized RPC Response Proto", null);
        return;
      }
      Message response = builder.build();
      callback.run(response);
    } catch (InvalidProtocolBufferException e) {
      handleError(socketController, ErrorReason.BAD_RESPONSE_PROTO,
          "Response could be parsed as "
              + responsePrototype.getClass().getName(), e);
    }
  }


  private void handleError(SocketRpcController socketController,
      ErrorReason reason, String msg, Exception e) {
    LOG.log(Level.WARNING, reason + ": " + msg, e);
    socketController.success = false;
    socketController.reason = reason;
    socketController.error = msg;
  }
}
