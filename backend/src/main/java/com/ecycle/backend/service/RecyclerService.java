package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.engine.GeoMath;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recycler facilities shown on the map. A "recycler" is an existing verified BUYER account whose buyer type is RECYCLER; admins may manage any facility. */
@Service @RequiredArgsConstructor
public class RecyclerService {
    private final RecyclerLocationRepository repo; private final BuyerProfileRepository buyers; private final Lookup lookup; private final AuditService audit;

    public boolean isRecycler(Long userId) { return buyers.findByUserId(userId).map(b -> b.getBuyerType() == BuyerType.RECYCLER && b.isVerified()).orElse(false); }

    /** Public facility list; with a point, results are limited to that radius (default 50 km) and sorted by distance. */
    @Transactional(readOnly = true)
    public List<RecyclerLocationDto> search(Double lat, Double lng, Double radiusKm, String category) {
        List<RecyclerLocation> all;
        boolean geo = lat != null && lng != null && GeoMath.validLatLon(lat, lng);
        double radius = radiusKm == null ? 50 : Math.max(1, Math.min(radiusKm, 2000));
        if (geo) { double[] b = GeoMath.boundingBox(lat, lng, radius); all = repo.findByActiveTrueAndLatitudeBetweenAndLongitudeBetween(b[0], b[1], b[2], b[3]); }
        else all = repo.findByActiveTrue();
        String cat = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        List<RecyclerLocationDto> out = new ArrayList<>();
        Map<Long, BuyerProfile> profiles = new HashMap<>(); buyers.findByUserIdIn(all.stream().map(RecyclerLocation::getRecyclerId).distinct().toList()).forEach(p -> profiles.put(p.getUserId(), p));
        for (RecyclerLocation l : all) {
            if (!cat.isEmpty() && (l.getAcceptedCategories() == null || !l.getAcceptedCategories().toLowerCase(Locale.ROOT).contains(cat))) continue;
            Double d = geo ? GeoMath.haversineKm(lat, lng, l.getLatitude(), l.getLongitude()) : null;
            if (geo && d > radius) continue;
            out.add(dto(l, profiles.get(l.getRecyclerId()), d));
        }
        if (geo) out.sort(Comparator.comparing(RecyclerLocationDto::distanceKm));
        else out.sort(Comparator.comparing(RecyclerLocationDto::facilityName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Transactional(readOnly = true)
    public List<RecyclerLocationDto> mine(AuthUser me) {
        BuyerProfile p = buyers.findByUserId(me.id()).orElse(null);
        return repo.findByRecyclerIdOrderByIdAsc(me.id()).stream().map(l -> dto(l, p, null)).toList();
    }

    @Transactional
    public RecyclerLocationDto save(AuthUser me, Long id, RecyclerLocationRequest r, Long ownerOverride) {
        boolean admin = me.has(Role.RESOLVER);
        if (!admin && !isRecycler(me.id())) throw ApiException.forbidden("Only verified recycler accounts can manage facilities");
        if (!GeoMath.validLatLon(r.latitude(), r.longitude())) throw ApiException.badRequest("Invalid coordinates");
        RecyclerLocation l;
        if (id == null) { l = new RecyclerLocation(); l.setRecyclerId(admin && ownerOverride != null ? ownerOverride : me.id()); if (!admin && repo.findByRecyclerIdOrderByIdAsc(me.id()).size() >= 20) throw ApiException.conflict("Facility limit reached"); }
        else { l = repo.findById(id).orElseThrow(() -> ApiException.notFound("Facility not found")); if (!admin && !l.getRecyclerId().equals(me.id())) throw ApiException.notFound("Facility not found"); }
        if (admin && ownerOverride != null && !isRecycler(ownerOverride)) throw ApiException.badRequest("Owner must be a verified recycler");
        l.setFacilityName(r.facilityName().trim()); l.setAddress(r.address().trim()); l.setCity(r.city().trim()); l.setLatitude(r.latitude()); l.setLongitude(r.longitude());
        l.setPhone(r.phone()); l.setOpeningHours(r.openingHours()); l.setAcceptedCategories(r.acceptedCategories()); l.setCertifications(r.certifications());
        l.setServiceRadiusKm(r.serviceRadiusKm()); l.setActive(r.active());
        repo.save(l);
        audit.log(me.id(), id == null ? "RECYCLER_LOCATION_ADDED" : "RECYCLER_LOCATION_UPDATED", "recycler_locations", l.getId(), l.getFacilityName());
        return dto(l, buyers.findByUserId(l.getRecyclerId()).orElse(null), null);
    }

    RecyclerLocation require(Long id) { return repo.findById(id).orElseThrow(() -> ApiException.notFound("Facility not found")); }

    RecyclerLocationDto dto(RecyclerLocation l, BuyerProfile p, Double distance) {
        return new RecyclerLocationDto(l.getId(), l.getRecyclerId(), lookup.name(l.getRecyclerId()), p == null ? null : p.getOrganization(), l.getFacilityName(), l.getAddress(), l.getCity(),
                l.getLatitude(), l.getLongitude(), l.getPhone(), l.getOpeningHours(), l.getAcceptedCategories(), l.getCertifications(), l.getServiceRadiusKm(), l.isActive(),
                distance == null ? null : Math.round(distance * 10.0) / 10.0, p == null ? 0 : p.getRecoveryCapability(), p == null ? 0 : p.getEnvironmentalRating());
    }
}
