package com.googlecode.protobuf.socketrpc;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;

/**
 * Simple controller.
 *
 * @author Shardul Deo
 */
public class SocketRpcController implements RpcController {

  /**
   * Error reason, to be used client side
   */
  public enum ErrorReason {
    /** Rpc was called with bad request proto */
    BadRequestProto,
    /** Rpc returned a bad response proto */
    BadResponseProto,
    /** Could not find supplied host */
    UnknownHost,
    /** I/O error while communicating with server */
    IOError,
    /** Server returned an error while running the rpc */
    ServerError,
  }

  boolean success = false;
  String error = null;
  ErrorReason reason = null;

  public void reset() {
    success = false;
    error = null;
  }

  public boolean failed() {
    return !success;
  }

  /**
   * @return Reason for rpc error, to be used client side
   */
  public ErrorReason errorReason() {
    return reason;
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
