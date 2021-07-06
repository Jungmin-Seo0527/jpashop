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

### 11-3. 주문 조회 V3: 엔티티를 DTO로 변환 - 페치 조인 최적화

#### OrderApiController.java (추가) - 페치 조인 최적화 적용

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

    @GetMapping("/api/v3/orders")
    public List<OrderDto> ordersV3() {
        return orderRepository.findAllWithItem()
                .stream()
                .map(OrderDto::new)
                .collect(Collectors.toList());
    }

    // ...
}

```

#### OrderRepository.java (추가) - 페치 조인 적용한 쿼리문으로 조회

```java
package jpabook.jpashop.repository;

import jpabook.jpashop.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final EntityManager em;

    // ...

    public List<Order> findAllWithItem() {
        return em.createQuery(
                "select distinct o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d" +
                        " join fetch o.orderItems oi" +
                        " join fetch oi.item i", Order.class
        ).getResultList();
    }
}

```

* 페치 조인으로 SQL이 1번만 실행됨
* `distinct`를 사용한 이유는 일대다 조인이 있으므로 데이터베이스 row가 증가한다. 그 결과 같은 `order`엔티티의 조회 수도 증가하게 된다. JPA의 `distinct`는 SQL에 `distinct`
  를 추가하고, 더해서 같은 엔티티가 조회되면, 애플리케이션에서 중복을 걸러준다. 이 예에서 order가 컬렉션 페치 조인 때문에 중복 조회 되는 것을 막아준다.

* 단점 - 페이징 불가능

> 참고    
> 컬렉션 페치 조인을 사용하면 페이징이 불가능하다. 하이버네이트는 경고 로그를 남기면서 모든 데이터를 DB에서 읽어오고, 메모리에서 페이징 해버린다. (매우 위험하다.)

> 참고    
> 컬렉션 페치 조인은 1개만 사용할 수 있다. 컬렉션 둘 이상에 페치 조인을 사용하면 안된다. 데이터가 부정합하게 조회될 수 있다.

> MTH   
> 일대다 관계의 테이블의 조인에서 페치조인을 사용하면 페이징이 불가능 하는 점이 이번 수업에서 중요한 점이었다. 만약 `setFirstResult()`, `setMaxResult()`를 적용하면 하이버네이트에서 WARNING 메시지를 출력한다.     
> `firstResult/maxResults specified with collection fetch; applying in memory!`   
> 페치 조인에서 페이징 관련 메소드를 사용했기에 이를 메모리상에서 처리하겠다는 메시지인데 이는 out of memory 위험이 크다.
>
> 위의 이유는 우리는 `order`엔티티 단 2개만을 기대했을 것이다. 하지만 일대다 관계를 가지는 테이블들을 조인하는 순간 `order`엔티티가 소위 `뻥튀기`되어 `orderItem`엔티티의 갯수에 맞춰지게 되므로 4개가 된다. 원래는 `order`를 기준으로 페이징을 수행해야 하는데 `order`가 뻥튀기 된 순간 페이징의 기준이 무너진 것이다. 그래서 하이버네이트는 경고 메시지를 주고 메모리에서 페이징을 처리하게 되는 것이다. (내 생각인데 결국 쿼리문을 처리하고 중복 제거까지 수행을 한 후에 페이징을 처리하기 위해서 메모리상에서 페이징을 수행하는 것이 아닌가 싶다...)
>
> 페이징에 관련되서는 아마 다음 수업에 더 자세하게, 그리고 해결법도 나올것이라 예상한다.     
> 기본적으로 내가 페이징을 할 줄 몰라서 이해하는데 쉽지 않았던 수업이었다.

### 11-4. 주문 조회 V3.1: 엔티티를 DTO로 변환 - 페이징과 한계 돌파

* 컬렉션을 페치 조인하면 페이징이 불가능하다.
    * 컬렉션을 페치 조인하면 일대다 조인이 발생하므로 데이터가 예측할 수 없이 증가한다.
    * 일대다에서 일을 기준으로 페이징을 하는 것이 목적이다. 그런데 데이터는 다(N)을 기준으로 row가 생성된다.
    * Order를 기준으로 페이징 하고 싶은데, 다(N)인 `OrderItem`을 조인하면 `OrderItem`이 기준이 되어버린다.
    * 이 경우 하이버네이트는 경고 로그를 남기고 모든 DB 데이터를 읽어서 메모리에서 페이징을 시도한다. 최악의 경우 장애로 이어질 수 있다.

* 한계 돌파
    * 그러면 페이징 + 컬렉션 엔티티를 함께 조회하려면 어떻게 해야할까?
    * 먼저 **ToOne**(OneToOne, ManyToOne)관계를 모두 페치조인 한다. ToOne관계는 row수를 증가시키지 않으므로 페이징 쿼리에 영향을 주지 않는다.
    * 컬렉션은 지연 로딩으로 조회한다.
    * 지연 로딩 성능 최적화를 위해 `hibernate.default_batch_fetch_size`, `@BatchSize`를 적용한다.
        * `hibernate.default_batch_fetch_size`: 글로벌 설정
        * `@BatchSize`: 개별 최적화
        * 이 옵션을 사용하면 컬렉션이나, 프록시 객체를 한꺼번에 설정한 size만큼 IN 쿼리로 조회한다.

#### OrderRepository.java (추가) - `findAllWithMemberDelivery(int offset, int limit)` 추가

```java
package jpabook.jpashop.repository;

