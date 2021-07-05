# TIL

## 10. API 개발 고급 - 지연 로딩과 조회 성능 최적화

> 참고: **매우 중요!!!!!**

### 10-1. 간단한 주문 조회 V1: 엔티티를 직접 노출

#### OrderSimpleApiController.java - 주문 조회 컨트롤러

* `src/main/java/jpabook/jpashop/api/OrderSimpleApiController.java`

```java
package jpabook.jpashop.api;

import jpabook.jpashop.domain.Order;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * xToOne(ManyToOne, OneToOne)
 * Order
 * Order -> Member
 * Order -> Delivery
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;

    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName(); // Lazy 강제 초기화
            order.getDelivery().getAddress(); // Lazy 강제 초기화
        }
        return all;
    }
}

```

* 엔티티를 직접 노출하는 것은 좋지 않다.
* `order` -> `member`와 `order` -> `address`는 지연 로딩이다. 따라서 실제 엔티티 대신에 프록시 존재
* jackson 라이브러리는 기본적으로 이 프록시 객체를 json으로 어떻게 생성해야 하는지 모름 -> 예외 발생
* `Hibernate5Module`을 스프링 빈으로 등록하면 해결(스프링 부트 사용중)

##### Hibernate5Module 등록

```java
package jpabook.jpashop;

import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class JpashopApplication {

    public static void main(String[] args) {
        SpringApplication.run(JpashopApplication.class, args);
    }

    @Bean
    Hibernate5Module hibernate5Module() {
        Hibernate5Module hibernate5Module = new Hibernate5Module();
        // hibernate5Module.configure(Hibernate5Module.Feature.FORCE_LAZY_LOADING, true);
        return hibernate5Module;
    }
}

```

> 참고: 다음 라이브러리를 추가
> ```
> com.fasterxml.jackson.datatype:jackson-datatype-hibernate5
> ```

* 다음과 같이 설정하면 강제로 지연 로딩 가능

```java
package jpabook.jpashop;

import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class JpashopApplication {

    public static void main(String[] args) {
        SpringApplication.run(JpashopApplication.class, args);
    }

    @Bean
    Hibernate5Module hibernate5Module() {
        Hibernate5Module hibernate5Module = new Hibernate5Module();
        hibernate5Module.configure(Hibernate5Module.Feature.FORCE_LAZY_LOADING, true); // 지연 로딩 강제 로딩
        return hibernate5Module;
    }
}

```

* 이 옵션을 키면 `order -> member`, `member -> orders` 양방향 연관관계를 계속 로딩하게 된다. 따라서 `@JsonIgnore`옵션을 한곳에 주어야 한다.

> 주의        
> 엔티티를 직접 노출할 때는 양방향 연관관계가 걸린 곳은 꼭! 한곳을 `@JsonIgnore`처리 해야 한다. 안그러면 양쪽을 서로 호출하면서 무한 루프가 걸린다.

> 참고        
> 앞에서 계속 강조했듯이 정말 간단한 애플리케이션이 아니면 엔티티를 API 응답으로 외부로 노출하는 것은 좋지 않다. 따라서 `Hibernate5Module`를 사용하기 보다는 DTO로 변환해서 반환하는 것이 더 좋은 방법이다.

> 주의        
> 지연 로딩(LAZY)을 피하기 위해 즉시 로딩(EAGER)으로 설정하면 안된다! 즉시 로딩 때문에 연관관계가 필요 없는 경우에도 데이터를 항상 조회해서 성능 문제가 발생할 수 있다. 즉시 로딩으로 설정하면 성능 튜닝이 매우 어려워 진다.      
> 항상 지연 로딩을 기본으로 하고, 성능 최적화가 필요한 경우에는 페치 조인(fetch join)을 사용해라! (V3 에서 설명)

### 10-2. 간단한 주문 조회 V2: 엔티티를 DTO로 변환

#### OrderSimpleApiController.java (추가) - 엔티티를 DTO로 변환한 컨트롤러

```java
package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * xToOne(ManyToOne, OneToOne)
 * Order
 * Order -> Member
 * Order -> Delivery
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;

    // ...

    @GetMapping("/api/v2/simple-orders")
    public List<SimpleOrderDto> ordersV2() {

        // Order 2개
        // N + 1 문제 (N = 2 -> 1 + 회원N(2) + 배달N(2) = 5번의 쿼리가 날라감)
        return orderRepository.findAllByString(new OrderSearch()).stream()
                .map(SimpleOrderDto::new)
                .collect(toList());
    }

    @Data
    static class SimpleOrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName(); // LAZY 초기화
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress(); // LAZY 초기화
        }
    }
}

```

