# TIL

## 3. 애플리케이션 구현 준비

### 3-1. 구현 요구사항

* 회원 기능
    * 회원 등록
    * 회원 조회

* 상품 기능
    * 상품 등록
    * 상품 수정
    * 상품 조회

* 주문 기능
    * 상품 주문
    * 주문 내역 조회
    * 주문 취소

**예제를 단순화 하기 위해 다음 기능은 구현x**

* 로그인과 권한 관리x
* 파라미터 검증과 예외 처리x
* 상품은 도서만 사용
* 카테고리는 사용x
* 배송 정보는 사용x

### 3-2. 애플리케이션 아키텍쳐

![](https://i.ibb.co/RzzN8v9/bandicam-2021-06-25-16-55-47-823.jpg)

#### 계층형 구조 사용

* controller, web: 웹 계층
* service: 비즈니스 로직, 트랜잭션 처리
* repository: JPA를 직접 사용하는 계층, 엔티티 매니저 사용
* domain: 엔티티가 모여 있는 계층, 모든 계층에서 사용

#### 패키지 구조

* jpabook.jpashop
    * domain
    * exception
    * repository
    * service
    * web

**개발 순서: 서비스, 리포지토리 계층을 개발하고, 테스트 케이스를 작성해서 검증, 마지막에 웹 계층 적용**

## Note

***

### 참고 블로그

* [@SpringBootTest로 통합 테스트 하기](https://goddaehee.tistory.com/211)
* [JPA 영속성 컨텍스트 특징](https://blog.baesangwoo.dev/posts/jpa-persistence-context/)
* [JPA 상속관계 매핑](https://hyeooona825.tistory.com/90)
* [JPA 고급 매핑 - 일대일, 일대다, 다대다 매핑](http://wonwoo.ml/index.php/post/834)