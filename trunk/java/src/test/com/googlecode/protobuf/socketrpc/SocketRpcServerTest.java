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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import junit.framework.TestCase;

import com.google.protobuf.ByteString;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.ErrorReason;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Response;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService;

/**
 * Unit tests for {@link SocketRpcServer}
 *
 * @author Shardul Deo
 */
public class SocketRpcServerTest extends TestCase {

  private static final Request REQUEST;
  private static final SocketRpcProtos.Request RPC_REQUEST;

  static {
    REQUEST = Request.newBuilder().setStrData("Request Data").build();
    RPC_REQUEST = createRpcRequest(TestService.getDescriptor().getFullName(),
        TestService.getDescriptor().getMethods().get(0).getName(),
        REQUEST.toByteString());
  }

  private SocketRpcServer socketRpcServer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    socketRpcServer = new SocketRpcServer(-1, null);
  }

  private void runHandler(Socket socket) throws IOException {
    SocketConnection connection = new SocketConnection(socket, false);
    socketRpcServer.new ConnectionHandler(connection).run();
  }

  /**
   * Test rpc server running on the localhost only.
   *
   * @throws UnknownHostException
   */
  public void testRpcLocalServer() throws Exception {
    // Create data
    String resdata = "Response Data";
    Response response = Response.newBuilder().setStrData(resdata).build();

    SocketRpcServer socketRpcLocalServer = new SocketRpcServer(-1,
    		0, InetAddress.getLocalHost(), null);

    // Call handler
    socketRpcLocalServer.registerService(
        new FakeServiceImpl(REQUEST).withResponse(response));
    FakeSocket socket = new FakeSocket(false).withRequest(RPC_REQUEST);
    socketRpcLocalServer.new ConnectionHandler(
        new SocketConnection(socket, false)).run();

    // Verify result
    assertTrue(socket.getResponse().getCallback());
    assertEquals(response.toByteString(),
        socket.getResponse().getResponseProto());
  }

  /**
   * Test good request/response
   */
  public void testGoodRpc() throws Exception {
    // Create data
    String resdata = "Response Data";
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call handler
    socketRpcServer.registerService(
        new FakeServiceImpl(REQUEST).withResponse(response));
    FakeSocket socket = new FakeSocket(false).withRequest(RPC_REQUEST);
    runHandler(socket);

    // Verify result
    assertTrue(socket.getResponse().getCallback());
    assertEquals(response.toByteString(),
        socket.getResponse().getResponseProto());
  }

  /**
   * Successful RPC but callback is not called.
   */
  public void testNoCallback() throws Exception {
    // Call handler
    socketRpcServer.registerService(new FakeServiceImpl(REQUEST));
    FakeSocket socket = new FakeSocket(false).withRequest(RPC_REQUEST);
    runHandler(socket);

    // Verify result
    assertFalse(socket.getResponse().getCallback());
  }

  /**
   * Successful RPC but callback is called with null.
   */
  public void testNullCallBack() throws Exception {
    // Call handler
    socketRpcServer.registerService(new FakeServiceImpl(REQUEST)
        .withResponse(null));
    FakeSocket socket = new FakeSocket(false).withRequest(RPC_REQUEST);
    runHandler(socket);

    // Verify result
    assertTrue(socket.getResponse().getCallback());
    assertFalse(socket.getResponse().hasResponseProto());
  }

  /**
   * Server receives corrupt bytes in request.
   */
  public void testBadRequest() throws Exception {
    // Call handler
    socketRpcServer.registerService(new FakeServiceImpl(null));
    FakeSocket socket = new FakeSocket(false).withInputBytes(
        "bad bytes".getBytes());
    runHandler(socket);

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ErrorReason.BAD_REQUEST_DATA,
        socket.getResponse().getErrorReason());
  }

  /**
   * Server is called with RPC for unknown service.
   */
  public void testInvalidService() throws Exception {
    // Create data
    String resdata = "Response Data";
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call handler
    socketRpcServer.registerService(
        new FakeServiceImpl(REQUEST).withResponse(response));
    FakeSocket socket = new FakeSocket(false).withRequest(createRpcRequest(
        "BadService", "", REQUEST.toByteString()));
    runHandler(socket);

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ErrorReason.SERVICE_NOT_FOUND,
        socket.getResponse().getErrorReason());
  }

  /**
   * Server is called with RPC for unknown method.
   */
  public void testInvalidMethod() throws Exception {
    // Create data
    String resdata = "Response Data";
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call handler
    socketRpcServer.registerService(
        new FakeServiceImpl(REQUEST).withResponse(response));
    FakeSocket socket = new FakeSocket(false).withRequest(createRpcRequest(
        TestService.getDescriptor().getFullName(), "BadMethod",
        REQUEST.toByteString()));
    runHandler(socket);

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ErrorReason.METHOD_NOT_FOUND,
        socket.getResponse().getErrorReason());
  }

  /**
   * RPC Request proto is invalid.
   */
  public void testInvalidRequestProto() throws Exception {
    // Create data
    String resdata = "Response Data";
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call handler
    socketRpcServer.registerService(
        new FakeServiceImpl(null).withResponse(response));
    FakeSocket socket = new FakeSocket(false).withRequest(createRpcRequest(
        TestService.getDescriptor().getFullName(),
        TestService.getDescriptor().getMethods().get(0).getName(),
        ByteString.copyFrom("Bad Request".getBytes())));
    runHandler(socket);

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ErrorReason.BAD_REQUEST_PROTO,
        socket.getResponse().getErrorReason());
  }

  /**
   * RPC throws exception.
   */
  public void testRpcException() throws Exception {
    // Call handler
    socketRpcServer.registerService(
        new FakeServiceImpl(REQUEST).throwsException(new RuntimeException()));
    FakeSocket socket = new FakeSocket(false).withRequest(RPC_REQUEST);
    runHandler(socket);

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ErrorReason.RPC_ERROR,
        socket.getResponse().getErrorReason());
  }

  /**
   * RPC fails.
   */
  public void testRPCFailed() throws Exception {
    // Call handler
    socketRpcServer.registerService(
        new FakeServiceImpl(REQUEST).failsWithError("Error"));
    FakeSocket socket = new FakeSocket(false).withRequest(RPC_REQUEST);
    runHandler(socket);

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertEquals("Error", socket.getResponse().getError());
    assertEquals(ErrorReason.RPC_FAILED,
        socket.getResponse().getErrorReason());

    // Call handler
    socketRpcServer.registerBlockingService(new FakeServiceImpl(REQUEST)
        .failsWithError("New Error").toBlockingService());
    socket = new FakeSocket(false).withRequest(RPC_REQUEST);
    runHandler(socket);

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertEquals("New Error", socket.getResponse().getError());
    assertEquals(ErrorReason.RPC_FAILED,
        socket.getResponse().getErrorReason());
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
