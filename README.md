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

### 11-5. 주문 조회 V4: JPA에서 DTO 직접 조회

#### OrderApiController.java (추가) - V4

```java
package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
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
    private final OrderQueryRepository orderQueryRepository;

    // ...

    @GetMapping("/api/v4/orders")
    public List<OrderQueryDto> ordersV4() {
        return orderQueryRepository.findOrderQueryDtos();
    }

    // ...
}

```

#### OrderQueryRepository.java - 특정 DTO에 종속적인 조회 메소드를 가지는 Repository 객체

* `src/main/java/jpabook/jpashop/repository/order/query/OrderQueryRepository.java`

```java
package jpabook.jpashop.repository.order.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {

    private final EntityManager em;

    public List<OrderQueryDto> findOrderQueryDtos() {
        List<OrderQueryDto> result = findOrders();
        result.forEach(o -> {
            List<OrderItemQueryDto> orderItems = findOrderItems(o.getOrderId());
            o.setOrderItems(orderItems);
        });
        return result;
    }

    private List<OrderItemQueryDto> findOrderItems(Long orderId) {
        return em.createQuery(
                "select new jpabook.jpashop.repository.order.query.OrderItemQueryDto(oi.order.id, i.name, oi.orderPrice, oi.count)" +
                        " from OrderItem oi" +
                        " join oi.item i" +
                        " where oi.order.id = :orderId", OrderItemQueryDto.class)
                .setParameter("orderId", orderId)
                .getResultList();
    }

    private List<OrderQueryDto> findOrders() {
        return em.createQuery(
                "select new jpabook.jpashop.repository.order.query.OrderQueryDto(o.id, m.name, o.orderDate, o.status, d.address)" +
                        " from Order o" +
                        " join o.member m" +
                        " join o.delivery d", OrderQueryDto.class)
                .getResultList();
    }
}

```

#### OrderQueryDto.java - API 맟춤 DTO

* `src/main/java/jpabook/jpashop/repository/order/query/OrderQueryDto.java`

```java
package jpabook.jpashop.repository.order.query;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderQueryDto {

    private Long orderId;
    private String name;
    private LocalDateTime orderDate;
    private OrderStatus orderStatus;
    private Address address;
    private List<OrderItemQueryDto> orderItems;

    public OrderQueryDto(Long orderId, String name, LocalDateTime orderDate, OrderStatus orderStatus, Address address) {
        this.orderId = orderId;
        this.name = name;
        this.orderDate = orderDate;
        this.orderStatus = orderStatus;
        this.address = address;
    }
}

```

#### OrderItemQueryDto.java - OrderQueryDto의 컬렉션 필드인 OrderItem을 받기 위한 DTO

* `src/main/java/jpabook/jpashop/repository/order/query/OrderItemQueryDto.java`

```java
package jpabook.jpashop.repository.order.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class OrderItemQueryDto {

    @JsonIgnore
    private Long orderId;
    private String itemName;
    private int orderPrice;
    private int count;

    public OrderItemQueryDto(Long orderId, String itemName, int orderPrice, int count) {
        this.orderId = orderId;
        this.itemName = itemName;
        this.orderPrice = orderPrice;
        this.count = count;
    }
}

```

* Query: 루트 1번, 컬렉션 N번 실행
* ToOne(N:1, 1:1) 관계들을 먼저 조회하고, ToMany(1:N)관계는 각각 별도로 처리한다.
    * 이런 방식을 선택한 이유는 다음과 같다.
    * ToOne 관계는 조인해도 데이터 row 수가 증가하지 않는다.
    * ToMany(1:N)관계는 조인하면 row 수가 증가한다.
* row 수가 증가하지 않는 ToOne 관계는 조인으로 최적화 하기 쉬우므로 한번에 조회하고, ToMany관계는 최적화 하기 어려우므로 `findOrderItems()`같은 별도의 메서드로 조회한다.

