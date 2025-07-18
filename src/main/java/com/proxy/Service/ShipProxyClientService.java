package com.proxy.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

@Service
public class ShipProxyClientService {

    public void sendRequestToServer(HttpServletRequest request, HttpServletResponse response) {
        try (
                Socket offshoreSocket = new Socket("offshore-proxy-server", 9090);
                InputStream offshoreIn = offshoreSocket.getInputStream();
                OutputStream offshoreOut = offshoreSocket.getOutputStream();
                OutputStream clientOut = response.getOutputStream()
        ) {
            System.out.println("✅ Connected to Offshore Proxy at localhost:9090");

            String method = request.getMethod();
            String host = request.getHeader("Host");
            String url = (request.isSecure() ? "https://" : "http://") + host + request.getRequestURI();

            System.out.println("➡ Forwarding request:");
            System.out.println("   Method : " + method);
            System.out.println("   URL    : " + url);

            // Send method and URL
            offshoreOut.write((method + "\n").getBytes(StandardCharsets.US_ASCII));
            offshoreOut.write((url + "\n").getBytes(StandardCharsets.US_ASCII));

            // Send headers
            System.out.println("📋 Request Headers:");
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                String value = request.getHeader(header);
                if (!"host".equalsIgnoreCase(header)) {
                    String headerLine = header + ": " + value + "\n";
                    offshoreOut.write(headerLine.getBytes(StandardCharsets.US_ASCII));
                    System.out.println("   " + header + ": " + value);
                }
            }
            offshoreOut.write("\n".getBytes(StandardCharsets.US_ASCII));
            offshoreOut.flush();

            // Read status line
            String statusLine = readLine(offshoreIn);
            if (statusLine == null) {
                System.err.println(" No response from offshore proxy");
                response.sendError(502, "Bad Gateway: No response from offshore proxy");
                return;
            }

            System.out.println("✅ Received status line: " + statusLine);
            String[] parts = statusLine.split(" ", 3);
            int statusCode = (parts.length > 1) ? Integer.parseInt(parts[1]) : 502;
            response.setStatus(statusCode);

            // Read response headers
            System.out.println(" Response Headers:");
            int contentLength = -1;
            String line;
            while (!(line = readLine(offshoreIn)).isEmpty()) {
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
                    int read = offshoreIn.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead));
                    if (read == -1) {
                        System.out.println("Stream ended prematurely at " + totalRead + " bytes");
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
                while ((read = offshoreIn.read(buffer)) != -1) {
                    bodyCapture.write(buffer, 0, read);
                    clientOut.write(buffer, 0, read);
                }
                System.out.println(" Completed reading until EOF");
            }

            clientOut.flush();
            System.out.println(" Flushed client output stream");

            // Log response body preview
            String responseBodyText = bodyCapture.toString("UTF-8");
            System.out.println(" Full captured body size: " + responseBodyText.length() + " characters");
            if (responseBodyText.length() > 10000) {
                responseBodyText = responseBodyText.substring(0, 1000) + "... [truncated]";
            }
            System.out.println(" Response Body (preview):\n" + responseBodyText);

        } catch (IOException e) {
            System.err.println(" Proxy error: " + e.getMessage());
            try {
                response.sendError(500, "Internal Server Error: Proxy failure");
            } catch (IOException ignored) {}
        }
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') line.write(c);
        }
        return line.toString(StandardCharsets.UTF_8);
    }
}
