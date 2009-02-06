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

import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.RpcController;
import com.google.protobuf.Descriptors.MethodDescriptor;

/**
 * Socket implementation of {@link RpcChannel}.
 * 
 * @author Shardul Deo
 */
public class SocketRpcChannel implements RpcChannel {

  private final static Logger LOG = 
    Logger.getLogger(SocketRpcChannel.class.getName());
  
  private final String host;
  private final int port;

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
      socketController.success = false;
      socketController.error = "Could not find host: " + host;
      return;
    } catch (IOException e) {
      String msg = String.format("Could not open I/O for %s:%s", host, port);
      LOG.log(Level.WARNING, msg, e);
      socketController.success = false;
      socketController.error = msg;
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

    // Do read/write
    try {
      rpcRequest.writeTo(out);
      out.flush();
      socket.shutdownOutput();
      SocketRpcProtos.Response rpcResponse = SocketRpcProtos.Response
          .newBuilder().mergeFrom(in).build();
      // TODO Return error if bad type
      Message response = responsePrototype.newBuilderForType().mergeFrom(
          rpcResponse.toByteString()).build();
      socketController.success = true;
      done.run(response);
    } catch (IOException e) {
      String msg = String.format("Error reading/writing for %s:%s, %s", host,
          port);
      LOG.log(Level.WARNING, msg, e);
      socketController.success = false;
      socketController.error = msg;
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
}
