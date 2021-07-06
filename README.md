# TIL

## 11. API 개발 고급 - 컬렉션 조회 최적화

주문 내역에서 추가로 주문한 상품 정보를 추가로 조회하자.        
Order 기준으로 컬렉션인 `OrderItem`와 `Item`이 필요하다.

앞의 예제이서는 toOne(OneToOne, ManyToOne)관계만 있었따. 이번에는 컬렉션인 일대다 관계(OneToMany)를 조회하고, 치적화하는 방법을 알아보자.

### 11-1. 주문 조회 V1: 엔티티 직접 노출

#### OrderApiController.java - 주문 조회V1: 엔티티 직접 노출

* `src/main/java/jpabook/jpashop/api/OrderApiController.java`

```java
package jpabook.jpashop.api;

import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;

    @GetMapping("/api/v1/orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName();
            order.getDelivery().getAddress();
            List<OrderItem> orderItems = order.getOrderItems();
            orderItems.forEach(o -> o.getItem().getName());
        }
        return all;
    }
}

```

* `orderItem`, `item`관계를 직접 초기화하면 `Hibernate5Mudule`설정에 의해 엔티티를 JSON으로 생성한다.
* 양방향 연관관계면 무한 루프에 걸리지 않게 한곳에 `@JsonIgnore`를 추가해야 한다.
* 엔티티를 직접 노출하므로 좋은 방법은 아니다.

> MTH       
> 이전의 다대일, 일대일 관계에서 강제적으로 DB에서 엔티티를 꺼내와서 영속성 컨텍스트에 등록을 했었다. (LAZY 강제 초기화)
>
> 이번에도 같은 방법이며, 다른점은 일대다, 다대다 관계에서 `다`에 해당하는 엔티티를 `List`라는 컬렉션에 저장을 했기 때문에 `List`의 모든 엔티티의 LAZY를 초기화 시키는 과정이 필요하다. 즉 `List`의 요소만큼 쿼리를 보내야 한다.(그렇지 않으면 `Hibernate5Module`로 인해 null값이 나온다.)

### 11-2. 주문 조회 V2: 엔티티를 DTO로 변환

#### OrderApiController.java (추가) - 주문 조회 V2: 엔티티를 DTO로 변환

```java
package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;

    // ...

    @GetMapping("api/v2/orders")
    public List<OrderDto> ordersV2() {
        return orderRepository.findAllByString(new OrderSearch()).stream()
                .map(OrderDto::new)
                .collect(Collectors.toList());
    }

    @Getter
    static class OrderDto {

        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;
        private List<OrderItemDto> orderItems;

        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
            orderItems = order.getOrderItems().stream()
                    .map(OrderItemDto::new)
                    .collect(Collectors.toList());
        }
    }

    @Getter
    static class OrderItemDto {

        private String itemName;
        private int orderPrice;
        private int count;

        public OrderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }
    }
}

```

* 지연 로딩으로 너무 많은 SQL 실행
* SQL 실행 수
    * `order`1번
    * `member`, `address` 각각 N번(order 조회 수 만큼)
    * `orderItem` N번(order 조회 수 만큼)
    * `item` N번(orderItem 조회 수 만큼, 여기서 N은 `order`엔티티 갯수가 아닌 `orderItem`엔티티 갯수이다. 위의 N과는 다르다.)

> 참고: 지연 로딩은 영속성 컨텍스트에 있으면 영속성 컨텍스트에 있는 엔티티를 사용하고 없으면 SQL을 실행한다. 따라서 같은 영속성 컨텍스트에서 이미 로딩한 회원 엔티티를 추가로 조회하면 SQL을 실행하지 않는다.

> MTH   
> 이전에 일대일, 다대일에서 발생하는 N + 1이 다시 발생했다. 이번에는 관계 `1`에 해당하는 엔티티를 조회하기 위해서 각각 N번의 쿼리가 날라간다(최악의 경우)   
> 이번에는 `OrderItem`이라는 일대다 관계의 테이블까지 조회해야 한다. 따라서 조회된 `order`의 엔티티에 있는 여러개의 `orderItem`을 조회해야 하고, 추가로 `orderItem`과 다대일 관계의 `item`까지도 조회하기 위한 추가 쿼리가 필요하다.
>
> 위의 예시를 분석해보자.
>
> * `order`엔티티 조회를 위해 `1번`의 쿼리가 발생한다. 결과값은 `order`엔티티 2개가 반환된다.
> * 하나의 `order`엔티티에서 각각 `member`와 `address`를 조회하기 위해 각각 `2번 (N번)`의 쿼리가 날라간다. (여기까지가 N + 1 문제)
> * 각각의 `order`엔티티는 다대일 관계의 `orderItem`테이블에 조회를 위한 쿼리가 발생한다. `order`의 갯수인 `2번 (N번)`발생, 즉 각각의 `order`에서는 2개의 `orderItem`엔티티가 조회된다. (2개의 `order`엔티티에서 총 4개의 `orderItem`엔티티가 조회된다.)
> * 총 4개의 `orderItem`은 `item`의 이름을 알기 위해서 다시 `Item`테이블에서 `item`엔티티를 조회한다. (`4`개의 쿼리 발생)
> * 결과적으로 총 **11번의 쿼리가 발생한다**

## Note