> MTH   
> JPA에서 DTO로 직접 조회하는 방식은 꽤나 복잡하다. 우선 API에 종속적인 DTO를 만들고, Repository가 API에 종속적으로 변하는 것을 막기 위해서 Repository 클래스를 따로 만들어 준다.
>
> DTO로 조회해야 하기 때문에 엔티티 형태 전체로 조회하는 방법과는 차이가 있다. 엔티티로 조회할때는 ToOne 관계에서는 페치조인을 사용해서 한번에 조회가 가능했지만 DTO로 조회할때는 일반 조인을 사용한다. (DTO에는 엔티티에 존재하는 Column이 존재하지 않을 수 있기 때문에... 확실하지는 않다)    
> 그리고 일대다 관계는 따로 조회를 수행해야 한다. 소위 외래키에 해당하는 OrderItem테이블에 존재하는 Order 컬럼의 기본키를 확인한다. Order와 관계를 가지는 orderItem 엔티티를 모두 조회하고 order.setOrderItem()메소드로 세팅을 해준다.
>
> 강의 설명에서도 알수 있듯이 일대다 관계에서 조인을 수행하면 row 수가 조회된 엔티티 갯수만큼 증가하기 때문에 따로 조회를 수행했다. 하지만 이러한 방법도 order가 조회된 갯수만큼 orderItem을 조회하기 위한 쿼리가 생성된다. 즉 1 + N 문제가 발생하게 되는 것이다.
>
> 실제 위의 예제에서 order를 조회하기 위한 쿼리 1개, 그래고 order가 2개 조회되었다.   
> 그리고 각 order와 관계를 가지는 orderItem을 찾기 위한 쿼리문이 조회된 order 엔티티 갯수인 2개만큼 생성된다. 총 3개의 쿼리가 발생한다.

### 11-6. 주문 조회 V5: JPA에서 DTO 직접 조회 - 컬렉션 조회 최적화

#### OrderApiController.java (추가) - V5

```java
package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
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
    private final OrderQueryRepository orderQueryRepository;

    // ...

    @GetMapping("/api/v5/orders")
    public List<OrderQueryDto> orderV5() {
        return orderQueryRepository.findAllByDto_optimization();
    }
}

```

#### OrderQueryRepository.java (추가)

```java
package jpabook.jpashop.repository.order.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {

    private final EntityManager em;

    // ...

    public List<OrderQueryDto> findAllByDto_optimization() {
        List<OrderQueryDto> result = findOrders();

        Map<Long, List<OrderItemQueryDto>> orderItemMap = findOrderItemMap(toOrderIds(result));

        result.forEach(o -> o.setOrderItems(orderItemMap.get(o.getOrderId())));

        return result;
    }

    private Map<Long, List<OrderItemQueryDto>> findOrderItemMap(List<Long> orderIds) {
        return em.createQuery(
                "select new jpabook.jpashop.repository.order.query.OrderItemQueryDto(oi.order.id, i.name, oi.orderPrice, oi.count)" +
                        " from OrderItem oi" +
                        " join oi.item i" +
                        " where oi.order.id in :orderId", OrderItemQueryDto.class)
                .setParameter("orderId", orderIds)
                .getResultList()
                .stream()
                .collect(Collectors.groupingBy(OrderItemQueryDto::getOrderId));
    }

    private List<Long> toOrderIds(List<OrderQueryDto> result) {
        return result.stream()
                .map(OrderQueryDto::getOrderId)
                .collect(Collectors.toList());
    }

    private List<OrderQueryDto> findOrders() {
        return em.createQuery(
                "select new jpabook.jpashop.repository.order.query.OrderQueryDto(o.id, m.name, o.orderDate, o.status, d.address)" +
                        " from Order o" +
                        " join o.member m" +
                        " join o.delivery d", OrderQueryDto.class)
                .getResultList();
    }
}

```

* Query: 루트 1번, 컬렉션 1번
* ToOne 관계들을 먼저 조회하고, 여기서 얻은 식별자 orderId로 ToMany 관계인 `OrderItem`을 한꺼번에 조회
* Map을 사용해서 매칭 성능 향상(O(1))

> MTH   
> 쿼리를 줄이기 위한 아이디어는 간단하다. 각 `order`엔티티의 기본키에 해당하는 `id`를 모아 List에 저장한다. 그리고 `orderItem`엔티티를 조회할 때 `where in`문으로 리스트에 존재하는 `order`엔티티의 `id`를 가지고 있는 모든 `orderItem`엔티티를 한번에 찾는다.    
> 각각의 `orderItem`을 찾는 것이 아니라 필요한 `orderItem`엔티티를 한번에 조회하는 것이다.    
> 결과로 `orderItem`엔티티가 리스트 형태로 저장되어 있다. `orderItem`의 `order_id`를 key로 하여 Map에 매핑한다. 그리고 key와 `order`객체의 id를 매핑해서 `order`객체에 `orderItem`을 setting 한다.
>
> 결론적으로 쿼리문을 날리는 것보다 반복문을 더 도는게 효율적이다.    
> 첫번째 반복문에서 해당하는 `orderItem`을 조회하는 것이 아닌 `orderId`만을 뽑아서 `where in`을 이용해서 한번에 필요한 모든 `orderItem`엔티티를 조회했다.    
> 아마 쿼리문의 결과로 가지고 오는 데이터 양은 훨씬 늘어날 것 같다.