* 엔티티를 DTO로 변환하는 일반적인 방법이다.
* 쿼리가 총 1 + N + N번 실행된다. (v1과 쿼리수 결과는 같다.)
    * `order`조회 1번(`order`조회 결과 수가 N이 된다.)
    * `order -> member`지연 로딩 조회 N번
    * `order -> delivery`지연 로딩 조회 N번
    * 예) `order`의 결과가 4개면 최악의 경우 1 + 4 + 4번 실행된다.(최악의 경우)
        * 지연로딩은 연속성 컨텍스트에서 조회하므로, 이미 조회된 경우 쿼리를 생략한다.

> MTH: N + 1문제 정리     
> 위의 코드에서 `order`의 결과로 2개(N)의 결과가 나온다. (현재 주문은 2개 존재)       
> `order`객체를 `SimpleOrderDto`로 변환하는 과정에서 `name = order.getMember().getName()`을 수행하게 된다. 여기서 LAZY가 초기화 된다. 즉 현재 영속성 컨텍스트에 `order`는 존재한다.(방금 쿼리 날려서 조회 했으니깐...)       
> 하지만 `order`와 관련되어 있는 `member`는 영속성 컨텍스트에 존재하지 않는다. 그렇기에 DB에 member를 조회하는 쿼리를 날려야 한다.      
> 같은 이유로 `address = order.getDelivery().getAddress()`과정에서 `delivery`엔티티는 영속성 컨텍스트에 존재하지 않으므로 DB에 쿼리를 날려서 조회한 후에 `getAddress()`를 수행하게 된다.        
> 이 과정을 `order`의 갯수인 2번(N)수행하게 된다.      
> 첫 `order`조회 1번 (결과`order` N개)
> `order.getMember().getName()`을 위한 `member` 조회 쿼리 (N번)     
> `order.getDelivery().getAddress()`을 위한 `delivery`조회 쿼리 (N번)       
> **총 5번의 쿼리** 이것이 **N + 1 문제**
>
> 단 이는 최악의 경우이며 만약 order의 두 주문자가 같은 `member`라면 첫번째 조회에서 영속성 컨텍스트에 존재하게 되니 두번째 조회에서는 쿼리를 날리지 않아도 된다. (하지만 항상 최악의 상황을 고려해야 하기 때문에 **N + 1**)

### 10-3. 간단한 주문 조회 V3: 엔티티를 DTO로 변환 - 페치 조인 최적화

#### OrderSimpleApiController.java (추가) - 페치 조인 최적화 컨트롤러

```java
package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * xToOne(ManyToOne, OneToOne)
 * Order
 * Order -> Member
 * Order -> Delivery
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;

    // ...

    @GetMapping("/api/v3/simple-orders")
    public List<SimpleOrderDto> ordersV3() {
        return orderRepository.findAllWithMemberDelivery().stream()
                .map(SimpleOrderDto::new)
                .collect(Collectors.toList());
    }

    @Data
    static class SimpleOrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName(); // LAZY 초기화
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress(); // LAZY 초기화
        }
    }
}

```

#### OrderRepository.java (추가) - 페치 조인 쿼리문으로 테이블 조회

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

    /// ...

    public List<Order> findAllWithMemberDelivery() {
        return em.createQuery(
                "select o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d", Order.class
        ).getResultList();
    }
}

