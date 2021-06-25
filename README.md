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

## Note