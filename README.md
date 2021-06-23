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

## Note