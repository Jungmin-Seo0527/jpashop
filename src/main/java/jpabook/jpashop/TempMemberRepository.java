package jpabook.jpashop;

import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Repository
public class TempMemberRepository {

    @PersistenceContext
    private EntityManager em;

    public Long save(TempMember member) {
        em.persist(member);
        return member.getId();
    }

    public TempMember find(Long id) {
        return em.find(TempMember.class, id);
    }
}