### 11-7. 주문 조회 V6: JPA에서 DTO로 직접 조회, 플랫 데이터 최적화

#### OrderFlatDto.java - 객체로 묶여있는 필드를 하나의 객체로 펼쳐서 몰아준다.

* `src/main/java/jpabook/jpashop/repository/order/query/OrderFlatDto.java`

```java
package jpabook.jpashop.repository.order.query;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class OrderFlatDto {

    private Long orderId;
    private String name;
    private LocalDateTime orderDate;
    private OrderStatus orderStatus;
    private Address address;

    private String itemName;
    private int orderPrice;
    private int count;
}

```

* 본래 `itemName`, `orderPrice`, `count`는 `orderItem`객체의 필드맴버였다.
* 그리고 `orderItem`은 `List`로 묶여 있었다.
* 이를 펼쳐버리고 후에 `Order`테이블과 `OrderItem`테이블을 조인 시킨다.
* 일대다 관계이므로 `orderId`, `name`, `orderDat`, `orderStatus`, `address`가 중복되는 데이터가 존재한다.

#### OrderQueryRepository.java (추가) - V6 조회

```java
package jpabook.jpashop.repository.order.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {

    private final EntityManager em;

    // ...

    public List<OrderFlatDto> findAllByDto_flat() {
        return em.createQuery(
                "select new " +
                        " jpabook.jpashop.repository.order.query.OrderFlatDto(o.id, m.name, o.orderDate, o.status, d.address, i.name, oi.orderPrice, oi.count)" +
                        " from Order o" +
                        " join o.member m" +
                        " join o.delivery d" +
                        " join o.orderItems oi " +
                        " join oi.item i", OrderFlatDto.class)
                .getResultList();
    }
}

```

* 연관되어 있는 모든 테이블을 조인시켜서 한번에 원하는 데이터를 조회한다.(무지성 조인...)

#### OrderApiController.java (추가) - V6 OrderFlatDto -> OrderQueryDto 변환

```java
package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.query.OrderItemQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.*;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    // ...

    @GetMapping("/api/v6/orders")
    public List<OrderQueryDto> orderV6() {
        return orderQueryRepository.findAllByDto_flat()
                .stream()
                .collect(groupingBy(o -> new OrderQueryDto(o.getOrderId(),
                                o.getName(), o.getOrderDate(), o.getOrderStatus(), o.getAddress()),
                        mapping(o -> new OrderItemQueryDto(o.getOrderId(),
                                o.getItemName(), o.getOrderPrice(), o.getCount()), toList())
                ))
                .entrySet()
                .stream()
                .map(e -> new OrderQueryDto(e.getKey().getOrderId(),
                        e.getKey().getName(), e.getKey().getOrderDate(), e.getKey().getOrderStatus(),
                        e.getKey().getAddress(), e.getValue()))
                .collect(toList());
    }
}

```

* Query: 1번
* 단점
    * 쿼리는 한번이지만 조인으로 인해 DB에서 애플리케이션에 전달하는 데이터에 중복 데이터가 추가되므로 상황에 따라 V5 보다 더 느릴 수 도 있다.
    * 애플리케이션에서 추가 작업이 크다.
    * 페이징 불가능

> MTH   
> 플랫하게 데이터를 가져온다는 것은
> 1. 객체로 묶여 있는 필드를 하나의 객체로 몰빵한다.
> 2. 연관관계에 있는 테이블들을 모두 조인시킨다.
> 3. 쿼리 한번에 필요한 데이터를 select 한다.
>
> List로 묶여있는 객체의 필드 변수들이 풀려버리므로 List 이외의 필드(속성값)들이 중복되는 데이터가 존재한다.
>
> 경우에 따라서 V5와 V6방식을 선택해서 사용한다고 한다. (개인적으로 V6는 무지성 조인 방식이라고 명명했다.)

## Note