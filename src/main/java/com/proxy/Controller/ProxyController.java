package com.proxy.Controller;

import com.proxy.Service.ShipProxyClientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.net.Socket;
import java.util.Enumeration;

@RestController
public class ProxyController {

   @Autowired
   ShipProxyClientService proxyClientService;
    @RequestMapping("/**")
    public void handleProxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
       proxyClientService.sendRequestToServer(request,response);
    }
}
