package com.ecycle.backend.controller;

import com.ecycle.backend.config.Sec;
import com.ecycle.backend.service.*;
import com.ecycle.shared.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Offers, orders, payments, auctions, wishlist, reviews. */
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class TradeController {
    private final OfferService offers; private final OrderService orders; private final PaymentService payments; private final AuctionService auctions;
    private final WishlistService wishlist; private final ReviewService reviews;

    // offers
    @PostMapping("/offers") @PreAuthorize("hasRole('BUYER')") @ResponseStatus(HttpStatus.CREATED)
    public OfferDto offer(@Valid @RequestBody OfferRequest r) { return offers.make(Sec.me(), r); }
    @GetMapping("/offers/mine") @PreAuthorize("hasRole('BUYER')")
    public List<OfferDto> myOffers() { return offers.mine(Sec.me()); }
    @GetMapping("/offers/received") @PreAuthorize("hasRole('SELLER')")
    public List<OfferDto> received() { return offers.forSeller(Sec.me()); }
    @PostMapping("/offers/{id}/accept") @PreAuthorize("hasRole('SELLER')")
    public OrderDto accept(@PathVariable Long id) { return offers.accept(Sec.me(), id); }
    @PostMapping("/offers/{id}/reject") @PreAuthorize("hasRole('SELLER')")
    public OfferDto reject(@PathVariable Long id) { return offers.reject(Sec.me(), id); }
    @PostMapping("/offers/{id}/withdraw") @PreAuthorize("hasRole('BUYER')")
    public OfferDto withdraw(@PathVariable Long id) { return offers.withdraw(Sec.me(), id); }

    // orders
    @PostMapping("/orders") @PreAuthorize("hasRole('BUYER')") @ResponseStatus(HttpStatus.CREATED)
    public OrderDto buy(@Valid @RequestBody OrderRequest r) { return orders.buyNow(Sec.me(), r); }
    @GetMapping("/orders/mine")
    public List<OrderDto> myOrders(@RequestParam(defaultValue = "buyer") String as) { return orders.mine(Sec.me(), as); }
    @GetMapping("/orders/{id}")
    public OrderDto order(@PathVariable Long id) { return orders.get(Sec.me(), id); }
    @PutMapping("/orders/{id}/address") @PreAuthorize("hasRole('BUYER')")
    public OrderDto address(@PathVariable Long id, @Valid @RequestBody AddressRequest r) { return orders.setAddress(Sec.me(), id, r.shippingAddress()); }
    @PostMapping("/orders/{id}/cancel")
    public OrderDto cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest r) { return orders.cancel(Sec.me(), id, r == null ? null : r.reason()); }
    @PutMapping("/orders/{id}/shipment") @PreAuthorize("hasRole('SELLER')")
    public OrderDto shipment(@PathVariable Long id, @Valid @RequestBody ShipmentRequest r) { return orders.updateShipment(Sec.me(), id, r); }
    @PostMapping("/orders/{id}/confirm-delivery") @PreAuthorize("hasRole('BUYER')")
    public OrderDto confirm(@PathVariable Long id) { return orders.confirmDelivery(Sec.me(), id); }
    @GetMapping("/orders/{id}/receipt")
    public ReceiptDto receipt(@PathVariable Long id) { return payments.receipt(Sec.me(), id); }

    // payments
    @PostMapping("/payments/orders/{orderId}/init") @PreAuthorize("hasRole('BUYER')")
    public PaymentInitDto init(@PathVariable Long orderId) { return payments.initiate(Sec.me(), orderId); }
    @PostMapping("/payments/{id}/verify") @PreAuthorize("hasRole('BUYER')")
    public OrderDto verify(@PathVariable Long id, @Valid @RequestBody PaymentVerifyRequest r) { return payments.verify(Sec.me(), id, r); }
    @PostMapping("/payments/{id}/mock-confirm") @PreAuthorize("hasRole('BUYER')")
    public OrderDto mockConfirm(@PathVariable Long id) { return payments.mockConfirm(Sec.me(), id); }

    // reverse auctions
    @GetMapping("/auctions")
    public List<AuctionDto> browse() { return auctions.browse(Sec.me()); }
    @GetMapping("/auctions/mine")
    public List<AuctionDto> myAuctions() { return auctions.mine(Sec.me()); }
    @GetMapping("/auctions/{id}")
    public AuctionDto auction(@PathVariable Long id) { return auctions.view(Sec.me(), id); }
    @PostMapping("/auctions/{id}/bids") @PreAuthorize("hasRole('BUYER')") @ResponseStatus(HttpStatus.CREATED)
    public BidDto bid(@PathVariable Long id, @Valid @RequestBody BidRequest r) { return auctions.placeBid(Sec.me(), id, r); }
    @PostMapping("/auctions/{id}/accept/{bidId}") @PreAuthorize("hasRole('SELLER')")
    public OrderDto acceptBid(@PathVariable Long id, @PathVariable Long bidId) { return auctions.accept(Sec.me(), id, bidId); }
    @PostMapping("/auctions/{id}/cancel") @PreAuthorize("hasRole('SELLER')")
    public Msg cancelAuction(@PathVariable Long id) { auctions.cancel(Sec.me(), id); return new Msg("Auction cancelled"); }

    // wishlist & reviews
    @GetMapping("/wishlist")
    public List<ListingDto> wishlist() { return wishlist.list(Sec.me()); }
    @GetMapping("/wishlist/ids")
    public List<Long> wishlistIds() { return wishlist.ids(Sec.me()); }
    @PostMapping("/wishlist/{listingId}")
    public Msg addWish(@PathVariable Long listingId) { wishlist.add(Sec.me(), listingId); return new Msg("Added"); }
    @DeleteMapping("/wishlist/{listingId}")
    public Msg removeWish(@PathVariable Long listingId) { wishlist.remove(Sec.me(), listingId); return new Msg("Removed"); }
    @PostMapping("/reviews") @ResponseStatus(HttpStatus.CREATED)
    public ReviewDto review(@Valid @RequestBody ReviewRequest r) { return reviews.create(Sec.me(), r); }
    @GetMapping("/reviews/user/{id}")
    public List<ReviewDto> userReviews(@PathVariable Long id) { return reviews.forUser(id); }
}
