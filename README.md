# TIL

## 9. API 개발 고급 - 지연 로딩과 조회 성능 최적화

> 참고: **매우 중요!!!!!**

### 9-1. 간단한 주문 조회 V1: 엔티티를 직접 노출

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

### 9-2. 간단한 주문 조회 V2: 엔티티를 DTO로 변환

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

## Note