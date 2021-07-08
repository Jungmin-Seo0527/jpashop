# TIL

## 14. 다음으로 (스프링 데이터 JPA, QueryDSL)

### 14-1. 스프링 데이터 JAP 소개

스프링 데이터 JPA는 JPA를 사용할 때 지루하게 반복하는 코드를 자동화 해준다. 이미 라이브러리는 포함되어 있다. 기존의 `MemberRepository`를 스프링 데이터 JPA로 변경해보자.

#### MemberRepository.java - 스프링 데이터 JPA 적용

```java
package jpabook.jpashop.repository;

import jpabook.jpashop.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByName(String name);
}

```

`findOne()`-> `findById()`로 변경해야 한다.

* 스프링 데이터 JPA는 `JpaRepository`라는 인터페이스를 제공하는데, 여기에 기본적인 CRUD 기능이 모두 제공된다. (일반적으로 상상할 수 있는 모든 기능이 다 포함되어 있다.)
* `findByName`처럼 일반화 하기 어려운 기능도 메서드 이름으로 정확한 JPQL 쿼리를 실행한다.
    * `select m from member m wher m.name = :name`
* 개발자는 인터페이스만 만들면 된다. 구현체는 스프링 데이터 JPA가 애플리케이션 실행시점에 주입해준다.

스프링 데이터 JPA는 스프링과 JPA를 활용해서 애플리케이션을 만들 때 정말 편리한 기능을 많이 제공한다. 단순히 편리함을 넘어서 때로는 마법을 부리는 것 같을 정도로 놀라운 개발 생산성의 세계로 우리를 이끌어
준다.      
하지만 **스프링 데이터 JPA는 JPA를 사용해서 이런 기능을 제공할 뿐이다. 결국 JPA 자체를 잘 이해하는 것이 가장 중요**하다.

### 14-2. QueryDSL 소개

실무에서는 조건에 따라서 실행되는 쿼리가 달라지는 동적 쿼리를 많이 사용한다.   
주문 내역 검색으로 돌아가보고, 이 예제를 Querydsl로 바꾸어 보자.

#### MemberRepository.java - QueryDSL 적용 (`findAll()`)

```java
package jpabook.jpashop.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

import static jpabook.jpashop.domain.QMember.member;
import static jpabook.jpashop.domain.QOrder.order;

@Repository
public class OrderRepository {

    private final EntityManager em;
    private final JPAQueryFactory query;

    public OrderRepository(EntityManager em) {
        this.em = em;
        this.query = new JPAQueryFactory(em);
    }

    // ...

    public List<Order> findAll(OrderSearch orderSearch) {

        return query
                .select(order)
                .from(order)
                .join(order.member, member)
                .where(statusEq(orderSearch.getOrderStatus()), nameLike(orderSearch.getMemberName()))
                .limit(1000)
                .fetch();
    }

    private BooleanExpression statusEq(OrderStatus statusCond) {
        if (statusCond == null) {
            return null;
        }
        return order.status.eq(statusCond);
    }

    private BooleanExpression nameLike(String memberName) {
        if (!StringUtils.hasText(memberName)) {
            return null;
        }
        return member.name.like(memberName);
    }
}

```

#### build.gradle - querydsl 추가

```groovy
buildscript {
    dependencies {
        classpath("gradle.plugin.com.ewerk.gradle.plugins:querydsl-plugin:1.0.10")
    }
}

plugins {
    id 'org.springframework.boot' version '2.5.1'
    id 'io.spring.dependency-management' version '1.0.11.RELEASE'
    id 'java'
}

apply plugin: 'io.spring.dependency-management'
apply plugin: "com.ewerk.gradle.plugins.querydsl"

group = 'jpabook'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '11'

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-devtools'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-hibernate5'

    implementation 'com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.5.6'

    compileOnly 'org.projectlombok:lombok'
    runtimeOnly 'com.h2database:h2'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation("org.junit.vintage:junit-vintage-engine") {
        exclude group: "org.hamcrest", module: "hamcrest-core"
    }

    //querydsl 추가
    implementation 'com.querydsl:querydsl-jpa'
    //querydsl 추가
    implementation 'com.querydsl:querydsl-apt'
}

//querydsl 추가
//def querydslDir = 'src/main/generated'
def querydslDir = "$buildDir/generated/querydsl"

querydsl {
    library = "com.querydsl:querydsl-apt"
    jpa = true
    querydslSourcesDir = querydslDir
}
sourceSets {
    main {
        java {
            srcDirs = ['src/main/java', querydslDir]
        }
    }
}
compileQuerydsl {
    options.annotationProcessorPath = configurations.querydsl
}
configurations {
    querydsl.extendsFrom compileClasspath
}

test {
    useJUnitPlatform()
}
```

Querydsl은 SQL(JPQL)과 모양이 유사하면서 자바 코드로 동적 쿼리를 편리하게 생성할 수 있다.

실무에서는 복잡한 동적 쿼리를 많이 사용하게 되는데, 이때 Querydsl을 사용하면 높은 개발 생산성을 얻으면서 동시에 쿼리 오류를 컴파일 시점에 빠르게 잡을 수 있다.   
꼭 동적 쿼리가 아니라 정적 쿼리인 경우에도 다음과 같은 이유로 QueryDSL을 사용하는 것이 좋다.

* 직관적인 문법
* 컴파일 시점에 빠른 문법 오류 발견
* 코드 자동완성
* 코드 재사용(이것은 자바다.)
* JPQL new 명령어와는 비교가 안될 정도로 깔끔한 DTO 조회를 지원한다.

QueryDSL은 JPQL을 코드로 만드는 빌더 역할을 할 뿐이다. 따라서 JPQL을 잘 이해하면 금방 배울수 있다.   
**QueryDSL은 JPA로 애플리케이션을 개발 할 때 선택이 아닌 필수라 생각한다.**

> MTH   
> 이번 챕터의 스프링 데이터 JPA나 QueryDSL은 소개 정도의 내용만 있다. 추가 강의를 통해서 더 자세하게 배울 수 있다.   
> QueryDSL을 적용하기 위해 `build.gradle`설정을 추가할 때 버전 차이 때문에 애를 먹었다. 강의에서의 gradle은 예상컨테 5버전일 것이다. 그리고 지금 내 gradle 버전은 7이다. 버전 차이로 인해 강의 내용에 나오는 `build.gradle`로는 querydsl을 적용할 수 없다.
>
> 결국 구글링과 질문 게시판을 통해서 해결했다. 추가로 강의에서는 QueryDSL 관련 객체?? 즉 컴파일 후 생성되는 클래스들이 `generate`폴더에 위치해 있다. 하지만 `def querydslDir = "$buildDir/generated/querydsl"`설정으로 `build`폴더로 위치가 바뀐다. (`generate`폴더에 생성되지 않는다.) 기본적으로 컴파일 후 생기는 클래스들은 commit할 때 add 하지 않는다. 이런 이유와 아마 여러가지 이유? 로 관리하기 편하게 하기 위해서 `build`폴더에 위치시킨 것 같다.   
> 
> 이유는 모르겠지만 기존에 있던 `generate`폴더는 사라져 버렸다. 

## Note