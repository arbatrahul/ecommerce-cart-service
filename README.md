# Ecommerce Cart Service

Microservice for shopping cart management with MongoDB and Redis caching in the Ecommerce Platform.

## Overview

The Cart Service manages user shopping carts including:
- Add items to cart
- Update cart item quantities
- Remove items from cart
- Clear cart
- Cart persistence
- Redis caching for performance

## Features

- ✅ **Cart Management**: Full CRUD operations for cart items
- ✅ **MongoDB Storage**: NoSQL database for flexible cart structure
- ✅ **Redis Caching**: Fast cart retrieval with caching
- ✅ **Product Integration**: Validates products via OpenFeign
- ✅ **Kafka Integration**: Publishes cart events
- ✅ **Service Discovery**: Eureka client integration
- ✅ **OpenFeign**: Service-to-service communication
- ✅ **RESTful API**: Comprehensive REST endpoints

## Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6 or higher
- MongoDB 4.4+ (Port 27017)
- Redis 6.0+ (Port 6379)
- Kafka (Port 9092)
- Eureka Server (Port 8761)
- Product Service (for product validation)

### Database Setup

1. **Start MongoDB**:
   ```bash
   # Using Docker
   docker run -d -p 27017:27017 \
     -e MONGO_INITDB_ROOT_USERNAME=admin \
     -e MONGO_INITDB_ROOT_PASSWORD=password \
     mongo:latest
   ```

2. **Start Redis**:
   ```bash
   # Using Docker
   docker run -d -p 6379:6379 redis:latest
   ```

### Running Locally

1. **Build the project**:
   ```bash
   mvn clean package
   ```

2. **Run the application**:
   ```bash
   java -jar target/cart-service-1.0.0.jar
   ```

3. **Or use Maven**:
   ```bash
   mvn spring-boot:run
   ```

The service will start on `http://localhost:8083`

## API Endpoints

### Cart Endpoints

#### Get User's Cart
```http
GET /api/cart/{userId}
```

#### Add Item to Cart
```http
POST /api/cart/{userId}/add
Content-Type: application/json

{
  "productId": 1,
  "quantity": 2
}
```

#### Update Cart Item Quantity
```http
PUT /api/cart/{userId}/update/{productId}?quantity=3
```

#### Remove Item from Cart
```http
DELETE /api/cart/{userId}/remove/{productId}
```

#### Clear Cart
```http
DELETE /api/cart/{userId}/clear
```

#### Get Cart for Checkout
```http
GET /api/cart/{userId}/checkout
```

## Configuration

### Application Configuration (application.yml)

```yaml
server:
  port: 8083

spring:
  application:
    name: cart-service
  data:
    mongodb:
      uri: mongodb://localhost:27017/cart_service_db
    redis:
      host: localhost
      port: 6379
      database: 1
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10 minutes
  kafka:
    bootstrap-servers: localhost:9092
```

### MongoDB Configuration

- **Database**: `cart_service_db`
- **Collection**: `carts`
- **Auto Index Creation**: Enabled

### Redis Configuration

- **Host**: localhost
- **Port**: 6379
- **Database**: 1
- **TTL**: 10 minutes
- **Connection Pool**: Configured for performance

## Usage Examples

### Get User's Cart

```bash
curl http://localhost:8083/api/cart/1
```

### Add Item to Cart

```bash
curl -X POST http://localhost:8083/api/cart/1/add \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1,
    "quantity": 2
  }'
```

### Update Cart Item Quantity

```bash
curl -X PUT "http://localhost:8083/api/cart/1/update/1?quantity=3"
```

### Remove Item from Cart

```bash
curl -X DELETE http://localhost:8083/api/cart/1/remove/1
```

### Clear Cart

```bash
curl -X DELETE http://localhost:8083/api/cart/1/clear
```

## Architecture

### Cart Data Flow

1. **Add to Cart** → Validate product via Product Service
2. **Cart Update** → Update MongoDB document
3. **Cache Update** → Update Redis cache
4. **Event Publishing** → Publish cart event to Kafka
5. **Response** → Return updated cart

### Cart Structure (MongoDB)

