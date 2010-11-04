#!/usr/bin/python
# Copyright (c) 2009 Las Cumbres Observatory.
# Copyright (c) 2010 Jan Dittberner
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.

from setuptools import setup, find_packages

DESCRIPTION = """Google's protocol buffer library makes writing rpc services
easy, but it does not contain a rpc implementation. The transport details are
left up to the user to implement.

This is a simple tcp/ip socket based rpc implementation in java and python for
people who want a simple implementation of their protobuf rpc services.
"""

setup(
    name="protobuf.socketrpc",
    version="1.3.2",
    description="a Python implementation of protobuf RPC over sockets",
    long_description=DESCRIPTION,
    url='http://code.google.com/p/protobuf-socket-rpc/',
    author='Shardul Deo',
    author_email='shardul.deo@gmail.com',
    classifiers=[
        'Intended Audience :: Developers',
        'License :: OSI Approved :: MIT License',
        'Operating System :: OS Independent',
        'Programming Language :: Python',
        'Topic :: Software Development :: Libraries :: Python Modules'],
    packages=find_packages('src', exclude=[
            '*.*.tests', '*.*.examples', '*.*.examples.*']),
    package_dir={'': 'src'},
    # protobuf is not easy_install'able (yet) see
    # http://code.google.com/p/protobuf/issues/detail?id=66
    #install_requires=['protobuf>=2.2'],
    test_suite='protobuf.socketrpc.tests',
)
