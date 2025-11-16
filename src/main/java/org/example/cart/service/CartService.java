package org.example.cart.service;

import org.example.cart.entity.Cart;
import org.example.cart.entity.CartItem;
import org.example.cart.repository.CartRepository;
import org.example.cart.client.ProductServiceClient;
import org.example.cart.dto.AddToCartRequest;
import org.example.cart.dto.ProductDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CartService {

    @Autowired
    private CartRepository cartRepository;
    
    @Autowired
    private ProductServiceClient productServiceClient;
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Cacheable(value = "carts", key = "#userId")
    public Cart getCartByUserId(Long userId) {
        Optional<Cart> cart = cartRepository.findByUserId(userId);
        if (cart.isPresent()) {
            return cart.get();
        } else {
            // Create new cart if doesn't exist
            Cart newCart = new Cart(userId);
            return cartRepository.save(newCart);
        }
    }

    @CachePut(value = "carts", key = "#userId")
    public Cart addToCart(Long userId, AddToCartRequest request) {
        // Get product details from Product Service
        ProductDto product = productServiceClient.getProductById(request.getProductId());
        
        if (product == null) {
            throw new RuntimeException("Product not found with id: " + request.getProductId());
        }
        
        // Get or create cart
        Cart cart = getCartByUserId(userId);
        
        // Create cart item
        CartItem cartItem = new CartItem(
            product.getId(),
            product.getName(),
            product.getImageUrl(),
            product.getPrice(),
            request.getQuantity()
        );
        
        // Add item to cart
        cart.addItem(cartItem);
        
        // Save cart
        Cart savedCart = cartRepository.save(cart);
        
        // Send Kafka event
        kafkaTemplate.send("cart-events", "item-added", 
            new CartEvent(userId, "ITEM_ADDED", product.getId(), request.getQuantity()));
        
        return savedCart;
    }

    @CachePut(value = "carts", key = "#userId")
    public Cart updateCartItem(Long userId, Long productId, Integer quantity) {
        Cart cart = getCartByUserId(userId);
        cart.updateItemQuantity(productId, quantity);
        
        Cart savedCart = cartRepository.save(cart);
        
        // Send Kafka event
        kafkaTemplate.send("cart-events", "item-updated", 
            new CartEvent(userId, "ITEM_UPDATED", productId, quantity));
        
        return savedCart;
    }

    @CachePut(value = "carts", key = "#userId")
    public Cart removeFromCart(Long userId, Long productId) {
        Cart cart = getCartByUserId(userId);
        cart.removeItem(productId);
        
        Cart savedCart = cartRepository.save(cart);
        
        // Send Kafka event
        kafkaTemplate.send("cart-events", "item-removed", 
            new CartEvent(userId, "ITEM_REMOVED", productId, 0));
        
        return savedCart;
    }

    @CacheEvict(value = "carts", key = "#userId")
    public void clearCart(Long userId) {
        Cart cart = getCartByUserId(userId);
        cart.clearCart();
        
        cartRepository.save(cart);
        
        // Send Kafka event
        kafkaTemplate.send("cart-events", "cart-cleared", 
            new CartEvent(userId, "CART_CLEARED", null, 0));
    }

    // For order processing - get cart and clear it
    @CacheEvict(value = "carts", key = "#userId")
    public Cart getCartForCheckout(Long userId) {
        Cart cart = getCartByUserId(userId);
        
        if (cart.getItems().isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }
        
        // Send checkout event
        kafkaTemplate.send("cart-events", "checkout-initiated", 
            new CartEvent(userId, "CHECKOUT_INITIATED", null, cart.getTotalItems()));
        
        return cart;
    }

    // Inner class for Kafka events
    public static class CartEvent {
        private Long userId;
        private String eventType;
        private Long productId;
        private Integer quantity;
        
        public CartEvent(Long userId, String eventType, Long productId, Integer quantity) {
            this.userId = userId;
            this.eventType = eventType;
            this.productId = productId;
            this.quantity = quantity;
        }
        
        // Getters and setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
