#!/usr/bin/env python3
"""Local mock HTTP target for TaskFlow end-to-end checks. Not used in production."""

from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

HIT_LOG = Path("/tmp/taskflow-http-hits.log")


class Handler(BaseHTTPRequestHandler):
    def _read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(length) if length else b""

    def _record(self, status: int, body: bytes) -> None:
        HIT_LOG.parent.mkdir(parents=True, exist_ok=True)
        with HIT_LOG.open("a", encoding="utf-8") as handle:
            handle.write(f"{self.command} {self.path} status={status} body={body.decode('utf-8', 'replace')}\n")

    def _respond(self, status: int, payload: bytes) -> None:
        body = self._read_body()
        self._record(status, body)
        self.send_response(status)
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        self._respond(500 if self.path.startswith("/fail") else 200, b"ok")

    def do_POST(self):
        self._respond(500 if self.path.startswith("/fail") else 200, b"ok")

    def log_message(self, format, *args):
        return


if __name__ == "__main__":
    HIT_LOG.write_text("", encoding="utf-8")
    server = HTTPServer(("127.0.0.1", 9099), Handler)
    print("listening on http://127.0.0.1:9099", flush=True)
    server.serve_forever()
