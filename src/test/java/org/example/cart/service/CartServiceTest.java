package org.example.cart.service;

import org.example.cart.client.ProductServiceClient;
import org.example.cart.dto.AddToCartRequest;
import org.example.cart.dto.ProductDto;
import org.example.cart.entity.Cart;
import org.example.cart.entity.CartItem;
import org.example.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private CartService cartService;

    private Cart cart;
    private CartItem cartItem;
    private ProductDto productDto;
    private AddToCartRequest addToCartRequest;

    @BeforeEach
    void setUp() {
        // Setup Product DTO
        productDto = new ProductDto();
        productDto.setId(1L);
        productDto.setName("Test Product");
        productDto.setPrice(new BigDecimal("49.99"));
        productDto.setStock(100);
        productDto.setActive(true);

        // Setup Cart Item
        cartItem = new CartItem();
        cartItem.setProductId(1L);
        cartItem.setProductName("Test Product");
        cartItem.setPrice(new BigDecimal("49.99"));
        cartItem.setQuantity(2);

        // Setup Cart
        cart = new Cart();
        cart.setUserId(1L);
        cart.addItem(cartItem);
        cart.calculateTotals();

        // Setup Add to Cart Request
        addToCartRequest = new AddToCartRequest();
        addToCartRequest.setProductId(1L);
        addToCartRequest.setQuantity(2);
    }

    @Test
    void getCart_ExistingCart() {
        // Given
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        // When
        Cart result = cartService.getCart(1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals(1, result.getItems().size());
        assertEquals(new BigDecimal("99.98"), result.getTotalAmount());
        verify(cartRepository).findByUserId(1L);
    }

    @Test
    void getCart_NewCart() {
        // Given
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Cart result = cartService.getCart(1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals(0, result.getItems().size());
        assertEquals(BigDecimal.ZERO, result.getTotalAmount());
        verify(cartRepository).findByUserId(1L);
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void addToCart_NewItem() {
        // Given
        Cart emptyCart = new Cart();
        emptyCart.setUserId(1L);
        
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(emptyCart));
        when(productServiceClient.getProductById(1L)).thenReturn(productDto);
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Cart result = cartService.addToCart(1L, addToCartRequest);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(1L, result.getItems().get(0).getProductId());
        assertEquals(2, result.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("99.98"), result.getTotalAmount());
        verify(cartRepository).save(any(Cart.class));
        verify(kafkaTemplate).send(eq("cart-events"), eq("item-added"), any());
    }

    @Test
    void addToCart_ExistingItem() {
        // Given
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productServiceClient.getProductById(1L)).thenReturn(productDto);
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Cart result = cartService.addToCart(1L, addToCartRequest);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(4, result.getItems().get(0).getQuantity()); // 2 + 2
        assertEquals(new BigDecimal("199.96"), result.getTotalAmount());
        verify(cartRepository).save(any(Cart.class));
        verify(kafkaTemplate).send(eq("cart-events"), eq("item-updated"), any());
    }

    @Test
    void addToCart_ProductNotFound() {
        // Given
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productServiceClient.getProductById(1L)).thenReturn(null);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> cartService.addToCart(1L, addToCartRequest));
        assertEquals("Product not found", exception.getMessage());
        verify(cartRepository, never()).save(any(Cart.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void addToCart_InsufficientStock() {
        // Given
        productDto.setStock(1); // Less than requested quantity
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productServiceClient.getProductById(1L)).thenReturn(productDto);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> cartService.addToCart(1L, addToCartRequest));
        assertEquals("Insufficient stock", exception.getMessage());
        verify(cartRepository, never()).save(any(Cart.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void addToCart_InactiveProduct() {
        // Given
        productDto.setActive(false);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productServiceClient.getProductById(1L)).thenReturn(productDto);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> cartService.addToCart(1L, addToCartRequest));
        assertEquals("Product is not available", exception.getMessage());
        verify(cartRepository, never()).save(any(Cart.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void updateCartItem_Success() {
        // Given
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productServiceClient.getProductById(1L)).thenReturn(productDto);
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Cart result = cartService.updateCartItem(1L, 1L, 5);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(5, result.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("249.95"), result.getTotalAmount());
        verify(cartRepository).save(any(Cart.class));
        verify(kafkaTemplate).send(eq("cart-events"), eq("item-updated"), any());
    }

    @Test
    void updateCartItem_ItemNotFound() {
        // Given
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> cartService.updateCartItem(1L, 999L, 5));
        assertEquals("Item not found in cart", exception.getMessage());
        verify(cartRepository, never()).save(any(Cart.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void updateCartItem_InsufficientStock() {
        // Given
        productDto.setStock(3); // Less than requested quantity
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productServiceClient.getProductById(1L)).thenReturn(productDto);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> cartService.updateCartItem(1L, 1L, 5));
        assertEquals("Insufficient stock", exception.getMessage());
        verify(cartRepository, never()).save(any(Cart.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void removeFromCart_Success() {
        // Given
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Cart result = cartService.removeFromCart(1L, 1L);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        assertEquals(BigDecimal.ZERO, result.getTotalAmount());
        verify(cartRepository).save(any(Cart.class));
        verify(kafkaTemplate).send(eq("cart-events"), eq("item-removed"), any());
    }

    @Test
    void removeFromCart_ItemNotFound() {
        // Given
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> cartService.removeFromCart(1L, 999L));
        assertEquals("Item not found in cart", exception.getMessage());
        verify(cartRepository, never()).save(any(Cart.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void clearCart_Success() {
        // Given
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Cart result = cartService.clearCart(1L);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        assertEquals(BigDecimal.ZERO, result.getTotalAmount());
        verify(cartRepository).save(any(Cart.class));
        verify(kafkaTemplate).send(eq("cart-events"), eq("cart-cleared"), any());
    }

    @Test
    void getCartForCheckout_Success() {
        // Given
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        // When
        Cart result = cartService.getCartForCheckout(1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals(1, result.getItems().size());
        verify(cartRepository).findByUserId(1L);
    }

    @Test
    void getCartForCheckout_EmptyCart() {
        // Given
        Cart emptyCart = new Cart();
        emptyCart.setUserId(1L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(emptyCart));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> cartService.getCartForCheckout(1L));
        assertEquals("Cart is empty", exception.getMessage());
    }

    @Test
    void calculateTotals_Success() {
        // Given
        Cart testCart = new Cart();
        testCart.setUserId(1L);
        
        CartItem item1 = new CartItem();
        item1.setPrice(new BigDecimal("10.00"));
        item1.setQuantity(2);
        
        CartItem item2 = new CartItem();
        item2.setPrice(new BigDecimal("15.50"));
        item2.setQuantity(3);
        
        testCart.addItem(item1);
        testCart.addItem(item2);

        // When
        testCart.calculateTotals();

        // Then
        assertEquals(new BigDecimal("66.50"), testCart.getTotalAmount()); // (10*2) + (15.50*3)
        assertEquals(5, testCart.getTotalItems()); // 2 + 3
    }
}
