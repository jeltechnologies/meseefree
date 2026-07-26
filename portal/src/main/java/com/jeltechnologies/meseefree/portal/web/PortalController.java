package com.jeltechnologies.meseefree.portal.web;

import com.jeltechnologies.meseefree.portal.connection.ConnectionInfo;
import com.jeltechnologies.meseefree.portal.connection.ConnectionInfoProvider;
import com.jeltechnologies.meseefree.portal.connection.DomainDnsCheckService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PortalController {

    private final ConnectionInfoProvider connectionInfoProvider;
    private final DomainDnsCheckService domainDnsCheckService;
    private final QrCodeService qrCodeService;

    public PortalController(ConnectionInfoProvider connectionInfoProvider,
                             DomainDnsCheckService domainDnsCheckService, QrCodeService qrCodeService) {
        this.connectionInfoProvider = connectionInfoProvider;
        this.domainDnsCheckService = domainDnsCheckService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping({"/", "/index.html"})
    public String index(Model model) {
        model.addAttribute("info", connectionInfoProvider.connectionInfo());
        model.addAttribute("dnsMismatch", domainDnsCheckService.dnsMismatch());
        return "index";
    }

    @GetMapping("/about")
    public String info() {
        return "info";
    }

    @GetMapping(value = {"/json", "/api"}, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ConnectionInfo json() {
        return connectionInfoProvider.connectionInfo();
    }

    @PostMapping("/rename")
    public String rename(@RequestParam String label, Model model) {
        try {
            connectionInfoProvider.rename(label);
        } catch (IllegalArgumentException e) {
            model.addAttribute("info", connectionInfoProvider.connectionInfo());
            model.addAttribute("dnsMismatch", domainDnsCheckService.dnsMismatch());
            model.addAttribute("renameError", true);
            return "index";
        }
        return "redirect:/";
    }

    @GetMapping("/qr")
    public ResponseEntity<byte[]> qr() throws Exception {
        byte[] png = qrCodeService.generatePng(connectionInfoProvider.connectionInfo().vlessLink());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(png);
    }
}
