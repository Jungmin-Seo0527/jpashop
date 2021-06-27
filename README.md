# TIL

## 6. 주문 도메인 개발

**구현 기능**

* 상품 주문
* 주문 내역 조회
* 주문 취소

**순서**

* 주문 엔티티, 주문상품 엔티티 개발
* 주문 리포지토리 개발
* 주문 서비스 개발
* 주문 검색 기능 개발
* 주문 기능 테스트

### 6-1. 주문, 주문상품 엔티티 개발

#### Order.java (추가) - 주문 엔티티에서 생성 메소드, 비즈니스 로직(주문 취소), 조회 로직(전체 주문 가격) 추가

```java
package jpabook.jpashop.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static javax.persistence.CascadeType.*;
import static javax.persistence.FetchType.*;

@Entity
@Table(name = "orders")
@Getter @Setter
public class Order {

    // ...

    // == 생성 메서드 === //
    public static Order createOrder(Member member, Delivery delivery, OrderItem... orderItems) {
        Order order = new Order();
        order.setMember(member);
        order.setDelivery(delivery);
        for (OrderItem orderItem : orderItems) {
            order.addOrderItem(orderItem);
        }
        order.setStatus(OrderStatus.ORDER);
        order.setOrderDate(LocalDateTime.now());
        return order;
    }

    // == 비즈니스 로직 == //

    /**
     * 주문 취소
     */
    public void cancel() {
        if (delivery.getStatus() == DeliveryStatus.COMP) {
            throw new IllegalStateException("이미 배송완료된 상품은 취소가 불가능합니다.");
        }

        this.setStatus(OrderStatus.CANCEL);
        for (OrderItem orderItem : orderItems) {
            orderItem.cancel();
        }
    }

    // == 조회 로직 == //

    /**
     * 전체 주문 가격
     */
    public int getTotalPrice() {
        int totalPrice = 0;
        for (OrderItem orderItem : orderItems) {
            totalPrice += orderItem.getTotalPrice();
        }
        return totalPrice;
    }
}

```

* `createOrder()`: 생성 메서드
    * 주문 엔티티를 생성할 때 사용한다.
    * 주문 회원 배송정보, 주문 상품의 정보를 받아서 실제 주문 엔티티를 생성한다.
* `cancel()`: 주문 취소
    * 주문 취소시 사용한다.
    * 주문 상태를 취소로 변경하고 주문상품에 주문 취소를 알린다.
    * 만약 이미 배송을 완료한 상품이면 주문을 취소하지 못하도록 예외를 발생시킨다.
* `getTotalPrice()`: 전체 주문 가격 조회
    * 주문 시 사용한 전체 주문 가격을 조회한다.
    * 전체 주문 가격을 알려면 각각의 주문상품 가격을 알아야 한다.
    * 로직을 보면 연관된 주문상품들의 가격을 조회해서 더한 값을 반환한다.(실무에서는 주로 주문에 전체 주문 가격 필드를 두고 역정규화 한다.???)

#### OrderItem.java (추가) - 주문상품 엔티티에서 생성메소드, 비즈니스 로직(주문 취소), 조회 로직(주문 상품 전체 가격 조회) 추가

```java
package jpabook.jpashop.domain;

import jpabook.jpashop.domain.item.Item;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

import static javax.persistence.FetchType.*;

@Entity
@Getter @Setter
public class OrderItem {

    // ...

    // == 생성 메서드 == //
    public static OrderItem createOrderItem(Item item, int orderPrice, int count) {
        OrderItem orderItem = new OrderItem();
        orderItem.setItem(item);
        orderItem.setOrderPrice(orderPrice);
        orderItem.setCount(count);

        item.removeStock(count);
        return orderItem;
    }

    // == 비즈니스 로직 == //

    /**
     * 주문 취소
     */
    public void cancel() {
        getItem().addStock(count);
    }

    // == 조회 로직 == //

    /**
     * 주문 상품 전체 가격 조회
     */
    public int getTotalPrice() {
        return getOrderPrice() * getCount();
    }
}

```

* `createOrderItem()`: 생성 메서드
    * 주문 상품, 가격, 수량 정보를 사용해서 주문상품 엔티티를 만든다.
    * 주문 상품이 생성되었으니 `item`의 재고 수량을 감소시킨다.
* `cancel()`: 주문 취소
    * `getItem().addStock(count)`를 호출해서 취소한 주문 수량만큼 상품의 재고를 증가시킨다.
* `getTotalPrice()`: 주문 가격 조회
    * 주문 가격에 수량을 곱한 값을 반환한다.

## Note