package com.swiftdrop.user.repository;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Private to the user module: other modules go through UserService. */
public interface UserRepository extends JpaRepository<User, UUID> {

    // lower(...) matches the ux_users_email_lower index, so lookups are case-insensitive and indexed.
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);

    boolean existsByRole(Role role);

    Optional<User> findFirstByRole(Role role);

    Optional<User> findByGoogleSubject(String googleSubject);
}
