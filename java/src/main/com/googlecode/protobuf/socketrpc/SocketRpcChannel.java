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

import com.google.protobuf.BlockingRpcChannel;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.ErrorReason;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.Response;

/**
 * Socket implementation of {@link RpcChannel}. Makes a synchronous RPC call to
 * a server running {@link SocketRpcServer} with the RPC method implementation
 * running on it.
 * <p>
 * If an {@link RpcCallback} is given, the
 * {@link #callMethod(MethodDescriptor, RpcController, Message, Message, RpcCallback)}
 * method will invoke it with the same protobuf that the RPC method
 * implementation on the server side invoked the callback with, or will not
 * invoke it if that was the case on the server side. If some error occurred,
 * the callback will be invoked with null.
 *
 * @author Shardul Deo
 */
public class SocketRpcChannel implements RpcChannel, BlockingRpcChannel {

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
    try {
      Response rpcResponse = makeSyncSocketRpc(method, socketController,
          request);
      Message response = handleRpcResponse(responsePrototype, rpcResponse,
          socketController);

      if ((done == null) || !rpcResponse.getCallback()) {
        // No callback needed.
        return;
      }
      done.run(response);
    } catch (ServiceException e) {
      // Call done with null
      if (done != null) {
        done.run(null);
      }
    }
  }

  @Override
  public Message callBlockingMethod(MethodDescriptor method,
      RpcController controller, Message request, Message responsePrototype)
      throws ServiceException {
    // Must pass in a SocketRpcController
    SocketRpcController socketController = (SocketRpcController) controller;
    Response rpcResponse = makeSyncSocketRpc(method, socketController,
        request);
    return handleRpcResponse(responsePrototype, rpcResponse, socketController);
  }

  private Response makeSyncSocketRpc(MethodDescriptor method,
      SocketRpcController socketController, Message request)
      throws ServiceException {
    Socket socket = null;
    OutputStream out = null;
    InputStream in = null;

    // Check request
    socketController.success = true;
    if (!request.isInitialized()) {
      handleError(socketController, ErrorReason.INVALID_REQUEST_PROTO,
          "Request is uninitialized", null);
    }

    // Open socket
    try {
      socket = socketFactory.createSocket(host, port);
      out = new BufferedOutputStream(socket.getOutputStream());
      in = new BufferedInputStream(socket.getInputStream());
    } catch (UnknownHostException e) {
      handleError(socketController, ErrorReason.UNKNOWN_HOST,
          "Could not find host: " + host, e);
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
      }
      return builder.build();
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
    // Will never reach here
    return null;
  }

  private Message handleRpcResponse(Message responsePrototype,
      SocketRpcProtos.Response rpcResponse,
      SocketRpcController socketController)
      throws ServiceException {

    // Check for error
    if (rpcResponse.hasError()) {
      handleError(socketController, rpcResponse.getErrorReason(),
          rpcResponse.getError(), null);
    }

    if (!rpcResponse.hasResponseProto()) {
      // No response
      return null;
    }

    try {
      Message.Builder builder = responsePrototype.newBuilderForType()
          .mergeFrom(rpcResponse.getResponseProto());
      if (!builder.isInitialized()) {
        handleError(socketController, ErrorReason.BAD_RESPONSE_PROTO,
            "Uninitialized RPC Response Proto", null);
      }
      return builder.build();
    } catch (InvalidProtocolBufferException e) {
      handleError(socketController, ErrorReason.BAD_RESPONSE_PROTO,
          "Response could be parsed as "
              + responsePrototype.getClass().getName(), e);
    }
    // Should not reach here
    return null;
  }

  private void handleError(SocketRpcController socketController,
      ErrorReason reason, String msg, Exception e)
      throws ServiceException {
    if (e == null) {
      LOG.log(Level.WARNING, reason + ": " + msg);
    } else {
      LOG.log(Level.WARNING, reason + ": " + msg, e);
    }
    socketController.success = false;
    socketController.reason = reason;
    socketController.error = msg;
    throw new ServiceException(msg);
  }
}
