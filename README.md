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

## Note