import jpabook.jpashop.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final EntityManager em;

    // ...

    public List<Order> findAllWithMemberDelivery(int offset, int limit) {
        return em.createQuery(
                "select o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d", Order.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }
}

```

#### OrderApiController.java (추가) - V3.1: 지연 로딩 성능 최적화, 페이징 적용

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;

    // ...

    @GetMapping("/api/v3.1/orders")
    public List<OrderDto> ordersV3_page(@RequestParam(value = "offset", defaultValue = "0") int offset,
                                        @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return orderRepository.findAllWithMemberDelivery(offset, limit)
                .stream()
                .map(OrderDto::new)
                .collect(Collectors.toList());
    }

    // ...
}

```

#### application.yml (추가) - 최적화 옵션 추가

```yaml
spring:
  datasource:
    url: jdbc:h2:tcp://localhost/~/jpashop;
    username: sa
    password:
    driver-class-name: org.h2.Driver

  jpa:
    hibernate:
      ddl-auto: create
    properties:
      hibernate:
        #        show_sql: true
        format_sql: true
        default_batch_fetch_size: 100 // 추가
```

* 개발로 설정하려면 `@BatchSize`를 적용하면 된다. (컬렉션은 컬렉션 필드에, 엔티티는 엔티티 클래스에 적용)

* 장점
    * 쿼리 호출 수가 `1 + N` -> `1 + 1`로 최적화 된다.
    * 조인보다 DB 데이터 전송량이 최적화 된다. (`Order`와 `OrderItem`을 조인하면 `Order`가 `OrderItem`만큼 중복해서 조회된다. 이 방법은 각각 조회하므로 전송해야 할 중복
      데이터가 없다.)
    * 페치 조인 방식과 비교해서 쿼리 호출 수가 약간 증가하지만, DB 데이터 전송량이 감소한다.
    * 컬렉션 페치 조인은 페이징이 불가능 하지만 이 방법은 페이징이 가능하다.

* 결론
    * ToOne 관계는 페치 조인해도 페이징에 영향을 주지 않는다. 따라서 ToOne 관계는 페치조인으로 쿼리 수를 줄여서 해결하고, 나머지는 `hibernate.default_batch_fetch_size`로
      최적화 하자.

> 참고        
> `defalut_batch_fetch_size`의 크기는 적당한 사이즈를 골라야 하는데, 100 ~ 1000 사이를 선택하는 것을 권장한다. 이 전략을 SQL IN 절을 사용하는데, 데이터베이스에 따라 IN 절 파라미터를 1000으로 제한하기도 한다. 1000으로 잡으면 한번에 1000개를 DB에서 애플리케이션에 불러오므로 DB에 순간 부하가 증가할 수 있다. 하지만 애플리케이션은 100이든 1000이든 결국 전체 데이터를 로딩해야 하므로 메모리 사용량이 같다. 1000으로 설정하는 것이 성능상 가장 좋지만, 결국 DB든 애플리케이션이든 순간 부하를 어디까지 견딜 수 있는지로 결정하면 된다.

> MTH       
> fetch size를 설정한다는 것은 곧 DB에서 한번에 퍼올릴 데이터의 양을 설정하는 것이다. 그래서 강의에서도 fetch size를 100으로 설정하면 1000개의 데이터를 퍼올리기 위해 10번의 쿼리를 날린다고 설명한다.
>
> 페이징 기법을 위해서, 그리고 일대다, 다대다 관계 테이블의 조회에서 꼭 필요한 기법이다.        
> 쿼리를 보면 테이블을 전혀 조인하지 않는다. 단지 `order`엔티티의 `order_id`를 받아서 `orderItem`의 소위 외래키에 해당하는 `order`객체의 `order_id`를 확인해서 같은 값이 존재하는 엔티티를 호출하는 방식인 `WHERE IN`방식으로 조회를 한다.         
> 이 방법을 이용해서 JOIN으로 인한 데이터 중복(row갯수 증가)을 막고 내가 필요한 데이터만 알짜베기로 뽑아낼 수 있다. 단 전체 테이블을 페치조인 하는것 보다는 쿼리가 증가한다. 
>
>
> 결론은 `ToOne = fetch join`, `ToMany = fetch size 설정 (WHERE IN)`

## Note