```json
{
  "_id": "cart_id",
  "userId": 1,
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "price": 99.99,
      "addedAt": "2024-01-01T00:00:00Z"
    }
  ],
  "totalAmount": 199.98,
  "itemCount": 2,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

### Components

1. **CartController**: REST endpoints
2. **CartService**: Business logic
3. **CartRepository**: MongoDB operations
4. **ProductServiceClient**: OpenFeign client for product service
5. **Redis Cache**: Caching layer

## Kafka Integration

### Topics Produced

- `cart-events`: Cart operations (add, update, remove, clear)

### Event Types

- `CART_ITEM_ADDED` → Item added to cart
- `CART_ITEM_UPDATED` → Cart item quantity updated
- `CART_ITEM_REMOVED` → Item removed from cart
- `CART_CLEARED` → Cart cleared

### Event Consumers

- **Product Service**: Listens to cart events for stock management
- **Order Service**: Uses cart data for order creation

## Caching Strategy

### Redis Cache

- **Key Format**: `cart:{userId}`
- **TTL**: 10 minutes
- **Cache Strategy**: Write-through
- **Cache Invalidation**: On cart updates

### Cache Benefits

- Fast cart retrieval
- Reduced MongoDB load
- Better performance for frequent cart access

## Testing

### Run Tests

```bash
mvn test
```

### Manual Testing

1. Start MongoDB, Redis, Kafka, Eureka
2. Start Product Service
3. Start Cart Service
4. Add items to cart
5. Verify cart in MongoDB
6. Check Redis cache
7. Verify Kafka events

## Deployment

### Docker

```bash
# Build image
docker build -t ecommerce/cart-service .

# Run container
docker run -p 8083:8083 \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e SPRING_DATA_MONGODB_URI=mongodb://admin:password@mongodb:27017/cart_service_db \
  -e SPRING_DATA_REDIS_HOST=redis \
  ecommerce/cart-service
```

### Production Considerations

1. **MongoDB**:
   - Use replica set for high availability
   - Set up proper indexing
   - Configure connection pooling
   - Set up backups
2. **Redis**:
   - Use Redis Cluster for scalability
   - Configure persistence
   - Set up monitoring
3. **Caching**:
   - Tune cache TTL based on usage
   - Monitor cache hit rates
   - Handle cache failures gracefully
4. **Performance**:
   - Optimize MongoDB queries
   - Use Redis for frequently accessed carts
   - Monitor cart operation latency

## Troubleshooting

### Common Issues

1. **MongoDB Connection Failed**:
   - Verify MongoDB is running
   - Check connection URI
   - Verify credentials

2. **Redis Connection Failed**:
   - Verify Redis is running
   - Check host and port
   - Verify network connectivity

3. **Product Validation Fails**:
   - Verify Product Service is running
   - Check Eureka service registration
   - Verify OpenFeign configuration

4. **Cart Not Found**:
   - Check MongoDB collection
   - Verify user ID
   - Check cache

### Logs

```bash
# Enable debug logging
java -jar target/cart-service-1.0.0.jar \
  --logging.level.org.example.cart=DEBUG \
  --logging.level.org.springframework.data.mongodb=DEBUG \
  --logging.level.org.springframework.cache=DEBUG
```

## Dependencies

- Spring Boot 3.2.0
- Spring Data MongoDB
- Spring Data Redis
- Spring Cache
- Spring Kafka
- Spring Cloud OpenFeign
- Spring Cloud Netflix Eureka Client
- Validation API

## Project Structure

```
src/
├── main/
│   ├── java/org/example/cart/
│   │   ├── CartServiceApplication.java
│   │   ├── controller/
│   │   │   └── CartController.java
│   │   ├── service/
│   │   │   └── CartService.java
│   │   ├── entity/
│   │   │   ├── Cart.java
│   │   │   └── CartItem.java
│   │   ├── repository/
│   │   │   └── CartRepository.java
│   │   ├── dto/
│   │   │   ├── AddToCartRequest.java
│   │   │   └── ProductDto.java
│   │   └── client/
│   │       └── ProductServiceClient.java
│   └── resources/
│       └── application.yml
└── test/
```

## Contributing

1. Follow Spring Boot best practices
2. Write comprehensive tests
3. Optimize MongoDB queries
4. Use Redis caching effectively
5. Handle errors gracefully

## License

This project is part of the Ecommerce Microservices Platform.
