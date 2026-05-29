#!/usr/bin/env python3
"""
Local HTTPS proxy for Mavenizer.
Starts an HTTP server on port 8080 that redirects all requests to HTTPS.
Then we'll configure Mavenizer to use http://localhost:8080 instead of http://repo1.maven.org.
"""
import http.server
import urllib.request
import os
import sys
import threading
import time

class ProxyHandler(http.server.BaseHTTPRequestHandler):
    """HTTP proxy that forwards requests to HTTPS upstream servers."""
    
    # Map localhost paths to HTTPS upstreams
    UPSTREAMS = {
        '': 'https://repo1.maven.org',
        '/maven2': 'https://repo1.maven.org/maven2',
    }
    
    def do_GET(self):
        # Determine upstream URL
        path = self.path
        upstream = 'https://repo1.maven.org' + path
        
        try:
            req = urllib.request.Request(upstream, headers={'User-Agent': 'Mozilla/5.0'})
            response = urllib.request.urlopen(req, timeout=30)
            data = response.read()
            
            self.send_response(200)
            content_type = response.headers.get('Content-Type', 'application/octet-stream')
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', len(data))
            self.end_headers()
            self.wfile.write(data)
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            self.end_headers()
        except Exception as e:
            self.send_response(502)
            self.end_headers()
            self.wfile.write(str(e).encode())
    
    def log_message(self, format, *args):
        pass  # Suppress logging


def start_server(port=8080):
    server = http.server.HTTPServer(('127.0.0.1', port), ProxyHandler)
    print(f'HTTPS proxy running on http://127.0.0.1:{port}')
    server.serve_forever()


if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    start_server(port)
