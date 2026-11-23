package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.Listing;
import com.ecycle.backend.entity.Wishlist;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.ListingDto;
import com.ecycle.shared.Enums.ListingStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class WishlistService {
    private final WishlistRepository repo; private final ListingRepository listings; private final ListingMapper mapper;

    @Transactional
    public void add(AuthUser me, Long listingId) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (l.getStatus() != ListingStatus.APPROVED) throw ApiException.conflict("Only live listings can be wishlisted");
        if (repo.findByUserIdAndListingId(me.id(), listingId).isEmpty()) { Wishlist w = new Wishlist(); w.setUserId(me.id()); w.setListingId(listingId); repo.save(w); }
    }
    @Transactional
    public void remove(AuthUser me, Long listingId) { repo.findByUserIdAndListingId(me.id(), listingId).ifPresent(repo::delete); }
    @Transactional(readOnly = true)
    public List<ListingDto> list(AuthUser me) {
        return mapper.toDtos(repo.findByUserIdOrderByCreatedAtDesc(me.id()).stream().map(w -> listings.findById(w.getListingId()).orElse(null)).filter(l -> l != null).toList());
    }
    @Transactional(readOnly = true)
    public List<Long> ids(AuthUser me) { return repo.findByUserIdOrderByCreatedAtDesc(me.id()).stream().map(Wishlist::getListingId).toList(); }
}
