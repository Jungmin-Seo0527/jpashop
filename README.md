# TIL

## 4. 회원 도메인 개발

### 4-1. 회원 리포지토리 개발

#### MemberRepository.java - 회원 리포지토리

* `src/main/java/jpabook/jpashop/repository/MemberRepository.java`

```java
package jpabook.jpashop.repository;

import jpabook.jpashop.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.util.List;

@Repository
public class MemberRepository {

    @PersistanceContext
    private EntityManager em;

    public void save(Member member) {
        em.persist(member);
    }

    public Member findOne(Long id) {
        return em.find(Member.class, id);
    }

    public List<Member> findAll() {
        return em.createQuery("select m from Member m", Member.class)
                .getResultList();
    }

    public List<Member> findByName(String name) {
        return em.createQuery("select m from Member m where m.name = :name", Member.class)
                .setParameter("name", name)
                .getResultList();
    }
}

```

* `@Repository`: 스프링 빈으로 등록, JPA 예외를 스프링 기반 예외로 예외 변환
* `@PersistanceContext`: 엔티티 메니저 (`EntityManager`)주입
* `@PersistanceUnit`: 엔티티 메니저 팩터리 (`EntityManagerFactory`)주입

> MTH       
> `@PersistanceContext`, `EntityManager`, `JPQL`관련된 설명은 til의 기타 문서에서 [JPA 영속성 컨텍스트 특징] [JPA 객체 지향 쿼리, JPQL] 블로그 참고

### 4-2. 회원 서비스 개발

#### MemberService.java - 회원 서비스

* `src/main/java/jpabook/jpashop/service/MemberService.java`

```java
package jpabook.jpashop.service;

import jpabook.jpashop.domain.Member;
import jpabook.jpashop.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    /**
     * 회원 가입
     */
    @Transactional
    public Long join(Member member) {
        validateDuplicateMember(member);
        memberRepository.save(member);
        return member.getId();
    }

    private void validateDuplicateMember(Member member) {
        List<Member> findMembers = memberRepository.findByName(member.getName());
        if (!findMembers.isEmpty()) {
            throw new IllegalStateException("이미 존재하는 회원입니다.");
        }
    }

    /**
     * 회원 전체 조회
     */
    public List<Member> findMembers() {
        return memberRepository.findAll();
    }

    public Member findOne(Long memberId) {
        return memberRepository.findOne(memberId);
    }
}

```

* `@Service`
* `@Transactionsl`: 트랜잭션, 영속성 컨텍스트
    * `readOnly=true`: 데이터의 변경이 없는 읽기 전용 메서드에 사용, 영속성 컨텍스트를 flush 하지 않으므로 약간의 성능 향상(읽기 전용에는 다 적용)
    * 데이터베이스 드라이버가 지원하면 DB에서 성능 향상
* `@Autowired`
    * 생성자 injection 많이 사용, 생성자가 하나면 생략 가능

> 참고    
> 실무에서는 검증 로직이 있어도 멀티 쓰레드 상황을 고려해서 회원 테이블의 회원명 컬럼에 유니크 제약 조건을 추가하는 것이 안전하다.

> 참고: 스프링 필드 주입 대신에 생성자 주입을 사용하자.   
> 스프링 데이터 JPA를 사용하면 `EntityManager`도 `@PersistanceContext`를 생략하고 생성자 주입 가능

#### MemberService.java (수정) - 생성자 주입

```java
package jpabook.jpashop.repository;

import jpabook.jpashop.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MemberRepository {

    private final EntityManager em;

    public void save(Member member) {
        em.persist(member);
    }

    public Member findOne(Long id) {
        return em.find(Member.class, id);
    }

    public List<Member> findAll() {
        return em.createQuery("select m from Member m", Member.class)
                .getResultList();
    }

    public List<Member> findByName(String name) {
        return em.createQuery("select m from Member m where m.name = :name", Member.class)
                .setParameter("name", name)
                .getResultList();
    }
}
```

### 4-3. 회원 기능 테스트

#### MemberServiceTest.java

* `src/test/java/jpabook/jpashop/service/MemberServiceTest.java`

```java
package jpabook.jpashop.service;

import jpabook.jpashop.domain.Member;
import jpabook.jpashop.repository.MemberRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class MemberServiceTest {

    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    @Test
    public void 회원가입() throws Exception {
        // given
        Member member = new Member();
        member.setName("kim");

        // when
        Long savedId = memberService.join(member);

        // then
        assertEquals(member, memberRepository.findOne(savedId));
    }

    @Test(expected = IllegalStateException.class)
    public void 중복_회원_예외() throws Exception {
        // given
        Member member1 = new Member();
        member1.setName("kim");

        Member member2 = new Member();
        member2.setName("kim");

        // when
        memberService.join(member1);
        // try {
        //     memberService.join(member2);
        // } catch (IllegalStateException e) {
        //     return;
        // }
        memberService.join(member2);

        // then
        fail("예외가 발생해야 한다.");
    }
}
```

* `@RunWith(SpringRunner.class)`: 스프링과 테스트 통합
* `@SpringBootTest`: 스프링 부트 띄우고 테스트(이게 없으면 `@AutoWired`다 실패)
* `@Transactional`: 반복 가능한 테스트 지원, 각각의 테스트를 실행할 때마다 트랜잭션을 시작하고 **테스트가 끝나면 트랜잭션을 강제로 롤백**(이 어노테이션이 테스트 케이스에서 사용될 때만 롤백)

#### 테스트케이스를 위한 설정

테스트는 케이스 격리된 환경에서 실행하고, 끝나면 데이터를 초기화하는 것이 좋다. 그런 면에서 메모리DB를 사용하는 것이 가장 이상적이다.   
추가로 테스트 케이스를 위한 스프링 환경과, 일반적으로 애플리케이션을 실행하는 환경은 보통 다르므로 설정 파일을 다르게 사용하자.

다음과 같이 간단하게 테스트용 설정 파일을 추가하면 된다.

##### application.yml - 테스트 케이스를 위한 설정 파일

* `src/test/resources/application.yml`

```yaml
spring:
#  datasource:
#    url: jdbc:h2:mem:test
#    username: sa
#    password:
#    driver-class-name: org.h2.Driver
#
#  jpa:
#    hibernate:
#      ddl-auto: create
#    properties:
#      hibernate:
#        #        show_sql: true
#        format_sql: true


logging:
  level:
    org.hibernate.SQL: debug
#    org.hibernate.type: trace
```

이제 테스트에서 스프링을 실행하면 이 위치에 있는 설정 파일을 읽는다.   
(만약 이 위치에 없으면 `src/main/resources/application.yml`을 읽는다.)

스프링 부트는 datasource 설정이 없으면, 기본적으로 메모리 DB를 사용하고, driver-class도 현재 등록된 라이브러리를 보고 찾아준다. 추가로 `ddl-auto`도 `create-drop`모드로
동작한다. 따라서 데이터소스나, JPA 관련된 별도의 추가 설정을 하지 않아도 된다.

## Note