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
import com.googlecode.protobuf.socketrpc.SocketRpcController.ErrorReason;

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
      handleError(socketController, ErrorReason.BadRequestProto,
          "Request is uninitialized", null);
      return;
    }

    // Open socket
    try {
      socket = socketFactory.createSocket(host, port);
      out = new BufferedOutputStream(socket.getOutputStream());
      in = new BufferedInputStream(socket.getInputStream());
    } catch (UnknownHostException e) {
      handleError(socketController, ErrorReason.UnknownHost,
          "Could not find host: " + host, e);
      return;
    } catch (IOException e) {
      handleError(socketController, ErrorReason.IOError, String.format(
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
        handleError(socketController, ErrorReason.ServerError,
            "Bad response from server", null);
        return;
      }
      SocketRpcProtos.Response rpcResponse = builder.build();
      handleRpcResponse(responsePrototype, rpcResponse, socketController, done);
    } catch (IOException e) {
      handleError(socketController, ErrorReason.IOError, String.format(
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
      handleError(socketController, ErrorReason.ServerError,
          rpcResponse.getError(), null);
    }

    if ((callback == null) || !rpcResponse.hasResponseProto()) {
      // No callback needed
      return;
    }

    if (rpcResponse.getResponseProto().isEmpty()) {
      // Callback was called with null on server side
      callback.run(null);
      return;
    }

    try {
      Message.Builder builder = responsePrototype.newBuilderForType()
          .mergeFrom(rpcResponse.getResponseProto());
      if (!builder.isInitialized()) {
        handleError(socketController, ErrorReason.BadResponseProto,
            "Uninitialized RPC Response Proto", null);
        return;
      }
      Message response = builder.build();
      callback.run(response);
    } catch (InvalidProtocolBufferException e) {
      handleError(socketController, ErrorReason.BadResponseProto,
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
