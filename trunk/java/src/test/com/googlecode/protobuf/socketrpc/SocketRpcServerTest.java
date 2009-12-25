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

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.ErrorReason;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Response;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService;

import junit.framework.TestCase;

/**
 * Unit tests for {@link SocketRpcServer}
 *
 * @author Shardul Deo
 */
public class SocketRpcServerTest extends TestCase {

  private SocketRpcServer socketRpcServer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    socketRpcServer = new SocketRpcServer(-1, null);
  }

  /**
   * Test rpc server running on the localhost only.
   *
   * @throws UnknownHostException
   */
  public void testRpcLocalServer() throws InvalidProtocolBufferException,
      UnknownHostException {
    // Create data
    String reqdata = "Request Data";
    String resdata = "Response Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    Response response = Response.newBuilder().setStrData(resdata).build();

    SocketRpcServer socketRpcLocalServer = new SocketRpcServer(-1,
    		0, InetAddress.getLocalHost(), null);

    // Call handler
    socketRpcLocalServer.registerService(
        new FakeServiceImpl().withResponse(response));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(request));
    socketRpcLocalServer.new Handler(socket).run();

    // Verify result
    assertTrue(socket.getResponse().getCallback());
    assertEquals(response.toByteString(),
        socket.getResponse().getResponseProto());
  }

  /**
   * Test good request/response
   */
  public void testGoodRpc() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    String resdata = "Response Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call handler
    socketRpcServer.registerService(
        new FakeServiceImpl().withResponse(response));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(request));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertTrue(socket.getResponse().getCallback());
    assertEquals(response.toByteString(),
        socket.getResponse().getResponseProto());
  }

  /**
   * Successful RPC but callback is not called.
   */
  public void testNoCallback() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call handler
    socketRpcServer.registerService(new FakeServiceImpl());
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(request));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
  }

  /**
   * Successful RPC but callback is called with null.
   */
  public void testNullCallBack() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call handler
    socketRpcServer.registerService(new FakeServiceImpl().withResponse(null));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(request));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertTrue(socket.getResponse().getCallback());
    assertFalse(socket.getResponse().hasResponseProto());
  }

  /**
   * Server receives corrupt bytes in request.
   */
  public void testBadRequest() throws InvalidProtocolBufferException {
    // Call handler
    socketRpcServer.registerService(new FakeServiceImpl());
    FakeSocket socket = new FakeSocket().withInputBytes("bad bytes".getBytes());
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ErrorReason.BAD_REQUEST_DATA,
        socket.getResponse().getErrorReason());
  }

  /**
   * Server is called with RPC for unknown service.
   */
  public void testInvalidService() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    String resdata = "Response Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call handler
    socketRpcServer.registerService(
        new FakeServiceImpl().withResponse(response));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(
        "BadService", "", request.toByteString()));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ErrorReason.SERVICE_NOT_FOUND,
        socket.getResponse().getErrorReason());
  }

  /**
   * Server is called with RPC for unknown method.
   */
  public void testInvalidMethod() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    String resdata = "Response Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call handler
    socketRpcServer.registerService(
        new FakeServiceImpl().withResponse(response));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(
        TestService.getDescriptor().getFullName(), "BadMethod",
        request.toByteString()));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ErrorReason.METHOD_NOT_FOUND,
        socket.getResponse().getErrorReason());
  }

  /**
   * RPC Request proto is invalid.
   */
  public void testInvalidRequestProto() throws InvalidProtocolBufferException {
    // Create data
    String resdata = "Response Data";
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call handler
    socketRpcServer.registerService(
        new FakeServiceImpl().withResponse(response));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(
        TestService.getDescriptor().getFullName(),
        TestService.getDescriptor().getMethods().get(0).getName(),
        ByteString.copyFrom("Bad Request".getBytes())));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ErrorReason.BAD_REQUEST_PROTO,
        socket.getResponse().getErrorReason());
  }

  /**
   * RPC throws exception.
   */
  public void testRpcException() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call handler
    socketRpcServer.registerService(
        new FakeServiceImpl().throwsException(new RuntimeException()));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(request));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ErrorReason.RPC_ERROR,
        socket.getResponse().getErrorReason());
  }

  /**
   * RPC fails.
   */
  public void testRPCFailed() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call handler
    socketRpcServer.registerService(
        new FakeServiceImpl().failsWithError("Error"));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(request));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertEquals("Error", socket.getResponse().getError());
    assertEquals(ErrorReason.RPC_FAILED,
        socket.getResponse().getErrorReason());

    // Call handler
    socketRpcServer.registerBlockingService(
        new FakeServiceImpl().failsWithError("New Error").toBlockingService());
    socket = new FakeSocket().withRequest(createRpcRequest(request));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertEquals("New Error", socket.getResponse().getError());
    assertEquals(ErrorReason.RPC_FAILED,
        socket.getResponse().getErrorReason());
  }

  private SocketRpcProtos.Request createRpcRequest(Request request) {
    return createRpcRequest(TestService.getDescriptor().getFullName(),
        TestService.getDescriptor().getMethods().get(0).getName(),
        request.toByteString());
  }

  private SocketRpcProtos.Request createRpcRequest(String service,
      String method, ByteString request) {
    return SocketRpcProtos.Request.newBuilder()
        .setServiceName(service)
        .setMethodName(method)
        .setRequestProto(request)
        .build();
  }
}
