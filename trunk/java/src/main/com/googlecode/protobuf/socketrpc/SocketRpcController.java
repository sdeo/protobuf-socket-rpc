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

  public void reset() {
    success = false;
    error = null;
  }

  public boolean failed() {
    return !success;
  }

  public String errorText() {
    return error;
  }

  public void startCancel() {
    // Not yet supported
    throw new UnsupportedOperationException(
        "Cannot cancel request in Socket RPC");
  }

  public void setFailed(String reason) {
    success = false;
    error = reason;
  }

  public boolean isCanceled() {
    // Not yet supported
    throw new UnsupportedOperationException(
        "Cannot cancel request in Socket RPC");
  }

  public void notifyOnCancel(RpcCallback<Object> callback) {
    // Not yet supported
    throw new UnsupportedOperationException(
        "Cannot cancel request in Socket RPC");
  }
}
