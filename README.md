# TIL

## 1. 프로젝트 환경설정

### 1-1. 프로젝트 생성

#### build.gradle

```groovy
plugins {
    id 'org.springframework.boot' version '2.5.1'
    id 'io.spring.dependency-management' version '1.0.11.RELEASE'
    id 'java'
}

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
    compileOnly 'org.projectlombok:lombok'
    runtimeOnly 'com.h2database:h2'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

test {
    useJUnitPlatform()
}

```

### 1-2 라이브러리 살펴보기

#### gradle 의존관계 보기

```
./gradlew dependencies -configuration compileClasspath
```

#### 스프링 부트 라이브러리 살펴보기

* spring-boot-starter-web
    * spring-boot-starter-tomcat: 톰캣(웹서버)
    * spring-webmvc: 스프링 웹 MVC
* spring-boot-starter-thymeleaf: 타임리프 템플릿 엔진(View)
* spring-boot-starter-data-jpa
    * spring-boot-starter-aop
    * spring-boot-start-jdbc
        * HikariCP 커넥션 풀(부트 2.0 기본)
    * hibernate + JPA: 하이버네이트 + JPA
    * spring-data-jpa: 스프링 데이터 JPA
* spring-boot-starter(공통): 스프링 부트 + 스프링 코어 + 로깅
    * spring-boot
        * spring-core
    * spring-boot-starter-logging
        * logback, slf4j

#### 테스트 라이브러리

* spring-boot-starter-test
    * junit: 테스트 프레임워크
    * mockito: 목 라이브러리
    * assertj: 테스트 코드를 좀 더 편하게 작성하게 도와주는 라이브러리
    * spring-test: 스프링 통합 테스트 지원


* 핵심 라이브러리
    * 스프링 MVC
    * 스프링 ORM
    * JPA, 하이버네이트
    * 스프링 데이터 JPA
* 기타 라이브러리
    * H2 데이터베이스 클라이언트
    * 커넥션 풀: 부트 기본은 HikariCP
    * WEB(thymeleaf)
    * 로깅 SLF4J & LogBack
    * 테스트

### 1-3. View 환경 설정

#### thymeleaf 템플릿 엔진

* thymeleaf 공식 사이트: https://www.thymeleaf.org/
* 스프링 공식 튜토리얼: https://spring.io/guides/gs/serving-web-content/
* 스프링부트
  메뉴얼: https://docs.spring.io/spring-boot/docs/2.1.6.RELEASE/reference/html/boot-features-developing-web-applications.html#boot-features-spring-mvc-template-engines
* 스프링 부트 thymeleaf viewName 매핑
    * `resources:template/` + {ViewName}+.`.html`

```java
package jpabook.jpashop;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HelloController {

    @GetMapping("hello")
    public String hello(Model model) {
        model.addAttribute("data", "hello!!!");
        return "hello";
    }
}

```

#### thymeleaf 템플릿엔진 동작 확인 (hello.html)

* hello.html
    * `src/main/resources/templates/hello.html`
    ```html
    <!DOCTYPE HTML>
    <html xmlns:th="http://www.thymeleaf.org">
    <head>
        <title>Hello</title>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    </head>
    <body>
    <p th:text="'안녕하세요. ' + ${data}">안녕하세요. 손님</p>
    </body>
    </html>
    ```

* index.html
    * `src/main/resources/static/index.html`
    ```html
    <!DOCTYPE HTML>
    <html xmlns:th="http://www.thymeleaf.org">
    <head>
        <title>Hello</title>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    </head>
    <body>
    Hello
    <a href="/hello">hello</a>
    </body>
    </html>
    ```

> 참고    
> `spring-boot-devtools`라이브러리를 추가하면, `.html`파일을 컴파일만 해주면 서버 재시작 없이 View 파일 변경이 가능하다.    
> 인텔리제이 컴파일 방법: 메뉴 build -> Recompile

### 1-4. H2 데이터베이스 설치

> **주의!!!**
> **Version 1.4 200 사용 필수!!!**

* 1.4.200 버전 다운로드 링크
    * 윈도우 설치 버전: https://h2database.com/h2-setup-2019-10-14.exe
    * 윈도우, 맥, 리눅스 실행 버전: http://h2database.com/h2-2019-10-14.zip

* https://www.h2database.com
* 다운로드 및 설치
* 데이터베이스 파일 생성 방법
    * `jdbc:h2:~/jpashop`(최소 한번)
    * `~/jpashop.mv.db`파일 생성 확인
    * 이후부터는 `jdbc:h2:tcp://localhost/~/jpashop`이렇게 접속

> 처음 H2를 설치하고 실행할 때 bin 폴더에 있는 build.bat 파일을 실행해야 한다.     
> H2 폴더에도 build.bat 파일이 존재하지만 실행해도 아무런 일도 일어나지 않는다. 꼭 bin 폴더의 build.bat 파일을 실행하자.

