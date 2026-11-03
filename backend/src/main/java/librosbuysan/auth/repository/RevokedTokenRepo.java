package librosbuysan.auth.repository;

import java.time.Instant;
import librosbuysan.auth.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RevokedTokenRepo extends JpaRepository<RevokedToken, String> {

    boolean existsByTokenIdAndExpiresAtAfter(String tokenId, Instant now);

    @Modifying
    @Transactional
    @Query("DELETE FROM RevokedToken r WHERE r.expiresAt <= :now")
    void deleteExpired(@Param("now") Instant now);
}

