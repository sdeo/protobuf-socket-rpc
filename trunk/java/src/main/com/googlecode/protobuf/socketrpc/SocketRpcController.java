package com.googlecode.protobuf.socketrpc;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;

public class SocketRpcController implements RpcController {
  
  boolean success = false;
  String error = null;

  @Override
  public String errorText() {
    return error;
  }

  @Override
  public boolean failed() {
    return !success;
  }
  
  @Override
  public boolean isCanceled() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void notifyOnCancel(RpcCallback<Object> callback) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void reset() {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void setFailed(String reason) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void startCancel() {
    // TODO Auto-generated method stub
    
  }
}
