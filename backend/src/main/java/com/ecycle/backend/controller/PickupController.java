package com.ecycle.backend.controller;

import com.ecycle.backend.config.Sec;
import com.ecycle.backend.service.*;
import com.ecycle.shared.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Recycler map data and pickup booking / tracking. Live updates arrive over STOMP (/user/queue/events); these endpoints are the source of truth. */
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class PickupController {
    private final RecyclerService recyclers; private final PickupService pickups;

    @GetMapping("/recyclers")
    public List<RecyclerLocationDto> search(@RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng, @RequestParam(required = false) Double radiusKm,
                                            @RequestParam(required = false) String category) { return recyclers.search(lat, lng, radiusKm, category); }
    @GetMapping("/recyclers/mine")
    public List<RecyclerLocationDto> myFacilities() { return recyclers.mine(Sec.me()); }
    @PostMapping("/recyclers") @ResponseStatus(HttpStatus.CREATED)
    public RecyclerLocationDto addFacility(@Valid @RequestBody RecyclerLocationRequest r, @RequestParam(required = false) Long ownerId) { return recyclers.save(Sec.me(), null, r, ownerId); }
    @PutMapping("/recyclers/{id}")
    public RecyclerLocationDto updateFacility(@PathVariable Long id, @Valid @RequestBody RecyclerLocationRequest r) { return recyclers.save(Sec.me(), id, r, null); }

    @PostMapping("/pickups") @ResponseStatus(HttpStatus.CREATED)
    public PickupDto book(@Valid @RequestBody PickupRequest r) { return pickups.request(Sec.me(), r); }
    @GetMapping("/pickups")
    public List<PickupDto> list(@RequestParam(defaultValue = "requester") String as) { return pickups.mine(Sec.me(), "recycler".equalsIgnoreCase(as)); }
    @GetMapping("/pickups/{id}")
    public PickupDto get(@PathVariable Long id) { return pickups.get(Sec.me(), id); }
    @GetMapping("/pickups/track/{code}")
    public PickupDto track(@PathVariable String code) { return pickups.track(Sec.me(), code); }
    @GetMapping("/pickups/{id}/wipe-checklist")
    public ChecklistTemplateDto wipeTemplate(@PathVariable Long id) { return pickups.wipeTemplate(Sec.me(), id); }
    @PostMapping("/pickups/{id}/status")
    public PickupDto status(@PathVariable Long id, @Valid @RequestBody PickupStatusRequest r) { return pickups.updateStatus(Sec.me(), id, r); }
    @PostMapping("/pickups/{id}/ping")
    public PickupDto ping(@PathVariable Long id, @Valid @RequestBody PickupPingRequest r) { return pickups.ping(Sec.me(), id, r); }
    @PostMapping("/pickups/{id}/verify")
    public PickupDto verify(@PathVariable Long id, @Valid @RequestBody PickupVerifyRequest r) { return pickups.verify(Sec.me(), id, r); }
}
