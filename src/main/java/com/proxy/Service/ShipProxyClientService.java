package com.proxy.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.util.Enumeration;

@Service
public class ShipProxyClientService {

    public void sendRequestToServer(HttpServletRequest request, HttpServletResponse response) {
        try (
                Socket offshoreSocket = new Socket("localhost", 9090);
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(offshoreSocket.getOutputStream(), "UTF-8"));
                BufferedReader in = new BufferedReader(new InputStreamReader(offshoreSocket.getInputStream(), "UTF-8"));
                InputStream socketInputStream = offshoreSocket.getInputStream();
                OutputStream clientOut = response.getOutputStream()
        ) {
            System.out.println("✅ Connected to Offshore Proxy at localhost:9090");

            String method = request.getMethod();
            String host = request.getHeader("Host");
            String url = (request.isSecure() ? "https://" : "http://") + host + request.getRequestURI();
            System.out.println("➡ Forwarding request:");
            System.out.println("   Method : " + method);
            System.out.println("   URL    : " + url);

            out.write(method + "\n");
            out.write(url + "\n");

            System.out.println("📋 Request Headers:");
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                String value = request.getHeader(header);
                if (!"host".equalsIgnoreCase(header)) {
                    out.write(header + ": " + value + "\n");
                    System.out.println("   " + header + ": " + value);
                }
            }
            out.write("\n");
            out.flush();

            String statusLine = in.readLine();
            if (statusLine == null) {
                System.err.println(" No response from offshore proxy");
                response.sendError(502, "Bad Gateway: No response from offshore proxy");
                return;
            }

            System.out.println("✅ Received status line: " + statusLine);
            String[] parts = statusLine.split(" ", 3);
            int statusCode = parts.length > 1 ? Integer.parseInt(parts[1]) : 502;
            response.setStatus(statusCode);

            System.out.println("📥 Response Headers:");
            int contentLength = -1;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(":");
                if (colon > 0) {
                    String header = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    System.out.println("   " + header + ": " + value);
                    if ("Content-Length".equalsIgnoreCase(header)) {
                        try {
                            contentLength = Integer.parseInt(value);
                        } catch (NumberFormatException ignored) {}
                    }
                    if (!"Transfer-Encoding".equalsIgnoreCase(header)) {
                        response.addHeader(header, value);
                    }
                }
            }

            System.out.println(" Streaming response body...");
            ByteArrayOutputStream bodyCapture = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];

            if (contentLength >= 0) {
                System.out.println("📏 Known Content-Length: " + contentLength + " bytes");
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = socketInputStream.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead));
                    System.out.println(" Read " + read + " bytes from socketInputStream");
                    if (read == -1) {
                        System.out.println("⚠️ Stream ended prematurely at " + totalRead + " bytes");
                        break;
                    }
                    bodyCapture.write(buffer, 0, read);
                    clientOut.write(buffer, 0, read);
                    totalRead += read;
                }
                System.out.println(" Completed reading with total bytes: " + totalRead);
            } else {
                System.out.println("📏 Unknown Content-Length, streaming until EOF");
                int read;
                while ((read = socketInputStream.read(buffer)) != -1) {
                    bodyCapture.write(buffer, 0, read);
                    clientOut.write(buffer, 0, read);
                    System.out.println("➡ Sent " + read + " bytes to client response stream");
                }
                System.out.println(" Completed reading until EOF");
            }
            clientOut.flush();
            System.out.println(" Flushed client output stream");

            // Log response body text preview
            String responseBodyText = bodyCapture.toString("UTF-8");
            System.out.println(" Full captured body size: " + responseBodyText.length() + " characters");
            if (responseBodyText.length() > 10000) {
                responseBodyText = responseBodyText.substring(0, 1000) + "... [truncated]";
                System.out.println(" Response body truncated to preview length");
            }
            System.out.println(" Response Body (preview):\n" + responseBodyText);

        } catch (IOException e) {
            System.err.println(" Proxy error: " + e.getMessage());
            try {
                response.sendError(500, "Internal Server Error: Proxy failure");
            } catch (IOException ignored) {}
        }
    }
}