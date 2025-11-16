package org.example.cart.repository;

import org.example.cart.entity.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends MongoRepository<Cart, String> {
    
    Optional<Cart> findByUserId(Long userId);
    
    void deleteByUserId(Long userId);
}