> 주의    
> H2 데이터베이스의 MVCC 옵션은 H2 1.4.198 부터 제거되었다. **1.4.200 버전에서는 MVCC 옵션을 사용하면 오류가 발생**

### 1-5. JPA와 DB 설정, 동작 확인

#### application.yml

* `src/main/resources/application.yml`

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


logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.type: trace
```

* `spring.jpa.hibernate.ddl-auto`: create
    * 이 옵션은 애플리케이션 실행 시점에 테이블을 drop 하고, 다시 생성한다.

> 참고    
> 모든 로그 출력은 가급적 로거를 통해 남겨야 한다.
> `show_sql`: `System.out`에 하이버네이트 실행 SQL을 남긴다.
> `org.hibernate.SQL`: logger를 통해 하이버네티트 실행 SQL을 남긴다.

#### Member.java - 회원 엔티티

* `src/main/java/jpabook/jpashop/Member.java`

```java
package jpabook.jpashop;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
@Getter
@Setter
public class Member {

    @Id
    @GeneratedValue
    private Long id;
    private String username;
}

```

#### MemberRepository.java - 회원 저장소

* `src/main/java/jpabook/jpashop/MemberRepository.java`

```java
package jpabook.jpashop;

import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Repository
public class MemberRepository {

    @PersistenceContext
    private EntityManager em;

    public Long save(Member member) {
        em.persist(member);
        return member.getId();
    }

    public Member find(Long id) {
        return em.find(Member.class, id);
    }
}

```

#### MemberRepositoryTest.java - 회원 저장소 테스트 코드

* `src/test/java/jpabook/jpashop/MemberRepositoryTest.java`

```java
package jpabook.jpashop;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
public class MemberRepositoryTest {

    @Autowired
    MemberRepository memberRepository;

    @Test
    @Transactional
    @Rollback(false)
    public void testMember() throws Exception {
        // given
        Member member = new Member();
        member.setUsername("memberA");

        // when
        Long savedId = memberRepository.save(member);
        Member findMember = memberRepository.find(savedId);

        // then
        assertThat(findMember.getId()).isEqualTo(member.getId());
        assertThat(findMember.getUsername()).isEqualTo(member.getUsername());
        assertThat(findMember).isEqualTo(member);
    }
}
```

> 주의!   
> `@Test`는 JUnit4를 사용하면 `org.junit.Test`를 사용해야 한다. 만약 Junit5를 사용하면 그것에 맞게 사용하면 된다.

* Entity, Repository 동작 확인

> 오류사항 확인 필요!!!   
> 1-5. JPA와 DB 설정, 동작 확인 MemberRepositoryTest 실행 오류

#### 쿼리 파라미터 로그 남기기

* 로그에 다음을 추가하기 `org.hibernate.type`: SQL 실행 파라미터를 로그로 남긴다.
* 외부 라이브러리 사용
    * https://github.com/gavlyukovskiy/spring-boot-data-source-decorator

스프링 부트를 사용하면 이 라이브러리만 추가하면 된다.

```groovy
implementation 'com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.5.6'
```

> 참고    
> 쿼리 파라미터를 로그로 남기는 외부 라이브러리는 시스템 자원을 사용하므로, 개발 단계에서는 편하게 사용해도 된다. 하지만 운영시스템에 적용하려면 꼭 성능테스트를 하고 사용하는 것이 좋다.

## Note - 오류사항 정리

***

### 1-5. JPA와 DB 설정, 동작 확인 MemberRepositoryTest 실행 오류

Junit4 내 스프링 2.5.1 은 디폴트로 Junit5 을 설치해준다. 강의에서는 JUnit4를 사용하니 따로 추가해주어야 한다.

```groovy
dependencies {
    testImplementation("org.junit.vintage:junit-vintage-engine") {
        exclude group: "org.hamcrest", module: "hamcrest-core"
    }
}
```

여기까지 와도 테스트 코드가 에러가 발생한다.

```
Access to DialectResolutionInfo cannot be null when 'hibernate.dialect' not set
```

hibernate를 찾지 못하는 에러인것 같은데...

* `application.yml`
    ```yaml
    spring:
      datasource:
        url: jdbc:h2:tcp://localhost/~/jpashop;MVCC=TRUE
        username: sa
        password:
        driver-class-name: org.h2.Driver
    ```
    * 설정 정보에서 `MVCC=TRUE`부분을 제거해주면 된다.
    * 생략해도 상관 없지만 그냥 추가해 주었는데 생략과 표기가 큰 차이가 있는 듯 하다.

`MemberRepositoryTest`코드를 실행하려면 H2 데이터베이스에 연결을 시킨 후에 실행해야 정상적으로 테스트를 통과할 수 있다.

### 참고 블로그

* [@SpringBootTest로 통합 테스트 하기](https://goddaehee.tistory.com/211)
* [JPA 영속성 컨텍스트 특징](https://blog.baesangwoo.dev/posts/jpa-persistence-context/)