```

* 엔티티를 페치 조인(fetch join)을 사용해서 쿼리 1번에 조회
* 페치 조인으로 `order -> member`, `order -> delivery`는 이미 조회 된 상태 이므로 지연 로딩x

> MTH: `OrderSearch`        
> `orderSearch`객체에서 `memberName`이나 `orderStatus`가 null값인 경우에는 전체를 조회하도록 동적 쿼리를 작성하였다. (`findAllByString()`, `findAllByCriteria()`)      
> 동적 쿼리는 후에 배울 예정이므로 이번 강의에서는 동적 쿼리가 필요한 경우만 소개해 주었다. 그래서 순간 `orderSearch`의 필드에 있는 값이 `null`값인데 어떻게 모든 엔티티를 조회하는지 의문이 들었다.      
> 정답은 동적 쿼리이다. `null`값인 경우에는 전체를 조회하도록 쿼리문을 동적으로 작성했다.

> MTH: join fetch       
> `join fetch`는 정식 SQL문이 아니다. JPA에서만 쓰이는 쿼리문이다.         
> 페치 조인의 원리는 `Order`를 조회할 때 이와 관계를 가지고 있는 모든 테이블에 대한 엔티티도 동시에 조회하는 것이다.     
> 이전의 동적 쿼리문으로 작성한 `findAllByString()`메소드는 단지 `order`테이블에서만 엔티티를 조회했다. 그렇기 때문에 `Order`테이블과 양방향 연관관계를 가지는 `Member`테이블과, `Delivery`테이블의 조회는 전혀 수행하지 않았다. 결국 영속성 컨텍스트에 필요한 엔티티가 존재하지 않았고, 별도의 쿼리문을 DB에 전송할 수 밖에 없었다.
>
> 이번에는 `Order`테이블에서 필요한 엔티티를 조회함과 동시에 `Member`테이블과, `Delivery`테이블을 `Order`와 Join 하여 원하는 모든 엔티티를 한번에 조회한다.       
> 그리고 생성자에서 필요로 하는 데이터는 모두 영속성 컨텍스트에 존재하기 때문에 더이상의 쿼리문은 필요하지 않다.       
> 이 방법을 이용하면 이전의 5번의 쿼리문이 1번의 쿼리문으로 바뀌므로 성능이 개선된다.

### 10-4. 간단한 주문 조회 V4: JPA에서 DTO로 바로 조회

#### OrderSimpleQueryRepository.java - 조회 전용 리포지토리

* `src/main/java/jpabook/jpashop/repository/order/simplequery/OrderSimpleQueryRepository.java`

```java
package jpabook.jpashop.repository.order.simplequery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderSimpleQueryRepository {

    private final EntityManager em;

    public List<OrderSimpleQueryDto> findOrderDtos() {
        return em.createQuery(
                "select new jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto(o.id, m.name, o.orderDate, o.status, d.address)  " +
                        " from Order o" +
                        " join o.member m" +
                        " join o.delivery d", OrderSimpleQueryDto.class)
                .getResultList();
    }
}

```

#### OrderSimpleQueryDto.java - 리포지토리에서 DTO 직접 조회

* `src/main/java/jpabook/jpashop/repository/order/simplequery/OrderSimpleQueryDto.java`

```java
package jpabook.jpashop.repository.order.simplequery;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderSimpleQueryDto {
    private Long orderId;
    private String name;
    private LocalDateTime orderDate;
    private OrderStatus orderStatus;
    private Address address;

    public OrderSimpleQueryDto(Long orderId, String name, LocalDateTime orderDate, OrderStatus orderStatus, Address address) {
        this.orderId = orderId;
        this.name = name;
        this.orderDate = orderDate;
        this.orderStatus = orderStatus;
        this.address = address;
    }
}

```

* 일반적인 SQL을 사용할 때 처럼 원하는 값을 선택해서 조회
* `new`명령어를 사용해서 JPQL의 결과를 DTO로 즉시 변환
* SELECT절에서 원하는 데이터를 직접 선택하므로 DB -> 애플리케이션 네트웍 용량 최적화(생각보다 미비)
* 리포지토리 재사용성 떨어짐, API 스펙에 맞춘 코드가 리포지토리에 들어가는 단점

#### 정리

엔티티를 DTO로 변환하거나, DTO로 바로 조회하는 두가지 방법은 각각 장단점이 있다. 둘중 상황에 따라서 더 나은 방법을 선택하면 된다. 엔티티로 조회하면 리포지토리 재사용성도 좋고, 개발도 단순해진다. 따라서
권장하는 방법은 다음과 같다.

##### 쿼리 방식 선택 권장 순서

1. 우선 엔티티를 DTO로 변환하는 방법을 선택한다.
2. 필요하면 페치 조인으로 성능을 최적화 한다. -> 대부분의 성능 이슈가 해결된다.
3. 그래도 안되면 DTO로 직접 조회하는 방법을 사용한다.
4. 최후의 방법은 JPA가 제공하는 네이티브 SQL이나 스프링 JDBC Template을 사용해서 SQL을 직접 사용한다.

> MTH       
> `OrderSimpleQueryDto`는 API 스펙에 종속적인 DTO이다. 오로직 API를 위해서 Entity중 필요한 데이터만 뽑는 객체이다. 처음부터 `OrderRepository`에서 `OrderSimpleQueryDto`가 가진 데이터만 조회하려면 위와 같이 `new`를 이용해서 쿼리문을 직접 작성한다.       
> 그런데 만약 `OrderRepository`객체에 위의 과정을 수행하는 메소드가 존재한다면 `repository`계층이 `controller`계층에 종속적이게 된다. 정확하게 말하면 `repository`가 API에 종속적이게 된다. 이는 별로 좋은 코드가 아니다.
>
> 따라서 API에 종속적인 DTO를 바로 조회하는 메소드를 모아서 따로 `repository`객체를 생성한다. 그것이 `OrderSimpleQueryRepository`이다. 이 객체에는 이후에 API 혹은 DTO에 종속적인 조회를 모아둔다. (아마 이 방법이 가장 널리 쓰이는 듯 하다.)

## Note