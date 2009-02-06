package com.googlecode.protobuf.socketrpc;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;

/**
 * Simple controller.
 * 
 * @author Shardul Deo
 */
public class SocketRpcController implements RpcController {
  
  boolean success = false;
  String error = null;

  @Override
  public void reset() {
    success = false;
    error = null;
  }

  @Override
  public boolean failed() {
    return !success;
  }

  @Override
  public String errorText() {
    return error;
  }

  @Override
  public void startCancel() {
    // Not yet supported
    throw new UnsupportedOperationException(
        "Cannot cancel request in Socket RPC");
  }

  @Override
  public void setFailed(String reason) {
    success = false;
    error = reason;
  }

  @Override
  public boolean isCanceled() {
    // Not yet supported
    throw new UnsupportedOperationException(
        "Cannot cancel request in Socket RPC");
  }

  @Override
  public void notifyOnCancel(RpcCallback<Object> callback) {
    // Not yet supported
    throw new UnsupportedOperationException(
        "Cannot cancel request in Socket RPC");
  }
}
