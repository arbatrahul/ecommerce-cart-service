package org.example.cart.controller;

import org.example.cart.entity.Cart;
import org.example.cart.dto.AddToCartRequest;
import org.example.cart.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "*")
public class CartController {

    @Autowired
    private CartService cartService;

    // Get user's cart
    @GetMapping("/{userId}")
    public ResponseEntity<Cart> getCart(@PathVariable Long userId) {
        Cart cart = cartService.getCartByUserId(userId);
        return ResponseEntity.ok(cart);
    }

    // Add item to cart
    @PostMapping("/{userId}/add")
    public ResponseEntity<Map<String, Object>> addToCart(
            @PathVariable Long userId,
            @Valid @RequestBody AddToCartRequest request) {
        
        try {
            Cart cart = cartService.addToCart(userId, request);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Item added to cart successfully");
            response.put("cart", cart);
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Update cart item quantity
    @PutMapping("/{userId}/update/{productId}")
    public ResponseEntity<Map<String, Object>> updateCartItem(
            @PathVariable Long userId,
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        
        try {
            Cart cart = cartService.updateCartItem(userId, productId, quantity);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cart item updated successfully");
            response.put("cart", cart);
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Remove item from cart
    @DeleteMapping("/{userId}/remove/{productId}")
    public ResponseEntity<Map<String, Object>> removeFromCart(
            @PathVariable Long userId,
            @PathVariable Long productId) {
        
        try {
            Cart cart = cartService.removeFromCart(userId, productId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Item removed from cart successfully");
            response.put("cart", cart);
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Clear entire cart
    @DeleteMapping("/{userId}/clear")
    public ResponseEntity<Map<String, Object>> clearCart(@PathVariable Long userId) {
        try {
            cartService.clearCart(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cart cleared successfully");
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Get cart for checkout (used by order service)
    @GetMapping("/{userId}/checkout")
    public ResponseEntity<Cart> getCartForCheckout(@PathVariable Long userId) {
        try {
            Cart cart = cartService.getCartForCheckout(userId);
            return ResponseEntity.ok(cart);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
