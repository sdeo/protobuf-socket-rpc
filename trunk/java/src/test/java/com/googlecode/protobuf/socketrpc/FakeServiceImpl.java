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

import junit.framework.Assert;

import com.google.protobuf.BlockingService;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Response;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService;

/**
 * Fake service implementation useful for unit testing.
 *
 * @author Shardul Deo
 */
public class FakeServiceImpl extends TestService implements
    TestService.BlockingInterface, TestService.Interface {

  private final Request expectedRequest;;
  private String error = null;
  private RuntimeException rex = null;
  private boolean invokeCallback = false;
  private Response response = null;

  public FakeServiceImpl(Request expectedRequest) {
    this.expectedRequest = expectedRequest;
  }

  public FakeServiceImpl failsWithError(String error) {
    this.error = error;
    return this;
  }

  public FakeServiceImpl throwsException(RuntimeException e) {
    this.rex = e;
    return this;
  }

  public FakeServiceImpl withResponse(Response response) {
    invokeCallback = true;
    this.response = response;
    return this;
  }

  @Override
  public void testMethod(RpcController controller, Request request,
      final RpcCallback<Response> done) {
    Assert.assertEquals(expectedRequest, request);
    if (error != null) {
      controller.setFailed(error);
    }
    if (rex != null) {
      throw rex;
    }
    if (!invokeCallback) {
      return;
    }
    done.run(response);
  }

  @Override
  public Response testMethod(RpcController controller, Request request)
      throws ServiceException {
    Assert.assertEquals(expectedRequest, request);
    if (error != null) {
      controller.setFailed(error);
      throw new ServiceException(error);
    }
    if (rex != null) {
      throw rex;
    }
    return response;
  }

  public BlockingService toBlockingService() {
    return TestService.newReflectiveBlockingService(this);
  }
}