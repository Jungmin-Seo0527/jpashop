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

## Note