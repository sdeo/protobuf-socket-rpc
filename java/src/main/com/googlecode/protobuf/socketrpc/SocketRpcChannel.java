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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.RpcController;
import com.google.protobuf.UninitializedMessageException;
import com.google.protobuf.Descriptors.MethodDescriptor;

/**
 * Socket implementation of {@link RpcChannel}. Makes the rpc to a server
 * running {@link SocketRpcServer} with the rpc method implementation running on
 * it.
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

  /**
   * Create a channel for TCP/IP RPCs.
   */
  public SocketRpcChannel(String host, int port) {
    this.host = host;
    this.port = port;
  }

  /**
   * Create new rpc controller to be used to control one request.
   */
  public SocketRpcController newRpcController() {
    return new SocketRpcController();
  }

  @Override
  public void callMethod(MethodDescriptor method, RpcController controller,
      Message request, Message responsePrototype, RpcCallback<Message> done) {
    // Must pass in a SocketRpcController
    SocketRpcController socketController = (SocketRpcController) controller;
    Socket socket = null;
    OutputStream out = null;
    InputStream in = null;

    // Open socket
    try {
      socket = new Socket(host, port);
      out = new BufferedOutputStream(socket.getOutputStream());
      in = new BufferedInputStream(socket.getInputStream());
    } catch (UnknownHostException e) {
      handleError(socketController, "Could not find host: " + host, e);
      return;
    } catch (IOException e) {
      handleError(socketController, String.format(
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
      SocketRpcProtos.Response rpcResponse = SocketRpcProtos.Response
          .newBuilder().mergeFrom(in).build();
      handleRpcResponse(responsePrototype, rpcResponse, socketController, done);
    } catch (IOException e) {
      handleError(socketController, String.format(
          "Error reading/writing for %s:%s, %s", host, port), e);
    } catch (UninitializedMessageException e) {
      handleError(socketController, "Bad response from server", e);
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
      handleError(socketController, rpcResponse.getError(), null);
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
      Message response = responsePrototype.newBuilderForType().mergeFrom(
          rpcResponse.getResponseProto()).build();
      callback.run(response);
    } catch (InvalidProtocolBufferException e) {
      handleError(socketController, "Response could be parsed as "
          + responsePrototype.getClass().getName(), e);
    } catch (UninitializedMessageException e) {
      handleError(socketController, "Uninitialized RPC Response Proto", e);
    }
  }

  private void handleError(SocketRpcController socketController,
      String msg, Exception e) {
    LOG.log(Level.WARNING, msg, e);
    socketController.success = false;
    socketController.error = msg;
  }
}
