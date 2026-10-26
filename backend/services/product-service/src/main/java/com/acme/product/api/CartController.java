package com.acme.product.api;

import com.acme.product.application.CatalogService;
import com.acme.product.security.InternalContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;

/** Cart stores intent only. Prices and stock are resolved from MySQL at reservation time. */
@RestController
@RequestMapping("/api/cart")
public class CartController {
    public record Item(@NotBlank @Pattern(regexp="[A-Z0-9_-]{1,64}") String sku, @Min(1) @Max(1000) int quantity) {}
    public record Cart(@NotNull @Size(max=20) List<@Valid Item> items) {}
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final CatalogService catalog;
    public CartController(StringRedisTemplate redis, ObjectMapper json, CatalogService catalog) { this.redis = redis; this.json = json; this.catalog = catalog; }
    @GetMapping
    public Cart get(HttpServletRequest request) {
        String value = redis.opsForValue().get(key(request));
        return value == null ? new Cart(List.of()) : json.readValue(value, Cart.class);
    }
    @PutMapping
    public Cart replace(@Valid @RequestBody Cart cart, HttpServletRequest request) {
        var seen = new HashSet<String>();
        for (Item item : cart.items()) {
            if (!seen.add(item.sku())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate SKU");
        }
        if (!catalog.allExist(seen)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown SKU");
        redis.opsForValue().set(key(request), json.writeValueAsString(cart), Duration.ofDays(30));
        return cart;
    }
    private String key(HttpServletRequest request) {
        String user = (String) request.getAttribute(InternalContextFilter.USER_ATTRIBUTE);
        return "cart:v1:{" + Base64.getUrlEncoder().withoutPadding().encodeToString(user.getBytes(StandardCharsets.UTF_8)) + "}";
    }
}
