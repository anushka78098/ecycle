package com.ecycle.backend.controller;

import com.ecycle.backend.config.Sec;
import com.ecycle.backend.service.*;
import com.ecycle.shared.Dtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** QR-based chain of custody on top of the existing Digital Product Passport. */
@RestController @RequestMapping("/api/custody") @RequiredArgsConstructor
public class CustodyController {
    private final CustodyService custody;

    @GetMapping("/{code}")
    public CustodyChainDto chain(@PathVariable String code) { return custody.chain(Sec.me(), code); }
    @PostMapping("/{code}/events")
    public CustodyChainDto record(@PathVariable String code, @Valid @RequestBody CustodyEventRequest r) { return custody.recordManual(Sec.me(), code, r); }
    /** Scanning a passport QR (or typing its code) records a SCANNED event and returns the chain. */
    @PostMapping("/scan")
    public CustodyChainDto scan(@Valid @RequestBody CustodyScanRequest r) { return custody.scan(Sec.me(), r); }
}
