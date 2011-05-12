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

import junit.framework.TestCase;

import com.google.protobuf.ByteString;
import com.googlecode.protobuf.socketrpc.RpcForwarder.Callback;
import com.googlecode.protobuf.socketrpc.RpcForwarder.RpcException;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.ErrorReason;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Response;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService;

/**
 * Unit tests for {@link RpcForwarder}.
 *
 * @author Shardul Deo
 */
public class RpcForwarderTest extends TestCase {

  private static final Request REQUEST;
  private static final SocketRpcProtos.Request RPC_REQUEST;

  static {
    REQUEST = Request.newBuilder().setStrData("Request Data").build();
    RPC_REQUEST = createRpcRequest(TestService.getDescriptor().getFullName(),
        TestService.getDescriptor().getMethods().get(0).getName(),
        REQUEST.toByteString());
  }

  private RpcForwarder rpcForwarder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    rpcForwarder = new RpcForwarder();
  }

  /**
   * Test good request/response
   */
  public void testGoodRpc() throws RpcException {
    // Create data
    Response response = Response.newBuilder().setStrData("Response Data")
        .build();

    // Register Service
    rpcForwarder.registerService(TestService.newReflectiveService(new FakeServiceImpl(REQUEST)
        .withResponse(response)));

    // Test doBlockingRpc
    SocketRpcProtos.Response rpcResponse =
        rpcForwarder.doBlockingRpc(RPC_REQUEST);
    assertTrue(rpcResponse.getCallback());
    assertEquals(response.toByteString(), rpcResponse.getResponseProto());

    // Test doRpc
    Callback<SocketRpcProtos.Response> rpcCallback =
        new Callback<SocketRpcProtos.Response>();
    rpcForwarder.doRpc(RPC_REQUEST, rpcCallback);
    assertTrue(rpcCallback.isInvoked());
    rpcResponse = rpcCallback.getResponse();
    assertTrue(rpcResponse.getCallback());
    assertEquals(response.toByteString(), rpcResponse.getResponseProto());

    // Register BlockingService
    response = Response.newBuilder().setStrData("New Data").build();
    rpcForwarder.registerBlockingService(new FakeServiceImpl(REQUEST)
        .withResponse(response).toBlockingService());

    // Test doBlockingRpc
    rpcResponse = rpcForwarder.doBlockingRpc(RPC_REQUEST);
    assertTrue(rpcResponse.getCallback());
    assertEquals(response.toByteString(), rpcResponse.getResponseProto());

    // Test doRpc
    rpcCallback = new Callback<SocketRpcProtos.Response>();
    rpcForwarder.doRpc(RPC_REQUEST, rpcCallback);
    assertTrue(rpcCallback.isInvoked());
    rpcResponse = rpcCallback.getResponse();
    assertTrue(rpcResponse.getCallback());
    assertEquals(response.toByteString(), rpcResponse.getResponseProto());
  }

  /**
   * Successful RPC but callback is not called.
   */
  public void testNoCallback() throws RpcException {
    // Register Service
    rpcForwarder.registerService(new FakeServiceImpl(REQUEST));

    // Test doBlockingRpc
    SocketRpcProtos.Response rpcResponse =
        rpcForwarder.doBlockingRpc(RPC_REQUEST);
    assertFalse(rpcResponse.getCallback());
    assertFalse(rpcResponse.hasResponseProto());

    // Test doRpc
    Callback<SocketRpcProtos.Response> rpcCallback =
        new Callback<SocketRpcProtos.Response>();
    rpcForwarder.doRpc(RPC_REQUEST, rpcCallback);
    assertFalse(rpcCallback.isInvoked());

    // Register BlockingService
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl(REQUEST).toBlockingService());

    // Test doBlockingRpc
    rpcResponse = rpcForwarder.doBlockingRpc(RPC_REQUEST);
    assertTrue(rpcResponse.getCallback());
    assertFalse(rpcResponse.hasResponseProto());

    // Test doRpc
    rpcCallback = new Callback<SocketRpcProtos.Response>();
    rpcForwarder.doRpc(RPC_REQUEST, rpcCallback);
    assertTrue(rpcCallback.isInvoked());
    rpcResponse = rpcCallback.getResponse();
    assertTrue(rpcResponse.getCallback());
    assertFalse(rpcResponse.hasResponseProto());
  }

  /**
   * Successful RPC but callback is called with null.
   */
  public void testNullCallBack() throws RpcException {
    // Register Service
    rpcForwarder.registerService(
        new FakeServiceImpl(REQUEST).withResponse(null));

    // Test doBlockingRpc
    SocketRpcProtos.Response rpcResponse =
        rpcForwarder.doBlockingRpc(RPC_REQUEST);
    assertTrue(rpcResponse.getCallback());
    assertFalse(rpcResponse.hasResponseProto());

    // Test doRpc
    Callback<SocketRpcProtos.Response> rpcCallback =
        new Callback<SocketRpcProtos.Response>();
    rpcForwarder.doRpc(RPC_REQUEST, rpcCallback);
    assertTrue(rpcCallback.isInvoked());
    rpcResponse = rpcCallback.getResponse();
    assertTrue(rpcResponse.getCallback());
    assertFalse(rpcResponse.hasResponseProto());

    // Register BlockingService
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl(REQUEST).withResponse(null).toBlockingService());

    // Test doBlockingRpc
    rpcResponse = rpcForwarder.doBlockingRpc(RPC_REQUEST);
    assertTrue(rpcResponse.getCallback());
    assertFalse(rpcResponse.hasResponseProto());

    // Test doRpc
    rpcCallback = new Callback<SocketRpcProtos.Response>();
    rpcForwarder.doRpc(RPC_REQUEST, rpcCallback);
    assertTrue(rpcCallback.isInvoked());
    rpcResponse = rpcCallback.getResponse();
    assertTrue(rpcResponse.getCallback());
    assertFalse(rpcResponse.hasResponseProto());
  }

  /**
   * Server is called with RPC for unknown service.
   */
  public void testInvalidService() {
    rpcForwarder.registerService(new FakeServiceImpl(REQUEST));
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl(REQUEST).toBlockingService());

    // Test doBlockingRpc
    try {
      rpcForwarder.doBlockingRpc(
          createRpcRequest("BadService", "", REQUEST.toByteString()));
      fail("Should have failed");
    } catch (RpcException e) {
      assertEquals(ErrorReason.SERVICE_NOT_FOUND, e.errorReason);
    }

    // Test doRpc
    try {
      rpcForwarder.doRpc(
          createRpcRequest("BadService", "", REQUEST.toByteString()), null);
      fail("Should have failed");
    } catch (RpcException e) {
      assertEquals(ErrorReason.SERVICE_NOT_FOUND, e.errorReason);
    }
  }

  /**
   * Server is called with RPC for unknown method.
   */
  public void testInvalidMethod() {
    // Register Service
    rpcForwarder.registerService(new FakeServiceImpl(REQUEST));
    assertInvalidMethodFails();

    // Register BlockingService
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl(REQUEST).toBlockingService());
    assertInvalidMethodFails();
  }

  private void assertInvalidMethodFails() {
    // Test doBlockingRpc
    try {
      rpcForwarder.doBlockingRpc(createRpcRequest(
          TestService.getDescriptor().getFullName(),
          "BadMethod",
          REQUEST.toByteString()));
      fail("Should have failed");
    } catch (RpcException e) {
      assertEquals(ErrorReason.METHOD_NOT_FOUND, e.errorReason);
    }

    // Test doRpc
    try {
      rpcForwarder.doRpc(createRpcRequest(
          TestService.getDescriptor().getFullName(),
          "BadMethod",
          REQUEST.toByteString()), null);
      fail("Should have failed");
    } catch (RpcException e) {
      assertEquals(ErrorReason.METHOD_NOT_FOUND, e.errorReason);
    }
  }

  /**
   * RPC Request proto is invalid.
   */
  public void testInvalidRequestProto() {
    // Register Service
    rpcForwarder.registerService(new FakeServiceImpl(null));
    assertBadRequestProtoFails();

    // Register BlockingService
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl(null).toBlockingService());
    assertBadRequestProtoFails();
  }

  private void assertBadRequestProtoFails() {
    // Test doBlockingRpc
    try {
      rpcForwarder.doBlockingRpc(createRpcRequest(
          TestService.getDescriptor().getFullName(),
          TestService.getDescriptor().getMethods().get(0).getName(),
          ByteString.copyFrom("Bad Request".getBytes())));
      fail("Should have failed");
    } catch (RpcException e) {
      assertEquals(ErrorReason.BAD_REQUEST_PROTO, e.errorReason);
    }

    // Test doRpc
    try {
      rpcForwarder.doRpc(createRpcRequest(
          TestService.getDescriptor().getFullName(),
          TestService.getDescriptor().getMethods().get(0).getName(),
          ByteString.copyFrom("Bad Request".getBytes())), null);
      fail("Should have failed");
    } catch (RpcException e) {
      assertEquals(ErrorReason.BAD_REQUEST_PROTO, e.errorReason);
    }
  }

  /**
   * RPC throws exception.
   */
  public void testRpcException() {
    // Register Service
    RuntimeException error = new RuntimeException();
    rpcForwarder.registerService(
        new FakeServiceImpl(REQUEST).throwsException(error));
    assertRpcErrorFails(error);

    // Register BlockingService
    error = new RuntimeException();
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl(REQUEST).throwsException(error).toBlockingService());
    assertRpcErrorFails(error);
  }

  private void assertRpcErrorFails(RuntimeException error) {
    // Test doBlockingRpc
    try {
      rpcForwarder.doBlockingRpc(RPC_REQUEST);
      fail("Should have failed");
    } catch (RpcException e) {
      assertEquals(ErrorReason.RPC_ERROR, e.errorReason);
      assertSame(error, e.getCause());
    }

    // Test doRpc
    try {
      rpcForwarder.doRpc(RPC_REQUEST, null);
      fail("Should have failed");
    } catch (RpcException e) {
      assertEquals(ErrorReason.RPC_ERROR, e.errorReason);
      assertSame(error, e.getCause());
    }
  }

  /**
   * RPC fails.
   */
  public void testRPCFailed() throws RpcException {
    // Register Service
    rpcForwarder.registerService(
        new FakeServiceImpl(REQUEST).failsWithError("Error"));

    // Test doBlockingRpc
    SocketRpcProtos.Response rpcResponse =
        rpcForwarder.doBlockingRpc(RPC_REQUEST);
    assertFalse(rpcResponse.getCallback());
    assertEquals("Error", rpcResponse.getError());
    assertEquals(ErrorReason.RPC_FAILED, rpcResponse.getErrorReason());

    // Test doRpc
    Callback<SocketRpcProtos.Response> rpcCallback =
        new Callback<SocketRpcProtos.Response>();
    rpcForwarder.doRpc(RPC_REQUEST, rpcCallback);
    assertFalse(rpcCallback.isInvoked());

    // Register BlockingService
    rpcForwarder.registerBlockingService(new FakeServiceImpl(REQUEST)
        .failsWithError("New Error").toBlockingService());

    // Test doBlockingRpc
    try {
      rpcForwarder.doBlockingRpc(RPC_REQUEST);
    } catch (RpcException e) {
      assertEquals(ErrorReason.RPC_FAILED, e.errorReason);
      assertEquals("New Error", e.getMessage());
    }

    // Test doRpc
    try {
      rpcForwarder.doRpc(RPC_REQUEST, null);
    } catch (RpcException e) {
      assertEquals(ErrorReason.RPC_FAILED, e.errorReason);
      assertEquals("New Error", e.getMessage());
    }
  }

  private static SocketRpcProtos.Request createRpcRequest(String service,
      String method, ByteString request) {
    return SocketRpcProtos.Request.newBuilder()
        .setServiceName(service)
        .setMethodName(method)
        .setRequestProto(request)
        .build();
  }
}
