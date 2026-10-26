package com.acme.product.api;

import com.acme.product.application.CatalogService;
import com.acme.product.domain.Product;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {
    private final CatalogService catalog;
    public ProductController(CatalogService catalog) { this.catalog = catalog; }
    public record CreateProduct(@NotBlank @Pattern(regexp="[A-Z0-9_-]{1,64}") String sku,
                                @NotBlank @Size(max=200) String name,
                                @Min(1) @Max(1_000_000_000_000L) long priceMinor,
                                @NotNull @Pattern(regexp="USD") String currency,
                                @Min(0) @Max(1_000_000_000) int stock) {}
    @GetMapping
    public List<Product> list(@RequestParam(defaultValue="") @Pattern(regexp="[A-Z0-9_-]{0,64}") String after,
                              @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return catalog.list(after, size); }
    @GetMapping("/{sku}")
    public Product get(@PathVariable @Pattern(regexp="[A-Z0-9_-]{1,64}") String sku) {
        return catalog.find(sku).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@Valid @RequestBody CreateProduct request, HttpServletRequest servlet) {
        Object roles = servlet.getAttribute("authenticatedRoles");
        if (roles == null || !Arrays.asList(roles.toString().split(",")).contains("ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required");
        return catalog.create(new Product(request.sku(), request.name(), request.priceMinor(), request.currency(), request.stock()));
    }
}
